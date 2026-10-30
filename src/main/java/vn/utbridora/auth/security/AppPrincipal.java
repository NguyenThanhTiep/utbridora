package vn.utbridora.auth.security;

import java.io.Serializable;
import java.util.*;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/** Minimal serializable session principal: password hash is NOT serialized to JDBC session. */
public final class AppPrincipal implements UserDetails, Serializable {
    private static final long serialVersionUID=1L;
    private final String userId;
    private final String email;
    private transient String passwordHash;
    private final long securityVersion;
    private final List<GrantedAuthority> permissions;
    public AppPrincipal(String userId,String email,String passwordHash,long securityVersion,
            List<? extends GrantedAuthority> permissions) {
        this.userId=userId;this.email=email;this.passwordHash=passwordHash;
        this.securityVersion=securityVersion;this.permissions=new ArrayList<>(permissions);
    }
    public String userId(){return userId;}
    public long securityVersion(){return securityVersion;}
    @Override public Collection<? extends GrantedAuthority> getAuthorities(){return permissions;}
    @Override public String getPassword(){return passwordHash;}
    @Override public String getUsername(){return email;}
}
