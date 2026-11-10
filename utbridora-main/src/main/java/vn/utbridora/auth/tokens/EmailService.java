package vn.utbridora.auth.tokens;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailService {
    private final JavaMailSender sender;
    private final String publicUrl;
    private final String mailFrom;
    public EmailService(JavaMailSender sender,@Value("${app.public-url}") String publicUrl,
                        @Value("${app.mail-from}") String mailFrom){
        this.sender=sender; this.publicUrl=publicUrl.replaceAll("/+$", "");
        this.mailFrom=mailFrom;
    }
    public void invite(String to,String token) {
        send(to,"UT.Bridora - Invitation", "Open this link to activate your account (expires in 48h):\n"
            + publicUrl + "/accept-invitation#token=" + token);
    }
    public void reset(String to,String token) {
        send(to,"UT.Bridora - Password reset", "Open this link to reset your password (expires in 30m):\n"
            + publicUrl + "/reset-password#token=" + token);
    }
    private void send(String to,String subject,String content) {
        SimpleMailMessage mail=new SimpleMailMessage();
        mail.setFrom(mailFrom);mail.setTo(to);mail.setSubject(subject);mail.setText(content);
        sender.send(mail);
    }
}
