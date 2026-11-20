package com.kitepost.service.mail;

import com.kitepost.service.mail.ReplyAnalyzer.Analysis;
import com.kitepost.service.mail.ReplyAnalyzer.Incoming;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Replies are labelled with keyword rules only: no AI call, no suggested answer. */
class ReplyAnalyzerTest {
    private final ReplyAnalyzer analyzer = new ReplyAnalyzer();

    private Analysis read(String from, String subject, String text, String kind) {
        return analyzer.analyse(new Incoming(from, "Someone", subject, text, "<m@x>", null, null, false, kind), null, null);
    }

    private static final String OUR_EMAIL_QUOTED = "\n\nOn Mon, 3 Mar 2025 at 10:00, Sales Team <sales@ours.com> wrote:\n"
            + "> Hi Anna,\n> We help clinics book more patients. If you are not interested, reply unsubscribe.\n> Best, Sam\n"
            + "> You are receiving this because ... To unsubscribe reply STOP.\n";

    /** {expected label, from, subject, text, kind} */
    private static final String[][] CASES = {
            // ---------- interested ----------
            {"interested", "a@b.com", "Re: hello", "Hi Sam,\nThis sounds interesting. Can you send me pricing?", null},
            {"interested", "a@b.com", "Re: hello", "Yes, let's set up a call next week.", null},
            {"interested", "a@b.com", "Re: hello", "We would love to learn more. Please call me on 555-1234.", null},
            {"interested", "a@b.com", "Re: hello", "Please send details and a quote.", null},
            {"interested", "a@b.com", "Re: hello", "Sounds good, let's talk." + OUR_EMAIL_QUOTED, null},
            {"interested", "a@b.com", "Re: hello", "Please do not hesitate to contact me about this.", null},
            {"interested", "a@b.com", "Re: hello", "Thanks!\n\nYes interested. When can we meet?\n\n--\nJohn Smith\nIf you wish to unsubscribe from our newsletter click here", null},
            {"interested", "a@b.com", "AW: hallo", "Hallo, das klingt interessant. Bitte senden Sie uns ein Angebot.", null},
            {"interested", "a@b.com", "RE: bonjour", "Bonjour, ça m'intéresse. Pouvez-vous m'envoyer un devis ?", null},
            {"interested", "a@b.com", "Re: hoi", "Hoi, klinkt goed! Stuur mij maar meer informatie.", null},
            {"interested", "a@b.com", "Re: hola", "Hola, nos interesa. Envíenos más información.", null},
            {"interested", "a@b.com", "Re: ciao", "Ciao, siamo interessati. Inviateci un preventivo.", null},
            {"interested", "a@b.com", "Re: ola", "Olá, temos interesse. Envie mais informações.", null},
            // ---------- question ----------
            {"question", "a@b.com", "Re: hello", "Do you work with clinics in Spain?", null},
            {"question", "a@b.com", "Re: hello", "How much does it cost?", null},
            {"question", "a@b.com", "Re: hallo", "Wie funktioniert das genau?", null},
            {"question", "a@b.com", "Re: hello", "Which countries do you cover", null},
            // ---------- not interested ----------
            {"not_interested", "a@b.com", "Re: hello", "Not interested, thanks.", null},
            {"not_interested", "a@b.com", "Re: hello", "No thank you.", null},
            {"not_interested", "a@b.com", "Re: hello", "We already have a supplier for this.", null},
            {"not_interested", "a@b.com", "Re: hello", "We are no longer interested." + OUR_EMAIL_QUOTED, null},
            {"not_interested", "a@b.com", "Re: hallo", "Kein Interesse, danke.", null},
            {"not_interested", "a@b.com", "Re: bonjour", "Non merci, nous n'avons pas besoin de ce service.", null},
            {"not_interested", "a@b.com", "Re: hola", "No nos interesa, gracias.", null},
            {"not_interested", "a@b.com", "Re: hoi", "Geen interesse, bedankt.", null},
            {"not_interested", "a@b.com", "Re: ciao", "Non siamo interessati.", null},
            {"not_interested", "a@b.com", "Re: ola", "Não temos interesse.", null},
            // ---------- unsubscribe ----------
            {"unsubscribe", "a@b.com", "Re: hello", "Please unsubscribe me.", null},
            {"unsubscribe", "a@b.com", "Re: hello", "Remove me from your list.", null},
            {"unsubscribe", "a@b.com", "Re: hello", "Stop emailing us.", null},
            {"unsubscribe", "a@b.com", "Re: hello", "Do not contact me again.", null},
            {"unsubscribe", "a@b.com", "Re: hello", "Please don't email me again", null},
            {"unsubscribe", "a@b.com", "Re: hello", "How do I opt-out of these messages?", null},
            {"unsubscribe", "a@b.com", "Re: hallo", "Bitte keine E-Mails mehr senden.", null},
            {"unsubscribe", "a@b.com", "Re: bonjour", "Veuillez me désinscrire de cette liste.", null},
            {"unsubscribe", "a@b.com", "Re: hola", "Por favor, no me envíen más correos.", null},
            {"unsubscribe", "a@b.com", "Re: ciao", "Non contattateci più.", null},
            // ---------- out of office ----------
            {"out_of_office", "a@b.com", "Automatic reply: hello", "I am out of the office until Monday with limited access to email.", null},
            {"out_of_office", "a@b.com", "Re: hello", "Thank you for your email. I'm currently on vacation and will return on 5 May.", null},
            {"out_of_office", "a@b.com", "Re: hallo", "Ich bin bis zum 12.5. im Urlaub und kehre danach zurück.", null},
            {"out_of_office", "a@b.com", "Re: bonjour", "Je suis absent du bureau jusqu'au 3 mars.", null},
            {"out_of_office", "a@b.com", "Re: hello", "Thanks, I'll get back to you.", "auto"},
            // ---------- bounce ----------
            {"bounce", "MAILER-DAEMON@mx.google.com", "Delivery Status Notification (Failure)", "Your message wasn't delivered to anna@b.com", null},
            {"bounce", "postmaster@b.com", "Undeliverable: hello", "Delivery has failed", null},
            {"bounce", "a@b.com", "Re: hello", "550 5.1.1 The email account that you tried to reach does not exist. User unknown", null},
            {"bounce", "noreply@b.com", "Mail notification", "Report", "bounce"},
            {"unsubscribe", "a@b.com", "Re: hello", "STOP", null},
            {"unsubscribe", "a@b.com", "Re: hello", "Please remove me from this.\nRegards", null},
            {"not_interested", "a@b.com", "Re: hello", "No, thank you.", null},
            {"not_interested", "a@b.com", "Re: hello", "Thanks, but we're all set.", null},
            {"not_interested", "a@b.com", "Re: hallo", "Nein, danke.", null},
            {"not_interested", "a@b.com", "Re: bonjour", "Je ne suis pas intéressé.", null},
            {"interested", "a@b.com", "Re: hello", "I'd like to know more. Could you share a case study?", null},
            {"interested", "a@b.com", "Re: hello", "This is relevant for us. Let's chat on Thursday.", null},
            {"question", "a@b.com", "Re: hello", "Where are you based and what languages do you support?", null},
            // ---------- needs review ----------
            {"unclear", "a@b.com", "Re: hello", "Not interested at the moment.", null},
            {"unclear", "a@b.com", "Re: hello", "We're not interested right now, check back next year.", null},
            {"unclear", "a@b.com", "Re: hello", "I've forwarded this to the right team.", null},
            {"unclear", "a@b.com", "Re: hello", "Thanks.", null},
            {"unclear", "a@b.com", "Re: hello", "Not right now, maybe next quarter.", null},
            {"unclear", "a@b.com", "Re: hello", "I'm not the right person. Please contact my colleague John.", null},
            {"unclear", "a@b.com", "Re: hello", "Let me forward this to my manager.", null},
            {"unclear", "a@b.com", "Re: hello", "I'm busy this week.", null},
            {"unclear", "a@b.com", "Re: hello", "Perhaps later in the year.", null},
            {"unclear", "a@b.com", "Re: hello", "We are not sure if we are interested.", null},
            {"unclear", "a@b.com", "Re: hello", "Interesting but not right now.", null},
            {"unclear", "a@b.com", "Re: hello", "", null},
    };

