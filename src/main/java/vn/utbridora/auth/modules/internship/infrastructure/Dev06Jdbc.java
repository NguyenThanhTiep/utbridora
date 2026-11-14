package vn.utbridora.auth.modules.internship.infrastructure;

import java.time.*;
import java.time.temporal.ChronoUnit;
import org.springframework.dao.DataAccessException;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static vn.utbridora.auth.modules.internship.infrastructure.WeeklyReportRuntimeChecks.*;

/** DEV06 persistence helpers, not a datasource, transaction manager or schema installer. */
final class Dev06Jdbc {
    private Dev06Jdbc() {}
    static void ready(Runnable schema, boolean write) {
        if (write) {
            mutationTransaction();
            if (!Integer.valueOf(TransactionDefinition.ISOLATION_READ_COMMITTED).equals(
                    TransactionSynchronizationManager.getCurrentTransactionIsolationLevel())) throw unavailable();
        } else transaction();
        try { schema.run(); } catch (DataAccessException invalidSchema) { throw unavailable(); }
    }
    static String identifier(String value) {
        if (value == null || !value.matches("[a-z][a-z0-9_]{0,63}")) throw new IllegalArgumentException("Invalid SQL identifier");
        return "`" + value + "`";
    }
    static LocalDateTime micros(Instant value) {
        return value == null ? null : LocalDateTime.ofInstant(value.truncatedTo(ChronoUnit.MICROS), ZoneOffset.UTC);
    }
    static Integer remainder(Instant value) { return value == null ? null : value.getNano() % 1000; }
    static Instant instant(LocalDateTime value, Integer remainder) {
        if (value == null && remainder == null) return null;
        if (value == null || remainder == null || remainder < 0 || remainder > 999 || value.getNano() % 1000 != 0) throw unavailable();
        return value.toInstant(ZoneOffset.UTC).plusNanos(remainder);
    }
}
