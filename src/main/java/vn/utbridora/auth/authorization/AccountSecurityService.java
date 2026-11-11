package vn.utbridora.auth.authorization;

import java.util.Collection;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import vn.utbridora.auth.user.UserRepository;
import static vn.utbridora.auth.operations.WorkflowValues.*;

@Service
public class AccountSecurityService implements AccountSecurityPort {
    private final UserRepository users;
    public AccountSecurityService(UserRepository users) {this.users=users;}
    @Override @Transactional(propagation=Propagation.MANDATORY)
    public void invalidate(Collection<String> ids) {
        ids.stream().distinct().sorted().forEach(id->{
            var user=users.findByIdForUpdate(uuid(id)).orElseThrow(()->failure(404,"RESOURCE_NOT_FOUND"));
            user.securityVersion++;
        });
        users.flush();
    }
}