    @Test
    void labelsEveryKindOfReplyCorrectly() {
        List<String> wrong = new ArrayList<>();
        for (String[] c : CASES) {
            String got = read(c[1], c[2], c[3], c[4]).intent();
            if (!c[0].equals(got)) wrong.add("expected " + c[0] + " but got " + got + " for: " + c[3].replace("\n", " ⏎ "));
        }
        assertTrue(wrong.isEmpty(), "\n" + String.join("\n", wrong));
    }

    @Test
    void ourOwnQuotedEmailIsNeverReadAsTheirAnswer() {
        // our footer says "unsubscribe" and "not interested": a short reply on top must still win
        assertEquals("interested", read("a@b.com", "Re: hello", "Yes please, send details." + OUR_EMAIL_QUOTED, null).intent());
        // a reply that is only the quote still gets read instead of failing
        assertEquals("unsubscribe", read("a@b.com", "Re: hello", "> Please unsubscribe me", null).intent());
    }

    @Test
    void summaryIsTheNewTextOnly() {
        Analysis a = read("a@b.com", "Re: hello", "Hi Sam,\nSounds good, let's talk Thursday.\nThanks" + OUR_EMAIL_QUOTED, null);
        assertEquals("Sounds good, let's talk Thursday.", a.summary().substring(0, 33));
        assertTrue(!a.summary().contains("help clinics"));
        assertEquals("The email could not be delivered.", read("MAILER-DAEMON@x.com", "Undeliverable", "failed", null).summary());
    }

    @Test
    void neverSuggestsAnAnswer() {
        Analysis a = read("a@b.com", "Re: hello", "Yes, interested. Can we talk tomorrow?", null);
        assertEquals("", a.draft());
        assertEquals("rules", a.source());
    }
}
