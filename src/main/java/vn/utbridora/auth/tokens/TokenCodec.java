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
    /** Bind activation retries without introducing a fast password verifier into a DB dump.
     * The high-entropy raw invitation token is never stored; its DB hash cannot compute this MAC. */
    public static String activationRequestProof(String raw,String password,String displayName) {
        try {
            var mac=javax.crypto.Mac.getInstance("HmacSHA256");
            mac.init(new javax.crypto.spec.SecretKeySpec(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8),"HmacSHA256"));
            byte[] request=vn.utbridora.auth.operations.WorkflowValues.JSON.writeValueAsBytes(java.util.Arrays.asList("ACCOUNT_INVITATION_ACCEPT_V11",password,displayName));
            return HexFormat.of().formatHex(mac.doFinal(request));
        } catch(java.security.GeneralSecurityException unavailable) {throw new IllegalStateException("Activation request binding unavailable");}
    }
}
