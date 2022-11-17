package org.izfsk.diary.server;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.izfsk.diary.server.TargetPublicKey.encryptStringForClient;
import static org.junit.jupiter.api.Assertions.*;

class TargetPublicKeyTest {

	@Test
	@DisplayName("getPublicKey")
	void getTargetPublicKeyTest()  {
		assertNotNull(TargetPublicKey.targetPublicKey);
	}

	@Test
	@DisplayName("verifyDetachedMessage")
	void verifyDetachedSignatureTest1() {
		final String signature= """
				-----BEGIN PGP SIGNATURE-----
				    
				wnUEARYKACcFAmNjwxwJECH17hQU/t76FiEEyyDhZQVwMyh7LcWdIfXuFBT+
				3voAAAx6AP0UC5K9wzDEB0xIPXKR92FLRrooIzQWjGXfH51+t+g3kgD/TF6G
				EQGlQ8eJqCIzjvf6wZ3bKdXq/W22h/qjFDQWOwk=
				=A29N
				-----END PGP SIGNATURE-----
				""";
		final String message="Hello, World!";
		assertTrue(TargetPublicKey.verifyDetachedSignature(message,signature));
	}

	@Test
	@DisplayName("verifyInvalidDetachedMessage")
	void verifyDetachedSignatureTest2() {
		final String signature= """
				-----BEGIN PGP SIGNATURE-----
				    
				wnUEARYKACcFAmNjwxwJECH17hQU/t76FiEEyyDhZQVwMyh7LcWdIfXuFBT+
				3voAAAx6AP0UC5K9wzDEB0xIPXKR92FLRrooIzQWjGXfH51+t+g3kgD/TF6G
				EQGlQ8eJqCIzjvf6wZ3bKdXq/W22h/qjFDQWOwk=
				=A29N
				-----END PGP SIGNATURE-----
				""";
		final String message="Hel, World!";
		try{
			assertFalse(TargetPublicKey.verifyDetachedSignature(message,signature));
		}catch (Exception ignored){}
	}

	@Test
	@DisplayName("encryptForClient")
	void encryptStringForClientTest() {
		var result=encryptStringForClient("hello!");
		System.out.println(result);
		assertNotNull(result);
	}
}