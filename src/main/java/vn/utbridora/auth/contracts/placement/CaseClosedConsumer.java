package vn.utbridora.auth.contracts.placement;
@FunctionalInterface
public interface CaseClosedConsumer {EventConsumeResult consume(TrustedEventContext context,CaseClosedEvent event);}
