package com.kitepost.service.mail;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** What the reply reader takes from the mail itself: bounce reports, auto-replies, and our own Message-ID inside a bounce. */
class ImapPollerTest {
    private static final Session SESSION = Session.getInstance(new Properties());
    private static final String OURS = "<kp.0123456789abcdef@ours.com>";

    private static MimeMessage plain(String text) throws Exception {
        MimeMessage m = new MimeMessage(SESSION);
        m.setText(text);
        m.saveChanges();
        return m;
    }

    /** A delivery report the way mail servers build it: explanation, status, and the original's headers. */
    private static MimeMessage deliveryReport() throws Exception {
        MimeMultipart report = new MimeMultipart("report; report-type=delivery-status");
        MimeBodyPart text = new MimeBodyPart();
        text.setText("Your message wasn't delivered to anna@b.com because the address couldn't be found.");
        report.addBodyPart(text);
        MimeBodyPart original = new MimeBodyPart();
        original.setContent("Message-ID: " + OURS + "\nSubject: hello\n", "text/rfc822-headers");
        report.addBodyPart(original);
        MimeMessage m = new MimeMessage(SESSION);
        m.setContent(report);
        m.saveChanges();
        return m;
    }

    @Test
    void aDeliveryReportIsRecognisedAndPointsToOurEmail() throws Exception {
        MimeMessage m = deliveryReport();
        assertEquals("bounce", ImapPoller.kindOf(m));
        List<String> ids = new ArrayList<>();
        ImapPoller.ourIds(m, ids, 0);
        assertEquals(List.of(OURS), ids);
    }

    @Test
    void anAutomaticReplyIsRecognisedFromItsHeaders() throws Exception {
        MimeMessage a = plain("I'm away.");
        a.setHeader("Auto-Submitted", "auto-replied");
        assertEquals("auto", ImapPoller.kindOf(a));

        MimeMessage b = plain("I'm away.");
        b.setHeader("X-Autoreply", "yes");
        assertEquals("auto", ImapPoller.kindOf(b));

        MimeMessage c = plain("I'm away.");
        c.setHeader("Precedence", "auto_reply");
        assertEquals("auto", ImapPoller.kindOf(c));
    }

    @Test
    void aPersonsReplyIsNotTreatedAsAutomatic() throws Exception {
        MimeMessage m = plain("Sounds good.");
        assertNull(ImapPoller.kindOf(m));
        m.setHeader("Auto-Submitted", "no");
        assertNull(ImapPoller.kindOf(m));
        List<String> ids = new ArrayList<>();
        ImapPoller.ourIds(m, ids, 0);
        assertEquals(List.of(), ids);
    }
}
