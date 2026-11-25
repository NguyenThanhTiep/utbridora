package vn.utbridora.auth.authorization;

import java.util.Collection;
import java.util.Map;
import vn.utbridora.auth.modules.cooperation.domain.DemandModels.Page;

/** T1-owned identity projection for trusted, scope-authorized server workflows; never HTTP input. */
public interface EnterpriseMemberReferencePort {
    record Member(String userId,String displayName) {}
    Page<Member> candidates(String enterprise,String excludedUser,String search,int page,int size);
    Map<String,String> names(String enterprise,Collection<String> userIds);
    Map<String,String> organizationNames(Collection<String> enterpriseIds);
}
