package com.eventhub.eventhub_backend.mail;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class LogMailTransport implements MailTransport {

    @Override
    public void send(String to, String subject, String text, String html) {
        log.info("""

                ======== EMAIL (log mode, nothing is really sent) ========
                To: {}
                Subject: {}

                {}
                ==========================================================""", to, subject, text);
    }
}