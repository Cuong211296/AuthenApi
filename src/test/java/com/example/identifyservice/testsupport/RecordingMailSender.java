package com.example.identifyservice.testsupport;

import jakarta.mail.internet.MimeMessage;
import org.springframework.context.annotation.Primary;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Replaces the SMTP sender in every Spring test; records messages instead of sending. */
@Component
@Primary
public class RecordingMailSender extends JavaMailSenderImpl {
    public final List<MimeMessage> sent = new CopyOnWriteArrayList<>();
    public volatile boolean failing;

    public void reset() {
        sent.clear();
        failing = false;
    }

    @Override
    public void send(MimeMessage mimeMessage) {
        if (failing) throw new MailSendException("SMTP down (test)");
        sent.add(mimeMessage);
    }
}
