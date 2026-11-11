package vn.utbridora.auth.modules.cooperation.integration;

import java.sql.*;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import tools.jackson.databind.json.JsonMapper;
import vn.utbridora.auth.contracts.placement.*;

@Service
public class JdbcPlacementRead implements PlacementReadPort {
    private final JdbcTemplate jdbc;
    private final JsonMapper json=JsonMapper.builder().build();
    public JdbcPlacementRead(JdbcTemplate jdbc) {this.jdbc=jdbc;}
    @Override @Transactional(propagation=Propagation.MANDATORY)
    public Optional<PlacementView> readCurrentForGuard(String placement) {
        return jdbc.query("SELECT * FROM placements WHERE id=? FOR SHARE",(r,n)->new PlacementView(r.getString("id"),
            r.getString("student_user_id"),r.getString("school_org_id"),r.getString("enterprise_org_id"),r.getString("term_id"),
            r.getString("demand_id"),r.getString("cooperation_id"),r.getString("proposal_item_id"),r.getString("state"),r.getLong("version"),
            instant(r,"effective_at"),r.getDate("start_date").toLocalDate(),r.getDate("end_date").toLocalDate(),
            instant(r,"approved_at"),instant(r,"cancelled_at"),instant(r,"completed_at"),r.getString("decision_ref")),placement).stream().findFirst();
    }
    @Override @Transactional(propagation=Propagation.MANDATORY)
    public boolean authenticDelivery(TrustedEventContext context,PlacementLifecycleEvent event) {
        if(context==null || event==null || !context.producerModule().equals("T5")) return false;
        return jdbc.queryForList("SELECT payload_json FROM placement_outbox WHERE event_id=? AND delivery_operation_id=? AND placement_id=? AND aggregate_version=? AND event_type=?",
            String.class,event.eventId(),context.deliveryOperationId(),event.placementId(),event.aggregateVersion(),event.eventType())
            .stream().anyMatch(payload->event.equals(json.readValue(payload,PlacementLifecycleEvent.class)));
    }
    private static Instant instant(ResultSet r,String column) throws SQLException {
        java.time.LocalDateTime value=r.getObject(column,java.time.LocalDateTime.class);return value==null?null:value.toInstant(java.time.ZoneOffset.UTC);
    }
}
