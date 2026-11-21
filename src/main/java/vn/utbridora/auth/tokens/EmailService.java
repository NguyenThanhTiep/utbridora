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
    public void activation(String to,String token,String messageId) {
        try {
            var message=new jakarta.mail.internet.MimeMessage((jakarta.mail.Session)null) {
                @Override protected void updateMessageID() throws jakarta.mail.MessagingException {setHeader("Message-ID","<"+java.util.UUID.fromString(messageId)+"@activation.utbridora>");}
            };
            message.setFrom(mailFrom);message.setRecipients(jakarta.mail.Message.RecipientType.TO,to);
            message.setSubject("UT.Bridora - Student activation","UTF-8");
            message.setText("Open this link to activate your account (expires in 48h):\n"+publicUrl+"/accept-invitation#token="+token,"UTF-8");
            sender.send(message);
        } catch(jakarta.mail.MessagingException invalid) {throw new org.springframework.mail.MailParseException("Activation mail could not be constructed");}
    }
    private void send(String to,String subject,String content) {
        SimpleMailMessage mail=new SimpleMailMessage();
        mail.setFrom(mailFrom);mail.setTo(to);mail.setSubject(subject);mail.setText(content);
        sender.send(mail);
    }
}
