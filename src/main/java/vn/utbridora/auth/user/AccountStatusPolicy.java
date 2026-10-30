package vn.utbridora.auth.user;

/** Explicit account lifecycle; invitation activation is separate from admin status changes. */
public final class AccountStatusPolicy {
    private AccountStatusPolicy(){}
    public static boolean adminCanTransition(AccountStatus from,AccountStatus to) {
        if(from==to) return true;
        return switch(from) {
            case PENDING_ACTIVATION -> to==AccountStatus.SUSPENDED || to==AccountStatus.DEACTIVATED;
            case ACTIVE -> to==AccountStatus.SUSPENDED || to==AccountStatus.DEACTIVATED;
            case SUSPENDED -> to==AccountStatus.ACTIVE || to==AccountStatus.DEACTIVATED;
            case DEACTIVATED -> false;
        };
    }
}
