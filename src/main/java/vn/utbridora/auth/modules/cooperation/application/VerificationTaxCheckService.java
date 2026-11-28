package vn.utbridora.auth.modules.cooperation.application;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import vn.utbridora.auth.modules.cooperation.api.VerificationTaxCheckView;
import vn.utbridora.auth.modules.cooperation.persistence.VerificationTaxCheckStore;
import vn.utbridora.auth.operations.OperationFailure;

@Service
@Transactional(propagation=Propagation.NEVER)
public class VerificationTaxCheckService {
    private final VerificationService verifications;
    private final VerificationTaxCheckStore store;
    public VerificationTaxCheckService(VerificationService verifications, VerificationTaxCheckStore store) {
        this.verifications = verifications; this.store = store;
    }
    public VerificationTaxCheckView read(Authentication auth, String school, String id, boolean refresh) {
        // The existing read enforces live session/delegation, school scope, DRAFT privacy and submitted snapshots.
        var view = verifications.detail(auth, school, id, true);
        if (refresh) store.refresh(view); else store.enqueue(view); // Adopt pre-feature submissions idempotently.
        var result = store.read(view);
        var current = verifications.detail(auth, school, id, true);
        if (current.version() != view.version()) throw new OperationFailure(409, "VERSION_CONFLICT", "Hồ sơ vừa thay đổi. Vui lòng tải lại.", null);
        return result;
    }
}
