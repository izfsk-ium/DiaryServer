package org.izfsk.diary.server;

import org.bouncycastle.openpgp.PGPException;
import org.bouncycastle.openpgp.PGPPublicKeyRing;
import org.bouncycastle.util.io.Streams;
import org.pgpainless.PGPainless;
import org.pgpainless.algorithm.SymmetricKeyAlgorithm;
import org.pgpainless.decryption_verification.ConsumerOptions;
import org.pgpainless.decryption_verification.DecryptionStream;
import org.pgpainless.encryption_signing.EncryptionOptions;
import org.pgpainless.encryption_signing.ProducerOptions;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

public final class TargetPublicKey {

	public static final PGPPublicKeyRing targetPublicKey;

	static {
		try {
			targetPublicKey = PGPainless.readKeyRing().publicKeyRing(Configure.PublicKey);
		} catch (IOException e) {
			e.printStackTrace();
			throw new RuntimeException(e);
		}
	}

	public static boolean verifyDetachedSignature(String message, String asciiArmoredSignature){
		try{
			DecryptionStream verificationStream = PGPainless.decryptAndOrVerify()
					.onInputStream(new ByteArrayInputStream(message.getBytes()))
					.withOptions(ConsumerOptions.get()
							.addVerificationCert(targetPublicKey) // provide certificate for verification
							.addVerificationOfDetachedSignatures(new ByteArrayInputStream(asciiArmoredSignature.getBytes()))
					);
			Streams.drain(verificationStream); // push all the data through the stream
			verificationStream.close(); // finish verification
			return verificationStream.getResult().isVerified();
		} catch (PGPException | IOException e) {
			return false;
		}
	}

	public static String encryptStringForClient(String message){
		var outputStream = new ByteArrayOutputStream();
		try{
			var encryptionStream = PGPainless.encryptAndOrSign()
					.onOutputStream(outputStream)
					.withOptions(
							ProducerOptions.encrypt(
									new EncryptionOptions()
											.addRecipient(targetPublicKey)
											.overrideEncryptionAlgorithm(SymmetricKeyAlgorithm.AES_256)
							).setAsciiArmor(true) // Ascii armor or not
					);

			Streams.pipeAll(new ByteArrayInputStream(message.getBytes()), encryptionStream);
			encryptionStream.close();
			return outputStream.toString();
		} catch (PGPException | IOException e) {
			e.printStackTrace();
			return null;
		}
	}
}
