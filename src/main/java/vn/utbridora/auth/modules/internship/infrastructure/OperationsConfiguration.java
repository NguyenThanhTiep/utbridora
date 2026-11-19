package vn.utbridora.auth.modules.internship.infrastructure;
@org.springframework.context.annotation.Configuration
public class OperationsConfiguration {
    @org.springframework.context.annotation.Bean("internshipOperationsClock") public java.time.Clock clock() {return java.time.Clock.systemUTC();}
}
