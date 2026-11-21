package vn.utbridora.auth.tokens;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.*;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import static vn.utbridora.auth.operations.WorkflowValues.*;

/** Transport-only AES-256-GCM. Keys are supplied externally; absent keys fail closed on issuance. */
@Component
public class ActivationMailCipher {
    public record Encrypted(byte[] ciphertext,String keyId,byte[] nonce,byte[] tag) {}
    private final String activeKeyId;
    private final Map<String,byte[]> keys;
    private final SecureRandom random=new SecureRandom();
    public ActivationMailCipher(@Value("${app.onboarding.mail.key-id:}") String activeKeyId,@Value("${app.onboarding.mail.keys:}") String keyring) {
        this.activeKeyId=activeKeyId;var parsed=new HashMap<String,byte[]>();
        try {
            if(keyring!=null && !keyring.isBlank()) {
                var values=JSON.readTree(keyring);if(!values.isObject() || values.size()>16) throw new IllegalArgumentException();
                values.properties().forEach(entry->{
                    if(!entry.getKey().matches("[A-Za-z0-9._-]{1,64}") || !entry.getValue().isString()) throw new IllegalArgumentException();
                    byte[] key=Base64.getDecoder().decode(entry.getValue().asString());if(key.length!=32) throw new IllegalArgumentException();parsed.put(entry.getKey(),key);
                });
            }
        } catch(RuntimeException invalid) {throw new IllegalStateException("Invalid activation mail key configuration");}
        keys=Map.copyOf(parsed);
    }
    public void requireReady() {if(!keys.containsKey(activeKeyId)) throw failure(503,"TOKEN_DELIVERY_UNAVAILABLE");}
    public Encrypted encrypt(String token,String claim,long generation,String mail) {
        requireReady();byte[] nonce=new byte[12];random.nextBytes(nonce);
        try {
            var cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,new SecretKeySpec(keys.get(activeKeyId),"AES"),new GCMParameterSpec(128,nonce));
            cipher.updateAAD(aad(claim,generation,mail));byte[] sealed=cipher.doFinal(token.getBytes(StandardCharsets.UTF_8));
            return new Encrypted(Arrays.copyOf(sealed,sealed.length-16),activeKeyId,nonce,Arrays.copyOfRange(sealed,sealed.length-16,sealed.length));
        } catch(java.security.GeneralSecurityException invalid) {throw failure(503,"TOKEN_DELIVERY_UNAVAILABLE");}
    }
    public String decrypt(Encrypted encrypted,String claim,long generation,String mail) {
        byte[] key=encrypted==null?null:keys.get(encrypted.keyId());
        if(key==null || encrypted.nonce()==null || encrypted.nonce().length!=12 || encrypted.tag()==null || encrypted.tag().length!=16 || encrypted.ciphertext()==null) throw failure(503,"TOKEN_DELIVERY_UNAVAILABLE");
        try {
            var cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,new SecretKeySpec(key,"AES"),new GCMParameterSpec(128,encrypted.nonce()));
            cipher.updateAAD(aad(claim,generation,mail));byte[] sealed=Arrays.copyOf(encrypted.ciphertext(),encrypted.ciphertext().length+16);
            System.arraycopy(encrypted.tag(),0,sealed,encrypted.ciphertext().length,16);
            return new String(cipher.doFinal(sealed),StandardCharsets.UTF_8);
        } catch(java.security.GeneralSecurityException invalid) {throw failure(503,"TOKEN_DELIVERY_UNAVAILABLE");}
    }
    private byte[] aad(String claim,long generation,String mail) {
        if(generation<1) throw failure(503,"TOKEN_DELIVERY_UNAVAILABLE");
        return (uuid(claim)+"|"+generation+"|"+uuid(mail)+"|STUDENT_ACTIVATION_V1").getBytes(StandardCharsets.UTF_8);
    }
}
