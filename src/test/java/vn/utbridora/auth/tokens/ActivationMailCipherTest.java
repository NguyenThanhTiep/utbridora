package vn.utbridora.auth.tokens;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ActivationMailCipherTest {
    private static String key(int value) {byte[] key=new byte[32];Arrays.fill(key,(byte)value);return Base64.getEncoder().encodeToString(key);}
    @Test void ciphertextIsBoundToClaimGenerationMessageAndAuthenticationTag() {
        var cipher=new ActivationMailCipher("v1","{\"v1\":\""+key(1)+"\"}");String claim=UUID.randomUUID().toString(),mail=UUID.randomUUID().toString();
        var sealed=cipher.encrypt("private-token-never-persisted-plaintext",claim,1,mail);
        assertEquals("private-token-never-persisted-plaintext",cipher.decrypt(sealed,claim,1,mail));
        assertFalse(new String(sealed.ciphertext(),java.nio.charset.StandardCharsets.ISO_8859_1).contains("private-token"));
        assertThrows(RuntimeException.class,()->cipher.decrypt(sealed,UUID.randomUUID().toString(),1,mail));
        assertThrows(RuntimeException.class,()->cipher.decrypt(sealed,claim,2,mail));
        assertThrows(RuntimeException.class,()->cipher.decrypt(sealed,claim,1,UUID.randomUUID().toString()));
        sealed.tag()[0]^=1;assertThrows(RuntimeException.class,()->cipher.decrypt(sealed,claim,1,mail));
    }
    @Test void rotationRetainsOldDecryptKeyWhileNewIssuanceUsesTheActiveKey() {
        var old=new ActivationMailCipher("v1","{\"v1\":\""+key(1)+"\"}");String claim=UUID.randomUUID().toString(),mail=UUID.randomUUID().toString();
        var sealed=old.encrypt("pending-old-message",claim,1,mail);
        var rotated=new ActivationMailCipher("v2","{\"v1\":\""+key(1)+"\",\"v2\":\""+key(2)+"\"}");
        assertEquals("pending-old-message",rotated.decrypt(sealed,claim,1,mail));
        assertEquals("v2",rotated.encrypt("new-message",claim,2,UUID.randomUUID().toString()).keyId());
        assertThrows(RuntimeException.class,()->new ActivationMailCipher("v2","{\"v2\":\""+key(2)+"\"}").decrypt(sealed,claim,1,mail));
    }
    @Test void missingOrMalformedKeysNeverEnablePlaintextFallback() {
        assertThrows(RuntimeException.class,()->new ActivationMailCipher("","").requireReady());
        assertThrows(RuntimeException.class,()->new ActivationMailCipher("x","{\"x\":\"invalid-key\"}"));
    }
}
