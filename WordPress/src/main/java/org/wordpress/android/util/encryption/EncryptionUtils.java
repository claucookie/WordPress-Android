package org.wordpress.android.util.encryption;

import android.util.Base64;

import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.libsodium.jni.NaCl;

import java.util.List;
import java.util.UUID;

public class EncryptionUtils {
    static final int ABYTES = NaCl.sodium().crypto_secretstream_xchacha20poly1305_abytes();
    static final int BOX_SEALBYTES = NaCl.sodium().crypto_box_sealbytes();
    static final int KEYBYTES = NaCl.sodium().crypto_secretstream_xchacha20poly1305_keybytes();
    static final int STATEBYTES = NaCl.sodium().crypto_secretstream_xchacha20poly1305_statebytes();
    static final short TAG_FINAL = (short) NaCl.sodium().crypto_secretstream_xchacha20poly1305_tag_final();
    static final short TAG_MESSAGE = (short) NaCl.sodium().crypto_secretstream_xchacha20poly1305_tag_message();
    private static final int HEADERBYTES = NaCl.sodium().crypto_secretstream_xchacha20poly1305_headerbytes();
    private static final int BASE64_FLAGS = Base64.NO_WRAP;
    private static final String KEYED_WITH = "v1";
    private static final String ANDROID_PLATFORM = "android";
    private static final byte[] STATE = new byte[STATEBYTES];
    private static final String FIELD_KEYED_WITH = "keyedWith";
    private static final String FIELD_LOGS_ID = "logsId";
    private static final String FIELD_ENCRYPTED_KEY = "encryptedKey";
    private static final String FIELD_HEADER = "header";
    private static final String FIELD_MESSAGES = "messages";


    /**
     * This method is in charge of encrypting logs using the symmetric cypher
     * XCHACHA20 and the cryptographic message authentication code (MAC) POLY1305.
     * For more information about the encryption process you can find it here
     * https://libsodium.gitbook.io/doc/secret-key_cryptography/secretstream.
     *
     * @param publicKey   The decoded public key used to encrypt the encryption key or shared key.
     * @param logMessages The list of messages we want to encrypt.
     * @return a JSON String containing the following structure:
     * <p>
     * ```
     * {
     * "keyedWith": "v1",
     * "logsId": "<unique_identifier>",            // The UUID corresponding to this bunch of logs
     * "encryptedKey": "<base_64_encrypted_key>",  // The encrypted AES key, base-64 encoded
     * "header": "<base_64_encoded_header>",       // The xchacha20poly1305 stream header, base-64 encoded
     * "messages": [<base_64_encrypted_msgs>]      // the encrypted log messages, base-64 encoded
     * }
     * ```
     */
    public static String generateJSONEncryptedLogs(final byte[] publicKey,
                                                   final List<String> logMessages) throws JSONException {
        // Schema version
        JSONObject encryptionDataJson = new JSONObject();
        encryptionDataJson.put(FIELD_KEYED_WITH, KEYED_WITH);

        // Logs UUID
        UUID logsUUID = generateLogsUUID(KEYED_WITH, ANDROID_PLATFORM, System.currentTimeMillis());
        encryptionDataJson.put(FIELD_LOGS_ID, logsUUID.toString());

        // Encryption key
        final byte[] secretKey = createEncryptionKey();
        final byte[] encryptedSecretKey = encryptEncryptionKey(publicKey, secretKey);
        encryptionDataJson.put(FIELD_ENCRYPTED_KEY, encodeToBase64(encryptedSecretKey));

        // Header
        final byte[] encryptedHeader = createEncryptedHeader(secretKey);
        encryptionDataJson.put(FIELD_HEADER, encodeToBase64(encryptedHeader));

        // Log messages
        JSONArray encryptedAndEncodedMessagesJson = new JSONArray();
        for (String message : logMessages) {
            final byte[] encryptedMessage = encryptMessage(message, TAG_MESSAGE);
            encryptedAndEncodedMessagesJson.put(encodeToBase64(encryptedMessage));
        }

        // Final tag
        final byte[] encryptedDataBase64 = encryptMessage("", TAG_FINAL);
        encryptedAndEncodedMessagesJson.put(encodeToBase64(encryptedDataBase64));
        encryptionDataJson.put(FIELD_MESSAGES, encryptedAndEncodedMessagesJson);

        return encryptionDataJson.toString();
    }

    static UUID generateLogsUUID(String version, String platform, long currentTimeMillis) {
        String source = version + platform + currentTimeMillis;
        byte[] bytes = source.getBytes();
        // Using UUIDv3 to take more control over the UUID generation
        return UUID.nameUUIDFromBytes(bytes);
    }

    /**
     * This method retrieves the value of the field "logsId".
     *
     * @param encryptedLogsJson The encrypted logs as JSONObject.
     * @return a String with the value for "logsId" field.
     */
    public static String getLogsUUID(@NotNull JSONObject encryptedLogsJson) {
        String logsUUID = encryptedLogsJson.optString(FIELD_LOGS_ID);
        return logsUUID == null ? "" : logsUUID;
    }

    private static byte[] createEncryptionKey() {
        final byte[] secretKey = new byte[KEYBYTES];
        NaCl.sodium().crypto_secretstream_xchacha20poly1305_keygen(secretKey);
        return secretKey;
    }

    private static byte[] encryptEncryptionKey(final byte[] publicKeyBytes,
                                               final byte[] data) {
        final byte[] encryptedData = new byte[KEYBYTES + BOX_SEALBYTES];
        NaCl.sodium().crypto_box_seal(encryptedData, data, KEYBYTES, publicKeyBytes);
        return encryptedData;
    }

    private static byte[] createEncryptedHeader(final byte[] key) {
        final byte[] header = new byte[HEADERBYTES];
        NaCl.sodium().crypto_secretstream_xchacha20poly1305_init_push(STATE, header, key);
        return header;
    }

    private static byte[] encryptMessage(final String message,
                                         final short tag) {
        final int[] encryptedDataLengthOutput = new int[0]; // opting not to get this value
        final byte[] additionalData = new byte[0]; // opting not to use this value
        final int additionalDataLength = 0;
        final byte[] dataBytes = message.getBytes();
        final byte[] encryptedMessage = new byte[dataBytes.length + ABYTES];

        NaCl.sodium().crypto_secretstream_xchacha20poly1305_push(
                STATE,
                encryptedMessage,
                encryptedDataLengthOutput,
                dataBytes,
                dataBytes.length,
                additionalData,
                additionalDataLength,
                tag);

        return encryptedMessage;
    }

    private static String encodeToBase64(byte[] data) {
        return Base64.encodeToString(data, BASE64_FLAGS);
    }
}

