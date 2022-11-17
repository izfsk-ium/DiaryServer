import * as openpgp from 'openpgp';
import * as swal from "sweetalert2"
import { status_t } from './Store';
import { createStore } from "redux"
import { reducers } from "./Store/reducers"

const ENV: "development" | "production" | any = "production";
if (ENV !== "development") {
	console.log = (...data) => { };
	console.warn = (...data) => { };
}

export const keyStore = {
	encryptedPrivateKey: localStorage.getItem("EncryptedPrivateKey"),
	privateKey: null as any,
	publicKey: null as any,
	serverSideSalt: null as string | null,
	clientSideSalt: (() => {
		let chars = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789', str = '';
		for (let i = 0; i < 16; i++)
			str += chars.charAt(Math.floor(Math.random() * chars.length));
		return str;
	})(),
	isAuthFinished: false
}

export let CurrentLocation = "Unknown";
export const defaultState = {
	currentDate: new Date(),
	promptStatusText: 'Welcome To Journel Manage System.',
	currentjournel: null,
	edited: false
} as status_t

export let store = null;

async function getLocation() {
	const currentLocation =
		await fetch("https://api.ip.sb/geoip")
			.then(i => i.json())
			.then(json => { return `${json.country || "Unknown"} ${json.region || ""} ${json.city || ""}` })
			.catch(e => { return "unknown" });
	console.log(currentLocation);
	CurrentLocation = currentLocation;
}

async function promptForPrivateKey(): Promise<string> {
	return await swal.default.fire({
		title: "Input your private key:",
		text: "Key will be stored in localStore (encrypted)",
		input: 'textarea',
		inputPlaceholder: 'Paste your ASCII_armored key here...',
		showCancelButton: false
	}).then(resp => {
		return resp.value || ""
	}).catch(e => {
		console.error(e);
		return ""
	})
}

async function decryptPrivateKey(rawKey: string): Promise<boolean> {
	const decryptedPrivateKey = await openpgp.decryptKey({
		privateKey: await openpgp.readPrivateKey({ armoredKey: rawKey }),
		passphrase: prompt() || ""
	}).catch(e => { console.error(e); return null; });
	if (decryptPrivateKey == null) {
		return false;
	}
	// save keyStore
	keyStore.privateKey = decryptedPrivateKey;
	keyStore.publicKey = decryptedPrivateKey?.toPublic();
	localStorage.setItem("EncryptedPrivateKey", rawKey)
	return true;
}

async function stage1AuthProgress(): Promise<string> {
	const result =
		await fetch("/auth", { method: "GET" })
			.then(resp => { return resp.ok ? resp.text() : "null" })
			.catch(e => { return e.toString() });
	return result;
}

async function stage2AuthProgress(serverSideSalt: string): Promise<string | null> {
	const authMessage = await openpgp.createMessage({ text: serverSideSalt + keyStore.clientSideSalt });
	const detachedSignature = await openpgp.sign({
		message: authMessage,
		signingKeys: keyStore.privateKey,
		detached: true
	});
	let formData = new FormData();
	formData.append("signature", detachedSignature.toString());
	const authRequest =
		await fetch("/auth", {
			method: "POST",
			headers: {
				"x-clientside-salt": keyStore.clientSideSalt as string,
				"x-serverside-salt": keyStore.serverSideSalt as string,
			},
			body: formData
		})
			.then(resp => { return resp.text() })
			.catch(e => { return null; })
	return authRequest;
}

async function die(e: Error) {
	(document.getElementById("body") as HTMLElement).innerHTML = '';
	console.error(e);
	swal.default.fire({
		icon: "error",
		title: "Error!",
		text: e.toString()
	}).then(() => { location.reload() })
}

(async () => {
	// load location information, this is async
	getLocation();

	// load key from localStorage, if not found, prompt for it.
	let rawPrivateKey = "";
	if (keyStore.encryptedPrivateKey == null) {
		rawPrivateKey = await promptForPrivateKey();
	} else {
		rawPrivateKey = keyStore.encryptedPrivateKey;
	}

	// decrypt private key.
	if (!await decryptPrivateKey(rawPrivateKey)) {
		die(new Error("Unable to decrypt privateKey!"));
		return;
	}

	// start auth process
	const serverSideSalt = await stage1AuthProgress();
	keyStore.serverSideSalt = serverSideSalt;
	const auth2Result = await stage2AuthProgress(serverSideSalt)
	if (auth2Result === null) {
		console.error("stage2 return null");
		die(new Error("Unable to auth you!"));
		return;
	}
	keyStore.isAuthFinished = true;

	// init default data store
	if (auth2Result !== "null") {
		const pgpCipherText = await openpgp.readMessage({ armoredMessage: auth2Result });
		const [decryptedJSONText, detachedSignatures] = await openpgp.decrypt({
			message: pgpCipherText,
			verificationKeys: keyStore.publicKey,
			decryptionKeys: keyStore.privateKey
		})
			.then(result => { return [result.data, result.signatures] })
			.catch(e => { return [null, null] });
		if (decryptedJSONText == null || detachedSignatures == null) {
			die(new Error("Unable to auth you!"));
			return;
		}
		defaultState.currentjournel = JSON.parse(decryptedJSONText as any);
	}
	store = createStore(reducers);

	// load the main page
	await import("./Application").then(i => { i.initApplication() })
})().catch(e => { die(e) })