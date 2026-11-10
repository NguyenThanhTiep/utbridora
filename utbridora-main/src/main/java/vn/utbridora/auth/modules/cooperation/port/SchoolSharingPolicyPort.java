package vn.utbridora.auth.modules.cooperation.port;
import java.time.Instant;
import java.util.List;
public interface SchoolSharingPolicyPort {
    record SchoolSharingPolicy(String schoolOrgId,String termId,long version,List<String> allowedFields,Instant updatedAt,String updatedByUserId) {}
    SchoolSharingPolicy current(String school,String term);
}
