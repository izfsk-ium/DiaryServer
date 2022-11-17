package org.izfsk.diary.server.utils;

import org.junit.jupiter.api.Test;

import static org.izfsk.diary.server.utils.StringUtils.getRandomString;
import static org.junit.jupiter.api.Assertions.*;

class StringUtilsTest {

	@Test
	void getRandomStringTest() {
		var a=getRandomString(32);
		var b=getRandomString(32);
		assertNotEquals(a,b);
	}
}