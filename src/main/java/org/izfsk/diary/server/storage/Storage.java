package org.izfsk.diary.server.storage;

import io.javalin.http.Context;
import org.izfsk.diary.server.Configure;
import org.izfsk.diary.server.TargetPublicKey;
import org.izfsk.diary.server.controllers.Auth;
import org.izfsk.diary.server.utils.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Base64;

class SaveFileTask implements Runnable {
	private final RandomAccessFile randomAccessFile;
	private final String data;

	/**
	 * Save file to disk.
	 * Each day have its own dir, file is saved at
	 *
	 * @param targetDay      target day (full path)
	 * @param data           PGP encrypted ascii-armored data string
	 */
	public SaveFileTask(String targetDay, String data) {
		this.data = data;
		try {
			new File(targetDay).mkdirs();
			randomAccessFile = new RandomAccessFile(String.valueOf(Path.of(targetDay,"diary.asc")), "rw");
			randomAccessFile.setLength(0);
		} catch (IOException e) {
			LoggerFactory.getLogger("SaveFile").error(e.getMessage());
			throw new RuntimeException(e);
		}
	}

	@Override
	public void run() {
		try {
			FileChannel fileChannel = randomAccessFile.getChannel();
			FileLock fileLock;
			while (true) {
				try {
					fileLock = fileChannel.tryLock();
					break;
				} catch (IOException e) {
					Thread.sleep(100);
				}
			}
			// write file
			randomAccessFile.write(this.data.getBytes(StandardCharsets.UTF_8));
			fileLock.release();
			fileChannel.close();
			randomAccessFile.close();
		} catch (IOException e) {
			LoggerFactory.getLogger("WriteFileTask").warn("Unable to write file!");
			e.printStackTrace();
		} catch (InterruptedException e) {
			LoggerFactory.getLogger("WriteFileTask").error("Error while sleep!");
			e.printStackTrace();
			throw new RuntimeException(e);
		}
	}
}

public final class Storage {
	/**
	 * <h3>The journey data API.</h3>
	 * <p>
	 * verify user and return encrypted pgp data when found or "null" if not found.<br/>
	 * The path param is YYYY/MM/DD.<br/>
	 * the request cred is appended in header <code>x-token</code>
	 * contains <code>serverSideSalt+clientSideToken</code>.
	 *
	 * @param context Javalin content
	 */
	public static void EntryGetHandler(@NotNull Context context) {
		String targetDateString = context.pathParam("date");
		if (! StringUtils.checkDate(targetDateString)) {
			LoggerFactory.getLogger("EntryGet").warn("Invalid date string " + targetDateString);
			context.status(400);
			return;
		}

		// check user
		var token = context.header("x-token");
		if (token == null || Auth.checkToken(token, context.ip())) {
			LoggerFactory.getLogger("EntryGet").warn("Invalid token." + (token == null ? "(Token is NULL)" : ""));
			context.status(403);
			return;
		}

		// now send data. read from disk. acquire file lock first.
		String targetFilePath = String.valueOf(Path.of(Configure.DiaryRootDir,targetDateString, "diary.asc"));
		var targetFile = new File(targetFilePath);
		if (! targetFile.exists()) {
			LoggerFactory.getLogger("EntryGet").info("The entry " + targetDateString + " is not exists.");
			context.status(200).result("null");
			return;
		}

		try (var randomAccessFile = new RandomAccessFile(targetFilePath, "rw")) {
			var fileChannel = randomAccessFile.getChannel();
			FileLock fileLock;
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
			LoggerFactory.getLogger("EntryGet").info("Error while reading file " + targetDateString);
		}
	}

	/**
	 * <h3>The journey setter </h3>
	 * <p>
	 * verify user and save file. the file save operation is a task thread.
	 * <p>
	 * headers : <code>x-token</code> : serverSideSalt+clientSideSalt
	 * post form : <code>data</code> : gpg encrypted ascii-armored string
	 * <code>signature</code> : gpg signature of data
	 *
	 * @param context Javalin context
	 */
	public static void EntrySetHandler(@NotNull Context context) {
		// check user
		var token = context.header("x-token");
		if (token == null || Auth.checkToken(token, context.ip())) {
			LoggerFactory.getLogger("EntrySet").warn("Invalid token.");
			context.status(403);
			return;
		}

		// check if target is editable
		var targetDate = Auth.getSaveTargetDate(token);
		if (targetDate == null) {
			LoggerFactory.getLogger("EntrySet").warn("targetDate is Null!");
			context.status(403);
			return;
		}

		// now save data
		var gpgData = context.formParam("data");
		var signature = context.formParam("signature");
		if (gpgData == null || signature == null) {
			LoggerFactory.getLogger("EntrySet").warn("Empty token or gpg data!");
			context.status(400);
			return;
		}
		// the gpgData will be treated as decrypt target by verifyDetachedSignature
		// so the signature target is Base64-encoded-string of original gpgData!
		if (! TargetPublicKey.verifyDetachedSignature(Base64.getEncoder()
				.encodeToString(gpgData.replaceAll("\r\n", "\n").getBytes()), signature)) {
			LoggerFactory.getLogger("EntrySet").warn("Verify signature failed!");
			context.status(403);
			return;
		}

		// set a thread to save file
		context.async(() -> {
			var formattedDateString = String.join("/",
					String.valueOf(targetDate.getYear()),
					String.valueOf(targetDate.getMonthValue()),
					String.valueOf(targetDate.getDayOfMonth()));
			new SaveFileTask(String.valueOf(Path.of(
					Configure.DiaryRootDir, formattedDateString)
			), gpgData).run();
			context.status(200);
		});
	}
}
