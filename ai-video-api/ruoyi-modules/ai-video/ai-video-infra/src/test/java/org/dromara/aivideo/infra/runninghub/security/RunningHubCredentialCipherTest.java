package org.dromara.aivideo.infra.runninghub.security;

import org.dromara.aivideo.workflow.enums.WorkflowCredentialPurpose;
import org.dromara.common.core.exception.ServiceException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;

import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("dev")
class RunningHubCredentialCipherTest {

    private static final int NONCE_BYTES = 12;
    private static final int TAG_BYTES = 16;
    private static final String VALID_KEY = Base64.getEncoder()
        .encodeToString("0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8));

    @Test
    void usesSpringConfiguredMasterKey() {
        RunningHubCredentialCipher cipher = new RunningHubCredentialCipher(VALID_KEY);

        String encrypted = cipher.encryptForStorage(
            WorkflowCredentialPurpose.RUNNINGHUB_API_KEY, "configured-secret".toCharArray());
        char[] decrypted = cipher.decryptForUse(WorkflowCredentialPurpose.RUNNINGHUB_API_KEY, encrypted);

        assertThat(decrypted).containsExactly("configured-secret".toCharArray());
        Arrays.fill(decrypted, '\0');
    }

    @Test
    void springContextInjectsConfiguredMasterKey() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().getPropertySources().addFirst(
                new MapPropertySource("runninghub-test", Map.of("aivideo.runninghub.master-key", VALID_KEY)));
            context.register(RunningHubCredentialCipher.class);
            context.refresh();

            RunningHubCredentialCipher cipher = context.getBean(RunningHubCredentialCipher.class);
            String encrypted = cipher.encryptForStorage(
                WorkflowCredentialPurpose.RUNNINGHUB_API_KEY, "spring-secret".toCharArray());

            assertThat(cipher.decryptForUse(WorkflowCredentialPurpose.RUNNINGHUB_API_KEY, encrypted))
                .containsExactly("spring-secret".toCharArray());
        }
    }

    @Test
    void eachPurposeUsesIndependentAuthenticatedEncryptionContext() throws Exception {
        RunningHubCredentialCipher cipher = new RunningHubCredentialCipher(() -> VALID_KEY);

        String apiKey = cipher.encryptForStorage(
            WorkflowCredentialPurpose.RUNNINGHUB_API_KEY, "same-secret".toCharArray());
        String accessPassword = cipher.encryptForStorage(
            WorkflowCredentialPurpose.RUNNINGHUB_ACCESS_PASSWORD, "same-secret".toCharArray());

        assertThat(apiKey).startsWith("v1:").isNotEqualTo(accessPassword);
        assertThat(decryptIndependently(apiKey, WorkflowCredentialPurpose.RUNNINGHUB_API_KEY))
            .isEqualTo("same-secret");
        assertThat(decryptIndependently(accessPassword, WorkflowCredentialPurpose.RUNNINGHUB_ACCESS_PASSWORD))
            .isEqualTo("same-secret");
        assertThatThrownBy(() -> decryptIndependently(
            apiKey, WorkflowCredentialPurpose.RUNNINGHUB_ACCESS_PASSWORD))
            .isInstanceOf(AEADBadTagException.class);
        assertThatThrownBy(() -> decryptIndependently(
            accessPassword, WorkflowCredentialPurpose.RUNNINGHUB_API_KEY))
            .isInstanceOf(AEADBadTagException.class);
    }

    @Test
    void payloadHasTwelveByteNonceAndOneHundredTwentyEightBitTag() {
        RunningHubCredentialCipher cipher = new RunningHubCredentialCipher(() -> VALID_KEY);
        byte[] plaintext = "payload-shape".getBytes(StandardCharsets.UTF_8);

        byte[] payload = decodePayload(cipher.encryptForStorage(
            WorkflowCredentialPurpose.RUNNINGHUB_API_KEY, "payload-shape".toCharArray()));

        assertThat(Arrays.copyOfRange(payload, 0, NONCE_BYTES)).hasSize(NONCE_BYTES);
        assertThat(Arrays.copyOfRange(payload, NONCE_BYTES, payload.length))
            .hasSize(plaintext.length + TAG_BYTES);
    }

    @Test
    void constructionIsLazyButMissingOrInvalidMasterKeyFailsClosedOnWrite() {
        RunningHubCredentialCipher missing = new RunningHubCredentialCipher(() -> null);
        RunningHubCredentialCipher invalidBase64 = new RunningHubCredentialCipher(() -> "not-base64!");
        RunningHubCredentialCipher wrongLength = new RunningHubCredentialCipher(
            () -> Base64.getEncoder().encodeToString(new byte[16]));

        assertThatThrownBy(() -> missing.encryptForStorage(
            WorkflowCredentialPurpose.RUNNINGHUB_API_KEY, "secret".toCharArray()))
            .isInstanceOf(ServiceException.class).hasMessageContaining("主密钥");
        assertThatThrownBy(() -> invalidBase64.encryptForStorage(
            WorkflowCredentialPurpose.RUNNINGHUB_API_KEY, "secret".toCharArray()))
            .isInstanceOf(ServiceException.class).hasMessageContaining("主密钥");
        assertThatThrownBy(() -> wrongLength.encryptForStorage(
            WorkflowCredentialPurpose.RUNNINGHUB_API_KEY, "secret".toCharArray()))
            .isInstanceOf(ServiceException.class).hasMessageContaining("32");
    }

    @Test
    void decryptsOnlyWithMatchingPurposeAndRejectsTamperedCiphertext() {
        RunningHubCredentialCipher cipher = new RunningHubCredentialCipher(() -> VALID_KEY);
        String encrypted = cipher.encryptForStorage(
            WorkflowCredentialPurpose.RUNNINGHUB_API_KEY, "secret-api-key".toCharArray());

        char[] plaintext = cipher.decryptForUse(
            WorkflowCredentialPurpose.RUNNINGHUB_API_KEY, encrypted);

        assertThat(plaintext).containsExactly("secret-api-key".toCharArray());
        Arrays.fill(plaintext, '\0');
        assertThatThrownBy(() -> cipher.decryptForUse(
            WorkflowCredentialPurpose.RUNNINGHUB_ACCESS_PASSWORD, encrypted))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("解密失败");
        String tampered = encrypted.substring(0, encrypted.length() - 1)
            + (encrypted.endsWith("A") ? "B" : "A");
        assertThatThrownBy(() -> cipher.decryptForUse(
            WorkflowCredentialPurpose.RUNNINGHUB_API_KEY, tampered))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("解密失败");
    }

    @Test
    void nonceIsClearedWhenSecureRandomThrowsAfterWritingIt() {
        CapturingThrowingSecureRandom random = new CapturingThrowingSecureRandom();
        RunningHubCredentialCipher cipher = new RunningHubCredentialCipher(() -> VALID_KEY, random);

        assertThatThrownBy(() -> cipher.encryptForStorage(
            WorkflowCredentialPurpose.RUNNINGHUB_API_KEY, "secret".toCharArray()))
            .isInstanceOf(IllegalStateException.class);

        assertThat(random.capturedNonce).isNotNull().containsOnly((byte) 0);
    }

    private static String decryptIndependently(String ciphertext, WorkflowCredentialPurpose purpose)
        throws GeneralSecurityException {
        byte[] key = Base64.getDecoder().decode(VALID_KEY);
        byte[] payload = decodePayload(ciphertext);
        byte[] nonce = Arrays.copyOfRange(payload, 0, NONCE_BYTES);
        byte[] encrypted = Arrays.copyOfRange(payload, NONCE_BYTES, payload.length);
        byte[] aad = purpose.aadBytes();
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"),
                new GCMParameterSpec(TAG_BYTES * Byte.SIZE, nonce));
            cipher.updateAAD(aad);
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } finally {
            Arrays.fill(key, (byte) 0);
            Arrays.fill(payload, (byte) 0);
            Arrays.fill(nonce, (byte) 0);
            Arrays.fill(encrypted, (byte) 0);
            Arrays.fill(aad, (byte) 0);
        }
    }

    private static byte[] decodePayload(String ciphertext) {
        assertThat(ciphertext).startsWith("v1:");
        return Base64.getDecoder().decode(ciphertext.substring("v1:".length()));
    }

    private static final class CapturingThrowingSecureRandom extends SecureRandom {

        private byte[] capturedNonce;

        @Override
        public void nextBytes(byte[] bytes) {
            capturedNonce = bytes;
            Arrays.fill(bytes, (byte) 0x5a);
            throw new IllegalStateException("entropy unavailable");
        }
    }
}
