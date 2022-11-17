import { JournelData, status_t } from "."
import * as openpgp from 'openpgp';
import { CurrentLocation, defaultState, keyStore } from "../main";
import Swal from "sweetalert2";
import { clone } from "../utils";

async function saveOldData(targetDate: Date, data: JournelData) {
    if (data != null) {
        if (targetDate.toDateString() !== new Date().toDateString()) {
            return;
        }
        console.log("Saving data ", data, targetDate);
        data.location = CurrentLocation;
        if (typeof data.body === "undefined" || data.body == null || data.body == "") {
            return;
        }
        if (typeof data.feeling === "undefined" || data.feeling == null) {
            data.feeling = "Unknown"
        }
        if (typeof data.weather === "undefined" || data.weather == null) {
            data.weather = "sunny"
        }
        const encrypted = await openpgp.encrypt({
            message: await openpgp.createMessage({ text: JSON.stringify(data) }),
            encryptionKeys: keyStore.publicKey,
            signingKeys: keyStore.privateKey
        });
        const detachedSignature = await openpgp.sign({
            message: await openpgp.createMessage({ text: btoa(encrypted.toString()) }), // Message object, the pgp encrypted data is always ascii so just use btoa.
            signingKeys: keyStore.privateKey,
            detached: true
        });
        // now post to server
        const formData = new FormData();
        formData.append("data", encrypted.toString());
        formData.append("signature", detachedSignature.toString());
        fetch("/save", {
            method: "POST",
            headers: {
                "x-token": keyStore.serverSideSalt + keyStore.clientSideSalt
            },
            body: formData
        }).then(resp => {
            if (!resp.ok) {
                throw Error("Unable to save data!");
            }
        }).catch(e => {
            console.error(e);
            Swal.mixin({
                toast: true,
                position: 'top-end',
                showConfirmButton: false,
                timer: 3000
            }).fire({
                icon: 'error',
                title: 'Failed to save data!' + e.toString()
            })
        }).then(() => {
            Swal.mixin({
                toast: true,
                position: 'top-end',
                showConfirmButton: false,
                timer: 2500
            }).fire({
                icon: "success",
                text: 'Data saved for ' + targetDate.toDateString()
            })
        })
    }
}

export const reducers = (state = defaultState, action: { type: string, body: any }) => {
    let result = state;
    console.log("reducer : action " + action.type);
    switch (action.type) {
        case "manuallySave":
            if (state.edited) {
                saveOldData(state.currentDate, state.currentjournel as JournelData);
                result = {
                    ...state,
                    edited: false,
                    promptStatusText: "Saved."
                }
            } else {
                result = {
                    ...state,
                    edited: false,
                    promptStatusText: "Saved. (No modify)"
                }
            }
            break;
        case "updateDate":
            if (state.edited && state.currentDate.toDateString() === (new Date()).toDateString()) {
                const journelSnapshot = clone(state.currentjournel);
                const dateSnapshot = clone(state.currentDate);
                Swal.fire({
                    icon: "question",
                    title: "Save your data for " + state.currentDate.toLocaleDateString("zh-CN"),
                    showCancelButton: true,
                    showConfirmButton: true
                }).then(c => {
                    if (c.isConfirmed) saveOldData(dateSnapshot, journelSnapshot as JournelData);
                })
            }
            result = {
                currentDate: action.body.date,
                edited: false,
                promptStatusText: "Date changed to " + action.body.date.toDateString(),
                currentjournel: action.body.journel === null ? {
                    location: action.body.date.toDateString() === new Date().toDateString() ? CurrentLocation : "Unknown",
                    body: "",
                    feeling: "Unknown",
                    weather: "Unknown"
                } : action.body.journel
            } as status_t;
            break;
        case "updateFeeling":
            result = {
                ...state,
                promptStatusText: "Editing feeling...",
                edited: true,
                currentjournel: {
                    ...state.currentjournel,
                    feeling: action.body,
                }
            } as status_t
            break;
        case "updateWeather":
            result = {
                ...state,
                edited: true,
                currentjournel: {
                    ...state.currentjournel,
                    promptStatusText: "Editing weather...",
                    weather: action.body
                }
            } as status_t
            break;
        case "updateText":
            result = {
                ...state,
                promptStatusText: "Editing journel...",
                edited: true,
                currentjournel: {
                    ...state.currentjournel,
                    body: action.body.text
                }
            } as status_t
        default:
            break;
    }
    return result;
}