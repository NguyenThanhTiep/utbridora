package vn.utbridora.auth.modules.cooperation.application;

import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.json.JsonMapper;
import vn.utbridora.auth.contracts.placement.*;
import vn.utbridora.auth.modules.internship.integration.PlacementLifecycleConsumer;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PlacementOutboxWorkerTest {
    final JdbcTemplate jdbc=mock(JdbcTemplate.class);
    final PlacementLifecycleConsumer consumer=mock(PlacementLifecycleConsumer.class);
    final JsonMapper json=JsonMapper.builder().build();
    final PlacementOutboxWorker worker=new PlacementOutboxWorker(jdbc,consumer);
    final String eventId=id();
    final PlacementLifecycleEvent event=new PlacementLifecycleEvent(eventId,"PlacementApproved",1,id(),2L,2L,id(),id(),id(),id(),Instant.EPOCH,Instant.EPOCH,id(),null,null);
    static String id(){return UUID.randomUUID().toString();}
    void row(String body) {when(jdbc.queryForList(anyString())).thenReturn(List.of(Map.of("event_id",eventId,"delivery_operation_id",id(),"payload_json",body,"attempt_count",0)));}
    @ParameterizedTest @ValueSource(strings={"malformed","missing-nullable","extra","fractional","coerced","wrong-id","null"})
    void invalidWireDataIsRejectedWithoutCallingConsumer(String damage) {
        String body=json.writeValueAsString(event);
        body=switch(damage) {
            case "malformed"->"{";
            case "missing-nullable"->body.replace(",\"reasonCode\":null","");
            case "extra"->body.replace("{","{\"unexpected\":true,");
            case "fractional"->body.replace("\"aggregateVersion\":2","\"aggregateVersion\":2.5");
            case "coerced"->body.replace("\"schemaVersion\":1","\"schemaVersion\":\"1\"");
            case "wrong-id"->body.replace(eventId,id());default->"null";
        };
        row(body);worker.deliverPending();verifyNoInteractions(consumer);
        verify(jdbc).update(contains("state='DLQ'"),eq("INVALID_SCHEMA"),eq(eventId));
    }
    @Test void validDeliveryRetainsNullableFieldsAndAcknowledgesCommittedVersion() {
        row(json.writeValueAsString(event));when(consumer.consume(any(),eq(event))).thenReturn(new EventConsumeResult(eventId,"ACK_APPLIED",2L,"APPLIED",null));
        worker.deliverPending();verify(jdbc).update(contains("state='DONE'"),eq("APPLIED"),eq(eventId));
    }
    @Test void unrelatedAckRemainsPendingAndRetries() {
        row(json.writeValueAsString(event));when(consumer.consume(any(),eq(event))).thenReturn(new EventConsumeResult(id(),"ACK_APPLIED",2L,"APPLIED",null));
        worker.deliverPending();verify(jdbc).update(contains("attempt_count=attempt_count+1"),eq(1),eq("SOURCE_UNAVAILABLE"),eq(eventId),eq(0));
    }
    @Test void ackWithoutAppliedVersionDoesNotDiscardPendingDelivery() {
        row(json.writeValueAsString(event));when(consumer.consume(any(),eq(event))).thenReturn(new EventConsumeResult(eventId,"ACK_APPLIED",null,"APPLIED",null));
        worker.deliverPending();verify(jdbc).update(contains("attempt_count=attempt_count+1"),eq(1),eq("SOURCE_UNAVAILABLE"),eq(eventId),eq(0));
    }
}
