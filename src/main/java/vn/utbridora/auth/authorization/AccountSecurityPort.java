package vn.utbridora.auth.authorization;

import java.util.Collection;
/** Owner T1. Called in the same transaction as capability changes. */
public interface AccountSecurityPort {
    void invalidate(Collection<String> userIds);
}
