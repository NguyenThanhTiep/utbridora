package vn.utbridora.auth.tokens;
/** Internal T1 activation hook; never a controller callback. */
public interface MentorActivationPort {void activated(String tokenId,String userId);}
