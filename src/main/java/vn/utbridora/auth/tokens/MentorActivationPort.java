package vn.utbridora.auth.tokens;
/** Internal T1 activation hook; never a controller callback. */
public interface MentorActivationPort {
    void activated(String tokenId,String userId);
    default void lockPendingIdentity(String email) {}
    default boolean permitsPendingIdentity(String tokenId,String email,String userId) {return false;}
}
