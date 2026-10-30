package vn.utbridora.auth.tokens;

import java.security.*;
import java.util.Base64;
import java.util.HexFormat;

public final class TokenCodec {
    private static final SecureRandom RANDOM = new SecureRandom();
    private TokenCodec() {}
    public static String generate() {
        byte[] bytes=new byte[32]; RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
    public static String hash(String raw) {
        try {
            byte[] digest=MessageDigest.getInstance("SHA-256").digest(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}
