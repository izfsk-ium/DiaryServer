package org.izfsk.diary.server.utils;

import java.util.Random;

public final class StringUtils {
	public static String getRandomString(int length) {
		return new Random().ints(48, 123)
				.filter(i -> (i <= 57 || i >= 65) && (i <= 90 || i >= 97))
				.limit(length)
				.collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
				.toString();
	}

	public static boolean checkDate(String dateString) {
		int year, month, day;

		// check date format
		try {
			var spited = dateString.split("/");
			if (spited.length != 3) {
				throw new NumberFormatException();
			}
			year = Integer.parseInt(spited[0]);
			month = Integer.parseInt(spited[1]);
			day = Integer.parseInt(spited[2]);
			if (year < 2001 || year > 2100 || month < 0 || month > 12 || day < 0 || day > 31) {
				throw new NumberFormatException();
			}
		} catch (NumberFormatException e) {
			return false;
		}
		return true;
	}
}
