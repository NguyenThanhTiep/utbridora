package vn.utbridora.auth.bootstrap;

import org.springframework.boot.SpringApplication;
import vn.utbridora.auth.UtBridoraApplication;

/** One-shot Docker entry point for the explicit local seed-demo command. Credentials stay in environment. */
public final class LocalDemoImport {
    private LocalDemoImport() {}
    public static void main(String[] args) {
        var app=new SpringApplication(UtBridoraApplication.class);
        app.setAdditionalProfiles("local");
        try(var context=app.run("--app.demo.local.enabled=true","--app.demo.local.append-existing=true",
                "--server.address=127.0.0.1","--server.port=0","--app.t5.placement-delivery.enabled=false",
                "--logging.level.org.springframework.jdbc=WARN","--logging.level.org.hibernate.SQL=WARN")) {
            System.out.println("Local demo ready. Existing accounts/passwords were preserved; completed imports are not replayed.");
        }
    }
}
