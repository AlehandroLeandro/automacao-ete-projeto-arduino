package br.com.carbonifera.ete.device;

import java.security.SecureRandom;

public class DeviceKeyGenerator {
    private static final String UPPERCASE = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final String LOWERCASE = "abcdefghijklmnopqrstuvwxyz";
    private static final String DIGITS = "0123456789";
    private static final String SYMBOLS = "!@#$%^&*-_=+?";
    private static final String ALPHABET = UPPERCASE + LOWERCASE + DIGITS + SYMBOLS;
    private static final int KEY_LENGTH = 24;

    private final SecureRandom random;

    public DeviceKeyGenerator() {
        this(new SecureRandom());
    }

    DeviceKeyGenerator(SecureRandom random) {
        this.random = random;
    }

    public String generate() {
        char[] key = new char[KEY_LENGTH];
        key[0] = randomChar(UPPERCASE);
        key[1] = randomChar(LOWERCASE);
        key[2] = randomChar(DIGITS);
        key[3] = randomChar(SYMBOLS);
        for (int i = 4; i < key.length; i++) {
            key[i] = randomChar(ALPHABET);
        }
        for (int i = key.length - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            char value = key[i];
            key[i] = key[j];
            key[j] = value;
        }
        return new String(key);
    }

    private char randomChar(String alphabet) {
        return alphabet.charAt(random.nextInt(alphabet.length()));
    }
}
