package org.dromara.aivideo.infra.runninghub.security;

import org.dromara.aivideo.workflow.constant.WorkflowErrorCodes;
import org.dromara.aivideo.workflow.enums.WorkflowCredentialPurpose;
import org.dromara.aivideo.workflow.service.IWorkflowCredentialWriteService;
import org.dromara.aivideo.workflow.service.IWorkflowCredentialReadService;
import org.dromara.common.core.exception.ServiceException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * RunningHub 凭据 AES-256-GCM 存储加密与基础设施边界解密实现。
 */
@Component
public class RunningHubCredentialCipher implements IWorkflowCredentialWriteService, IWorkflowCredentialReadService {

    private static final String VERSION_PREFIX = "v1:";
    private static final int KEY_BYTES = 32;
    private static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final Supplier<String> masterKeySupplier;
    private final SecureRandom secureRandom;

    @Autowired
    public RunningHubCredentialCipher(
        @Value("${aivideo.runninghub.master-key:${AI_VIDEO_RUNNINGHUB_MASTER_KEY:}}") String masterKey) {
        this(() -> masterKey, new SecureRandom());
    }

    RunningHubCredentialCipher(Supplier<String> masterKeySupplier) {
        this(masterKeySupplier, new SecureRandom());
    }

    RunningHubCredentialCipher(Supplier<String> masterKeySupplier, SecureRandom secureRandom) {
        this.masterKeySupplier = Objects.requireNonNull(masterKeySupplier);
        this.secureRandom = Objects.requireNonNull(secureRandom);
    }

    @Override
    public String encryptForStorage(WorkflowCredentialPurpose purpose, char[] plaintext) {
        if (purpose == null) {
            throw failure("凭据用途不能为空");
        }
        if (plaintext == null || plaintext.length == 0) {
            throw failure("待加密凭据不能为空");
        }
        byte[] key = null;
        byte[] nonce = null;
        byte[] plaintextBytes = null;
        byte[] aad = null;
        byte[] encrypted = null;
        byte[] payload = null;
        try {
            key = loadKey();
            plaintextBytes = encode(plaintext);
            nonce = new byte[NONCE_BYTES];
            aad = purpose.aadBytes();
            secureRandom.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"),
                new GCMParameterSpec(TAG_BITS, nonce));
            cipher.updateAAD(aad);
            encrypted = cipher.doFinal(plaintextBytes);
            payload = ByteBuffer.allocate(nonce.length + encrypted.length)
                .put(nonce).put(encrypted).array();
            return VERSION_PREFIX + Base64.getEncoder().encodeToString(payload);
        } catch (GeneralSecurityException exception) {
            throw failure("RunningHub 凭据加密失败");
        } finally {
            clear(key);
            clear(nonce);
            clear(plaintextBytes);
            clear(aad);
            clear(encrypted);
            clear(payload);
        }
    }

    @Override
    public char[] decryptForUse(WorkflowCredentialPurpose purpose, String ciphertext) {
        if (purpose == null) {
            throw failure("凭据用途不能为空");
        }
        if (ciphertext == null || !ciphertext.startsWith(VERSION_PREFIX)) {
            throw failure("RunningHub 凭据解密失败");
        }
        byte[] key = null;
        byte[] payload = null;
        byte[] nonce = null;
        byte[] encrypted = null;
        byte[] aad = null;
        byte[] plaintextBytes = null;
        try {
            key = loadKey();
            payload = Base64.getDecoder().decode(ciphertext.substring(VERSION_PREFIX.length()));
            if (payload.length <= NONCE_BYTES + TAG_BITS / Byte.SIZE) {
                throw failure("RunningHub 凭据解密失败");
            }
            nonce = Arrays.copyOfRange(payload, 0, NONCE_BYTES);
            encrypted = Arrays.copyOfRange(payload, NONCE_BYTES, payload.length);
            aad = purpose.aadBytes();
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"),
                new GCMParameterSpec(TAG_BITS, nonce));
            cipher.updateAAD(aad);
            plaintextBytes = cipher.doFinal(encrypted);
            return decode(plaintextBytes);
        } catch (GeneralSecurityException | IllegalArgumentException | CharacterCodingException exception) {
            throw failure("RunningHub 凭据解密失败");
        } finally {
            clear(key);
            clear(payload);
            clear(nonce);
            clear(encrypted);
            clear(aad);
            clear(plaintextBytes);
        }
    }

    private byte[] loadKey() {
        String encoded = masterKeySupplier.get();
        if (encoded == null || encoded.isBlank()) {
            throw failure("RunningHub 主密钥未配置");
        }
        byte[] key;
        try {
            key = Base64.getDecoder().decode(encoded.trim());
        } catch (IllegalArgumentException exception) {
            throw failure("RunningHub 主密钥不是合法 Base64");
        }
        if (key.length != KEY_BYTES) {
            Arrays.fill(key, (byte) 0);
            throw failure("RunningHub 主密钥解码后必须为 32 字节");
        }
        return key;
    }

    private byte[] encode(char[] plaintext) {
        ByteBuffer encoded = StandardCharsets.UTF_8.encode(CharBuffer.wrap(plaintext));
        byte[] result = new byte[encoded.remaining()];
        encoded.get(result);
        if (encoded.hasArray()) {
            Arrays.fill(encoded.array(), (byte) 0);
        }
        return result;
    }

    private char[] decode(byte[] plaintext) throws CharacterCodingException {
        CharBuffer decoded = StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(plaintext));
        char[] result = new char[decoded.remaining()];
        decoded.get(result);
        if (decoded.hasArray()) {
            Arrays.fill(decoded.array(), '\0');
        }
        return result;
    }

    private void clear(byte[] value) {
        if (value != null) {
            Arrays.fill(value, (byte) 0);
        }
    }

    private ServiceException failure(String message) {
        return new ServiceException(message, WorkflowErrorCodes.WORKFLOW_CONFIGURATION_INVALID);
    }
}
