package vn.utbridora.auth.operations;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.json.JsonMapper;

/**
 * C.0/G.1/G.2 shared REST command ledger. The caller must perform current authentication,
 * authorization and schema validation before entering, then put version checks and every
 * domain/audit/outbox write inside the mutation. All writes use the caller's transaction.
 * No independently committed IN_PROGRESS reservation, no per-module command table.
 */
@Service
public class OperationLedger {
    private static final JsonMapper JSON=JsonMapper.builder().build();
    private static final Set<String> SECRET_FIELDS=Set.of("password","newpassword","currentpassword",
            "token","accesstoken","refreshtoken","csrftoken","sessionid");
    private final JdbcTemplate jdbc;
    private final Clock clock;

    @Autowired public OperationLedger(JdbcTemplate jdbc) { this(jdbc,Clock.systemUTC()); }
    public OperationLedger(JdbcTemplate jdbc,Clock clock) { this.jdbc=jdbc;this.clock=clock; }

    /** Server-constructed scope; never bind this type from a public request body. */
    public record Command(String actorId,String organizationId,String operationCode,String key,
                          Object payload,int responseStatus) {
        public Command {
            actorId=uuid(actorId); organizationId=organizationId==null?null:uuid(organizationId); key=uuid(key);
            if (operationCode==null || !operationCode.matches("[A-Z][A-Z0-9_.:]{1,99}")
                    || responseStatus<200 || responseStatus>299 || payload==null) throw invalid();
        }
        String scope() { return "USER:"+actorId+"|ORG:"+(organizationId==null?"GLOBAL":organizationId); }
    }

    private record Stored(String id,String hash,String state,String response,Instant expiresAt) {}

    @Transactional(propagation=Propagation.MANDATORY)
    public <T> T execute(Command command,Class<T> type,Supplier<T> mutation,Function<T,String> effectReference) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Operation ledger requires the domain transaction");
        }
        Objects.requireNonNull(type);Objects.requireNonNull(mutation);Objects.requireNonNull(effectReference);
        String hash=payloadHash(command.payload()), reservation=UUID.randomUUID().toString();
        Integer previousTimeout=jdbc.queryForObject("SELECT @@SESSION.innodb_lock_wait_timeout",Integer.class);
        jdbc.execute("SET SESSION innodb_lock_wait_timeout=5");
        try {
            Instant now=clock.instant();
            jdbc.update("INSERT INTO operation_records (id,actor_id,actor_scope_key,operation_code,idempotency_key,payload_hash,state,created_at) VALUES (?,?,?,?,?,?,'IN_PROGRESS',?) ON DUPLICATE KEY UPDATE id=id",
                    reservation,command.actorId(),command.scope(),command.operationCode(),command.key(),hash,Timestamp.from(now));
            Stored stored=jdbc.queryForObject("SELECT id,payload_hash,state,response_json,response_expires_at FROM operation_records WHERE actor_scope_key=? AND operation_code=? AND idempotency_key=? FOR UPDATE",
                    (row,index)->new Stored(row.getString(1),row.getString(2),row.getString(3),row.getString(4),
                            row.getTimestamp(5)==null?null:row.getTimestamp(5).toInstant()),
                    command.scope(),command.operationCode(),command.key());
            if (!stored.hash().equals(hash)) throw new OperationFailure(409,"IDEMPOTENCY_KEY_REUSED",
                    "Mã thao tác đã được dùng với dữ liệu khác.",null);
            if (!stored.id().equals(reservation)) {
                if (!"DONE".equals(stored.state())) throw inProgress();
                if (stored.response()==null || stored.expiresAt()==null || !clock.instant().isBefore(stored.expiresAt())) {
                    throw new OperationFailure(409,"IDEMPOTENCY_RESPONSE_EXPIRED","Kết quả thao tác đã hết hạn; không thực hiện lại thao tác.",null);
                }
                return JSON.readValue(stored.response(),type);
            }
            T result=Objects.requireNonNull(mutation.get(),"Operation response must not be null");
            Object response=JSON.convertValue(result,Object.class);
            rejectSecrets(response);
            String effect=effectReference.apply(result);
            if (effect!=null) effect=uuid(effect);
            jdbc.update("UPDATE operation_records SET state='DONE',effect_ref=?,response_status=?,response_json=?,response_expires_at=? WHERE id=?",
                    effect,command.responseStatus(),JSON.writeValueAsString(result),Timestamp.from(clock.instant().plus(Duration.ofHours(24))),reservation);
            return result;
        } catch (DataAccessException failure) {
            for (Throwable cause=failure;cause!=null;cause=cause.getCause()) {
                if (cause instanceof SQLException sql) {
                    if (sql.getErrorCode()==1205) throw inProgress();
                    if (sql.getErrorCode()==1213) throw new OperationFailure(503,"TRANSIENT_FAILURE",
                            "Thao tác đang tranh chấp dữ liệu. Vui lòng thử lại cùng mã thao tác.",null);
                }
            }
            throw failure;
        } finally {
            jdbc.execute("SET SESSION innodb_lock_wait_timeout="+previousTimeout);
        }
    }

    public static String payloadHash(Object value) {
        try {
            byte[] bytes=JSON.writeValueAsString(canonical(JSON.convertValue(value,Object.class),null)).getBytes(StandardCharsets.UTF_8);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    private static Object canonical(Object value,String field) {
        if (value instanceof Map<?,?> object) {
            var sorted=new TreeMap<String,Object>();
            object.forEach((key,item)->sorted.put(key.toString(),canonical(item,key.toString())));
            return sorted;
        }
        if (value instanceof List<?> array) {
            var ordered=new ArrayList<Object>();array.forEach(item->ordered.add(canonical(item,null)));
            if ("selectedRowIds".equals(field) || "caseIds".equals(field)) ordered.sort(Comparator.comparing(JSON::writeValueAsString));
            return ordered;
        }
        return value;
    }
    private static void rejectSecrets(Object value) {
        if (value instanceof Map<?,?> object) {
            object.forEach((key,item)->{
                if (SECRET_FIELDS.contains(key.toString().toLowerCase(Locale.ROOT))) {
                    throw new OperationFailure(500,"INTERNAL_ERROR","Kết quả thao tác không được phép lưu vào bộ nhớ phản hồi.",null);
                }
                rejectSecrets(item);
            });
        } else if (value instanceof List<?> array) array.forEach(OperationLedger::rejectSecrets);
    }
    private static String uuid(String value) {
        if (value==null || !value.matches("[0-9a-fA-F]{8}(-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}")) throw invalid();
        return value.toLowerCase(Locale.ROOT);
    }
    private static OperationFailure invalid() { return new OperationFailure(400,"INVALID_REQUEST","Thông tin thao tác chưa hợp lệ.",null); }
    private static OperationFailure inProgress() { return new OperationFailure(409,"REQUEST_IN_PROGRESS","Thao tác đang được xử lý. Vui lòng thử lại sau.",1); }
}
