package vn.utbridora.auth.security;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

class AppPrincipalSerializationTest {
    @Test void jdbcSessionSerializationDoesNotContainCredentialHash() throws Exception {
        String sensitive="credential-hash-marker-for-serialization-test";
        AppPrincipal principal=new AppPrincipal("test-user","test@example.test",sensitive,3,
            List.of(new SimpleGrantedAuthority("ROLE_STUDENT")));
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        try(ObjectOutputStream stream=new ObjectOutputStream(bytes)) {stream.writeObject(principal);}
        assertFalse(bytes.toString(StandardCharsets.ISO_8859_1).contains(sensitive));
        try(ObjectInputStream stream=new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            AppPrincipal restored=(AppPrincipal)stream.readObject();assertNull(restored.getPassword());
            assertEquals(principal.userId(),restored.userId());assertEquals(3,restored.securityVersion());
            assertEquals(principal.getAuthorities(),restored.getAuthorities());
        }
    }
}
