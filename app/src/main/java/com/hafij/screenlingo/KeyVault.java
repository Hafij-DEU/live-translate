package com.hafij.screenlingo;

import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import android.util.Log;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * Seals API keys with AES-256-GCM. The AES key is made inside the Android Keystore
 * (hardware-backed where the phone has it) and can never be read out, so the stored
 * values are useless off this phone or to other apps.
 */
final class KeyVault {
    private static final String TAG = "ScreenLingo";
    private static final String STORE = "AndroidKeyStore";
    private static final String ALIAS = "screenlingo_api_keys";
    private static final String CIPHER = "AES/GCM/NoPadding";
    private static final int TAG_BITS = 128;

    private KeyVault() {}

    /** Returns base64 of ivLength + iv + ciphertext, "" for an empty value, or null if the Keystore refused. */
    static String seal(String plain) {
        if (plain.isEmpty()) return "";
        try {
            Cipher cipher = Cipher.getInstance(CIPHER);
            cipher.init(Cipher.ENCRYPT_MODE, key());
            byte[] iv = cipher.getIV();
            byte[] sealed = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            byte[] out = new byte[1 + iv.length + sealed.length];
            out[0] = (byte) iv.length;
            System.arraycopy(iv, 0, out, 1, iv.length);
            System.arraycopy(sealed, 0, out, 1 + iv.length, sealed.length);
            return Base64.encodeToString(out, Base64.NO_WRAP);
        } catch (Exception e) {
            Log.w(TAG, "could not seal a key", e);
            return null;
        }
    }

    /** Returns "" when there is nothing saved or it cannot be opened (e.g. the Keystore key was wiped). */
    static String open(String stored) {
        if (stored == null || stored.isEmpty()) return "";
        try {
            byte[] in = Base64.decode(stored, Base64.NO_WRAP);
            int ivLength = in[0];
            Cipher cipher = Cipher.getInstance(CIPHER);
            cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(TAG_BITS, in, 1, ivLength));
            byte[] plain = cipher.doFinal(in, 1 + ivLength, in.length - 1 - ivLength);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (Exception e) {
            Log.w(TAG, "could not open a saved key", e);
            return "";
        }
    }

    private static synchronized SecretKey key() throws Exception {
        KeyStore store = KeyStore.getInstance(STORE);
        store.load(null);
        Key existing = store.getKey(ALIAS, null);
        if (existing instanceof SecretKey) return (SecretKey) existing;
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, STORE);
        generator.init(new KeyGenParameterSpec.Builder(ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build());
        return generator.generateKey();
    }
}
