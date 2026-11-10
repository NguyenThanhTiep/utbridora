package vn.utbridora.auth.security;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import vn.utbridora.auth.tokens.TokenCodec;
import vn.utbridora.auth.core.PasswordPolicy;

class TokenCodecTest {
    @Test void tokensAreUniqueAndStoredHashed() {
        String a=TokenCodec.generate(),b=TokenCodec.generate();
        assertNotEquals(a,b);
        assertEquals(64,TokenCodec.hash(a).length());
        assertNotEquals(a,TokenCodec.hash(a));
    }
    @Test void rejectTooShortOrTooLongBcryptSecrets() {
        assertThrows(IllegalArgumentException.class,()->PasswordPolicy.requireValid("12345678901"));
        assertThrows(IllegalArgumentException.class,()->PasswordPolicy.requireValid("x".repeat(73)));
        assertDoesNotThrow(()->PasswordPolicy.requireValid("long-valid-password-123"));
    }
}
