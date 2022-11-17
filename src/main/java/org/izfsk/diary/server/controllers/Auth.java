package org.izfsk.diary.server.controllers;

import io.javalin.http.Context;
import io.javalin.http.util.NaiveRateLimit;
import org.izfsk.diary.server.Configure;
import org.izfsk.diary.server.TargetPublicKey;
import org.izfsk.diary.server.utils.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileLock;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.Period;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Token payload, outdated after 1 day.
 *
 * @param clientSideSaltString
 * @param clientIP
 * @param valid
 * @param createDate
 */
record TokenPayload(String clientSideSaltString, String clientIP, boolean valid, LocalDate createDate) {
	@Override
	public String toString() {
		return "TokenPayload{" +
				"clientSideSaltString='" + clientSideSaltString + '\'' +
				", clientIP='" + clientIP + '\'' +
				", valid=" + valid +
				", createDate=" + createDate +
				'}';
	}
}

/**
 * singleton class to store auth tokens
 */
class authTokenStore {
	private static authTokenStore store = null;

	private final Map<String, TokenPayload> records;

	private authTokenStore() {
		records = new HashMap<>();
	}

	public Map<String, TokenPayload> getRecords() {
		return records;
	}

	public static authTokenStore getInstance() {
		if (store == null) {
			store = new authTokenStore();
		}
		return store;
	}
}

public final class Auth {
	public static LocalDate getSaveTargetDate(String splintedToken) {
		var serverSideSalt = splintedToken.substring(0, 16);
		var tokenPayload = authTokenStore.getInstance().getRecords().get(serverSideSalt);
		if (tokenPayload == null) {
			LoggerFactory.getLogger("getSaveTargetDate").warn("Token not found!");
			return null;
		}
		return tokenPayload.createDate();
	}

	public static boolean checkToken(String spitedToken, String peerIPAddr) {
		if (spitedToken.length() != (16 + 16)) {
			return true;
		}
		var serverSideSalt = spitedToken.substring(0, 16);
		var clientSideSalt = spitedToken.substring(16, 32);
		var tokenPayload = authTokenStore.getInstance().getRecords().get(serverSideSalt);
		if (tokenPayload == null) {
			LoggerFactory.getLogger("checkToken").warn("Token not found!");
			return true;
		}
		if (! tokenPayload.valid() ||
				! tokenPayload.clientSideSaltString().equals(clientSideSalt) ||
				! tokenPayload.clientIP().equals(peerIPAddr)) {
			LoggerFactory.getLogger("checkToken").warn("Token is not valid!");
			return true;
		}
		var period = Period.between(tokenPayload.createDate(), LocalDate.now());
		if (period.getDays() > 1) {
			LoggerFactory.getLogger("checkToken").warn("Token is outdated!");
			return true;
		}
		return false;
	}

	/**
	 * <b>Stage 1 auth</b> <br/>
	 * <p>
	 * return a random string as server side salt,
	 * store it and peer's IP address for stage 2 auth.
	 * <p>
	 * check IP in store, if found, then delete old token entry.
	 *
	 * @param context Javalin context
	 */
	public static void Stage1AuthHandler(@NotNull Context context) {
		NaiveRateLimit.requestPerTimeUnit(context, 10, TimeUnit.MINUTES);

		// check and remove old entry
		for (var tokenEntry : authTokenStore.getInstance().getRecords().entrySet()) {
			if (tokenEntry.getValue().clientIP().equals(context.ip())) {
				LoggerFactory.getLogger("Auth").warn("Delete old token " + tokenEntry.getValue());
				authTokenStore.getInstance().getRecords().remove(tokenEntry.getKey());
				break;
			}
		}

		// generate and store new entry
		String serverSideSalt = StringUtils.getRandomString(16);
		authTokenStore.getInstance().getRecords().put(
				serverSideSalt,
				new TokenPayload(
						null,
						context.ip(),
						false,
						LocalDate.now())
		);

		// send salt
		context.status(200).result(serverSideSalt);
	}

	/**
	 * <b>Stage 2 auth</b><br/>
	 * <p>
	 * client return its clientSideSalt and the signature of serverSideSalt + clientSideSalt
	 * server verify it, if valid, update the token store and return 200, otherwise return 403.
	 * <p>
	 * The client side salt is stored in header <code>x-clientside-salt</code>, it's a 16 char string.
	 * original server side salt is stored in header <code>x-serverside-salt</code>.
	 * signature is the header param <code>x-signature</code>
	 * <p>
	 * stage 2 auth will return today's journey data if succeed.
	 *
	 * @param context Javalin context
	 */
	public static void Stage2AuthHandler(@NotNull Context context) {
		NaiveRateLimit.requestPerTimeUnit(context, 10, TimeUnit.MINUTES);

		// check client side salt and its length
		String clientSideSalt = context.header("x-clientside-salt");
		String serverSideSalt = context.header("x-serverside-salt");
		if (clientSideSalt == null || clientSideSalt.length() != 16) {
			LoggerFactory.getLogger("Auth").warn("Failed to auth : clientSideSalt is too short!");
			context.status(403);
			return;
		}

		// check server side salt, its length and its existence.
		if (serverSideSalt == null || serverSideSalt.length() != 16 ||
				! authTokenStore.getInstance().getRecords().containsKey(serverSideSalt)) {
			LoggerFactory.getLogger("Auth").warn("Failed to auth : serverSideSalt is not exist or clientSideSalt is too short!");
			context.status(403);
			return;
		}

		// check this request came from the same client.
		var tokenContent = authTokenStore.getInstance().getRecords().get(serverSideSalt);
		if (! Objects.equals(tokenContent.clientIP(), context.ip())) {
			LoggerFactory.getLogger("Auth").warn("Failed to auth : client IP not match!");
			context.status(403);
			return;
		}

		// check signature
		var signature = context.formParamMap().get("signature").get(0);
		if (signature == null) {
			LoggerFactory.getLogger("Auth").warn("Failed to auth : signature is NULL!");
			context.status(403);
			return;
		}
		if (! TargetPublicKey.verifyDetachedSignature(
				serverSideSalt + clientSideSalt,
				signature
		)) {
			LoggerFactory.getLogger("Auth").warn("Failed to auth : signature is bad!");
			context.status(403);
			return;
		}

		// now update token body and return that day's data
		authTokenStore.getInstance().getRecords().replace(serverSideSalt, new TokenPayload(
				clientSideSalt,
				context.ip(),
				true,
				LocalDate.now()
		));
		var targetDate = LocalDate.now();
		var formattedDateString = String.join("/",
				String.valueOf(targetDate.getYear()),
				String.valueOf(targetDate.getMonthValue()),
				String.valueOf(targetDate.getDayOfMonth()));
		Path targetFilePath = Path.of(
				Configure.DiaryRootDir, formattedDateString, "diary.asc");
		var targetFile = new File(String.valueOf(targetFilePath));
		if (! targetFile.exists()) {
			context.status(200).result("null");
			return;
		}
		try (var randomAccessFile = new RandomAccessFile(String.valueOf(targetFilePath), "rw")) {
			var fileChannel = randomAccessFile.getChannel();
			FileLock fileLock = null;
			while (true) {
				try {
					fileLock = fileChannel.tryLock();
					break;
				} catch (Exception e) {
					Thread.sleep(100);
				}
			}
			context.status(200).result(new FileInputStream(targetFile));
			fileLock.release();
		} catch (IOException | InterruptedException e) {
			e.printStackTrace();
			LoggerFactory.getLogger("EntryGet").info("Error while reading file " + targetDate);
			context.status(500);
		}
	}
}
