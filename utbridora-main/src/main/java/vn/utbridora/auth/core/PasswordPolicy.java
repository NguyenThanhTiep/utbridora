package vn.utbridora.auth.core;

import java.nio.charset.StandardCharsets;

/** BCrypt has a 72-byte input limit. Do not silently truncate long UTF-8 passwords. */
public final class PasswordPolicy {
    private PasswordPolicy() {}
    public static void requireValid(String password) {
        if(password==null || password.codePointCount(0,password.length())<12
                || password.getBytes(StandardCharsets.UTF_8).length>72)
            throw new IllegalArgumentException("Password must have at least 12 characters and at most 72 UTF-8 bytes");
    }
}
