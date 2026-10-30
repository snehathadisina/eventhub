package com.eventhub.eventhub_backend.mail;

public interface MailTransport {
    void send(String to, String subject, String text, String html) throws Exception;
}