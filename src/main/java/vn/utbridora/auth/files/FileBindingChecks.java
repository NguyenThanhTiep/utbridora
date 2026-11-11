package vn.utbridora.auth.files;

import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import vn.utbridora.auth.operations.OperationFailure;

/** Owner metadata/binding validation, without claiming a storage uploader or antivirus worker exists. */
@Service
public class FileBindingChecks {
    private final JdbcTemplate jdbc;
    public FileBindingChecks(JdbcTemplate jdbc) {this.jdbc=jdbc;}
    @org.springframework.transaction.annotation.Transactional(propagation=org.springframework.transaction.annotation.Propagation.MANDATORY)
    public void requireVerificationEvidence(String actor,String enterprise,String verification,List<String> ids) {
        for(String id:ids.stream().sorted().toList()) {
            var matches=jdbc.queryForList("SELECT f.id FROM files f JOIN file_bindings b ON b.file_id=f.id WHERE f.id=? AND f.organization_id=? AND f.deleted_at IS NULL AND f.scan_status='CLEAN' AND f.content_type IN ('application/pdf','image/png','image/jpeg') AND f.size_bytes BETWEEN 1 AND 10485760 AND (f.owner_user_id=? OR f.classification='ORGANIZATION') AND b.binding_kind='ENTERPRISE_VERIFICATION' AND b.purpose='VERIFICATION' AND b.resource_id=? FOR UPDATE",String.class,id,enterprise,actor,verification);
            if(matches.size()!=1) throw new OperationFailure(422,"VERIFICATION_DATA_INVALID","Minh chứng chưa CLEAN hoặc không thuộc đúng hồ sơ và phạm vi truy cập.",null);
        }
    }
}
