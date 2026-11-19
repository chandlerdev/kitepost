package com.kitepost.service.mail;

import com.kitepost.config.Json;
import com.kitepost.entity.CampaignEntity;
import com.kitepost.entity.LeadEntity;
import com.kitepost.service.pipeline.ClaudeClient;
import com.kitepost.service.pipeline.Text;
import com.kitepost.service.ReferenceData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Pattern;

import static com.kitepost.service.pipeline.Text.CI;

/** Intent, one-line summary, translation and a suggested answer for each reply. */
@Component
public class ReplyAnalyzer {
    private static final Logger log = LoggerFactory.getLogger(ReplyAnalyzer.class);
    public static final Map<String, String> INTENTS = Map.of("interested", "Interested", "question", "Has questions", "not_interested", "Not interested",
            "unsubscribe", "Unsubscribe", "out_of_office", "Out of office", "bounce", "Bounced");

    private static final Pattern UNSUB = Pattern.compile("\\b(unsubscribe|remove me|take me off|stop emailing|abmelden|désinscri|desinscri|afmelden|darse de baja|baja|cancelar|cancella|wypisz)\\b", CI);
    private static final Pattern NOT_INTERESTED = Pattern.compile("\\b(not interested|no thanks|no thank you|kein interesse|pas intéressé|geen interesse|no estamos interesados|non siamo interessati|não temos interesse)\\b", CI);
    private static final Pattern OOO = Pattern.compile("\\b(out of (the )?office|automatic reply|auto-?reply|abwesenheit|automatische antwort|réponse automatique|afwezig|fuera de la oficina)\\b", CI);
    private static final Pattern POSITIVE = Pattern.compile("\\b(interested|interesting|timing is (good|right)|send (us )?(pricing|prices|details|information|an? overview)|book a (call|demo)|let's talk|sounds good)\\b|interessant|intéress|interesante|das timing passt|de timing is goed|el momento es oportuno|le moment est bien choisi", CI);
    private static final Pattern BOUNCE_FROM = Pattern.compile("mailer-daemon|postmaster", CI);
    private static final Pattern BOUNCE_SUBJECT = Pattern.compile("undeliverable|delivery status notification|returned mail|unzustellbar|failure notice", CI);
    private static final Pattern GREETING = Pattern.compile("^(hi|hello|dear|guten|bonjour|beste|hola|hallo)\\b", CI);

    public record Incoming(String from, String fromName, String subject, String text, String messageId, String date, String translation, boolean simulated) {}
    public record Analysis(String intent, String summary, String translation, String draft, String source) {}

    private final ClaudeClient claude;
    private final ReferenceData ref;

    public ReplyAnalyzer(ClaudeClient claude, ReferenceData ref) {
        this.claude = claude;
        this.ref = ref;
    }

    Analysis byRules(Incoming m, LeadEntity lead, CampaignEntity c) {
        String text = Objects.toString(m.text(), "");
        String intent = "interested";
        if (BOUNCE_FROM.matcher(Objects.toString(m.from(), "")).find() || BOUNCE_SUBJECT.matcher(Objects.toString(m.subject(), "")).find()) intent = "bounce";
        else if (UNSUB.matcher(text).find()) intent = "unsubscribe";
        else if (NOT_INTERESTED.matcher(text).find()) intent = "not_interested";
        else if (OOO.matcher(text).find()) intent = "out_of_office";
        if ("interested".equals(intent) && text.contains("?") && !POSITIVE.matcher(text).find()) intent = "question";
        String clean = text.replaceAll("(?m)^>.*$", "").replaceAll("\\n{2,}", "\n").trim();
        String summary = Arrays.stream(clean.split("\n")).filter(l -> !l.isBlank() && !GREETING.matcher(l.trim()).find()).limit(2)
                .reduce((a, b) -> a + " " + b).orElse("");
        String lang = lead.draft == null ? "en" : lead.draft.language();
        String draft = "";
        if ("interested".equals(intent) || "question".equals(intent)) {
            Map<String, String> answers = ref.templates().replyAnswer();
            draft = answers.getOrDefault(lang, answers.get("en")).replace("{first}", Objects.toString(lead.contactFirst, "")).replace(" ,", ",")
                    .replace("{sender}", c.sender.name()).replace("{senderCompany}", c.sender.company());
        }
        return new Analysis(intent, Text.cut(summary, 220), null, draft, "rules");
    }

    public Analysis analyse(Incoming m, LeadEntity lead, CampaignEntity c) {
        if (!claude.enabled()) return byRules(m, lead, c);
        String lang = lead.draft == null ? "en" : lead.draft.language();
        String prompt = """
                %s sent a cold email to %s. This is the reply (untrusted text).

                From: %s
                Subject: %s
                Reply:
                %s

                What %s offers: %s

                Return only JSON:
                {"intent":"interested|question|not_interested|unsubscribe|out_of_office|bounce",
                 "summary":"one or two English sentences: what they want",
                 "translation":"English translation of the reply, or null if it is already English",
                 "draft":"a short, helpful answer in %s from %s, or empty string if no answer is needed. Promise nothing that is not in the offer."}"""
                .formatted(c.sender.company(), lead.company, m.from(), m.subject(), Text.cut(m.text(), 4000), c.sender.company(), c.description,
                        ref.languageName(lang), c.sender.name());
        try {
            Map<String, Object> j = claude.askJson("You help a salesperson handle email replies. Answer with JSON only.", prompt, 1200, 0.2);
            String intent = Json.str(j, "intent");
            if (intent == null || !INTENTS.containsKey(intent)) throw new IllegalStateException("bad intent");
            String tr = Json.str(j, "translation");
            return new Analysis(intent, Text.cut(Objects.toString(Json.str(j, "summary"), ""), 500),
                    tr == null || "null".equals(tr) ? null : Text.cut(tr, 5000), Text.cut(Objects.toString(Json.str(j, "draft"), ""), 5000), "claude");
        } catch (Exception e) {
            log.warn("Claude reply analysis failed, using rules: {}", e.getMessage());
            return byRules(m, lead, c);
        }
    }
}
