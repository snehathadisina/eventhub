package com.eventhub.eventhub_backend.mail;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;

@Configuration
public class MailConfig {

    @Bean
    MailTransport mailTransport(@Value("${app.mail.mode:log}") String mode,
                                @Value("${app.mail.from:EventHub <no-reply@eventhub.local>}") String from,
                                ObjectProvider<JavaMailSender> mailSender) {
        if ("smtp".equalsIgnoreCase(mode)) {
            JavaMailSender sender = mailSender.getIfAvailable();
            if (sender == null) {
                throw new IllegalStateException(
                        "app.mail.mode=smtp needs spring.mail.host. Start the app with the 'smtp' profile.");
            }
            return new SmtpMailTransport(sender, from);
        }
        return new LogMailTransport();
    }
}