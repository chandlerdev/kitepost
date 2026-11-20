package com.kitepost.service.mail;

import com.kitepost.entity.CampaignEntity;
import com.kitepost.entity.LeadEntity;
import com.kitepost.service.pipeline.Text;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Labels each reply from its text with keyword rules (English, German, French, Dutch, Spanish, Italian, Portuguese).
 * No AI, no cost, nothing leaves the server.
 *
 * <p>Order matters. The labels that change what happens next are decided only on clear wording:
 * <b>unsubscribe</b> and <b>not interested</b> put the address on the do-not-contact list. A reply that is
 * not clearly one of them (a polite "maybe later", "ask my colleague", "thanks") is labelled
 * <b>unclear</b> ("Needs review") so a person decides, instead of guessing.
 *
 * <p>Order: bounce, out of office, unsubscribe, not interested, unclear (soft no / referral),
 * interested, question, unclear (anything else). Only the new part of the reply is read: the quoted copy
 * of our own email below it is cut off first, so our own footer ("unsubscribe") cannot be mistaken for theirs.
 */
@Component
public class ReplyAnalyzer {
    public static final Map<String, String> INTENTS = new LinkedHashMap<>();
    static {
        INTENTS.put("interested", "Interested");
        INTENTS.put("question", "Has questions");
        INTENTS.put("not_interested", "Not interested");
        INTENTS.put("unsubscribe", "Unsubscribe");
        INTENTS.put("out_of_office", "Out of office");
        INTENTS.put("bounce", "Bounced");
        INTENTS.put("unclear", "Needs review");
    }

    private static final int CI = Pattern.CASE_INSENSITIVE;

    // ---- where the new part of the reply ends and the quoted original starts (checked on the raw text) ----
    private static final Pattern QUOTE_START = Pattern.compile(
            "(?im)^\\s*(on .{5,250}wrote:|am .{5,250}schrieb.*:|le .{5,250}a \\u00e9crit.*:|el .{5,250}escribi\\u00f3:|il .{5,250}ha scritto:|op .{5,250}schreef.*:|em .{5,250}escreveu:"
                    + "|-{2,}\\s*(original message|urspr\\u00fcngliche nachricht|message d'origine|mensaje original|messaggio originale|oorspronkelijk bericht|mensagem original)[^\\n]*"
                    + "|_{8,}|from:\\s.+@.+|von:\\s.+@.+|de:\\s.+@.+|van:\\s.+@.+|da:\\s.+@.+)\\s*$");
    private static final Pattern SIGNATURE = Pattern.compile("(?m)^--\\s*$");

    // ---- bounce ----
    private static final Pattern BOUNCE_FROM = Pattern.compile("mailer-daemon|mailerdaemon|postmaster|mail delivery (sub)?system", CI);
    private static final Pattern BOUNCE_SUBJECT = Pattern.compile(
            "undeliverable|undelivered|delivery status notification|delivery (has )?failed|delivery failure|returned mail|mail delivery failed|failure notice|message not delivered|not delivered|could not be delivered"
                    + "|unzustellbar|nicht zugestellt|zustellung fehlgeschlagen|non remis|echec de (la )?livraison|onbestelbaar|niet bezorgd|bezorging mislukt|no se pudo entregar|entrega fallida|rebotado|non consegnat|consegna non riuscita|nao foi entregue|falha na entrega", CI);
    private static final Pattern BOUNCE_BODY = Pattern.compile(
            "delivery to the following recipients? (has |have )?failed|wasn't delivered to|was not delivered to|couldn't be delivered|could not be delivered to|user unknown|unknown user|no such user|recipient address rejected|mailbox (unavailable|not found)|550[ -]5\\.\\d\\.\\d|\\b5\\.1\\.1\\b", CI);

    // ---- out of office (only trusted on short messages: a real reply that mentions leave is not an auto-reply) ----
    private static final Pattern OOO_SUBJECT = Pattern.compile(
            "^\\s*(re:\\s*)?(auto(matic)?[ -]?reply|autoreply|auto:|out of (the )?office|ooo\\b|abwesenheit|abwesend|automatische antwort|reponse automatique|absence|automatisch antwoord|afwezig|respuesta automatica|fuera de la oficina|ausencia|risposta automatica|fuori ufficio|resposta automatica)", CI);
    private static final Pattern OOO_BODY = Pattern.compile(
            "out of (the )?office|(i am|i'm) (currently |now )?(away|out of)|i (will|shall) be (away|out|on (leave|vacation|holiday))|on (annual |parental |maternity )?(leave|vacation|holiday)\\b|(will be |am )back (in the office |at work )?(on|from|after) |returning (on|to the office)|limited (access|availability)( to)?( my)? e-?mail|(will|shall) (reply|respond|get back to you) (when|upon|after) (i )?return"
                    + "|auto-?reply|automatic(ally)? (generated )?(reply|response)|abwesenheit|abwesend|im urlaub|nicht im buro|automatische antwort|hors du bureau|en conge|reponse automatique|absent(e)? (du bureau|jusqu)|afwezig|met vakantie|automatisch antwoord|fuera de la oficina|estoy ausente|de vacaciones|respuesta automatica|fuori ufficio|sono assente|in ferie|risposta automatica|fora do escritorio|estou ausente|de ferias|resposta automatica", CI);

    // ---- unsubscribe: clear requests to stop ----
    private static final Pattern UNSUB = Pattern.compile(
            "^\\s*(stop|remove|unsubscribe|cancel)\\s*[.!]?\\s*$|\\bunsubscribe\\b|\\bopt[- ]?out\\b|remove (me|my (e-?mail|address|name))\\b|remove (us|this (e-?mail|address)) from|take me off|(stop|quit) (e-?mailing|sending|contacting|messaging|writing)|do not (e-?mail|contact|send|message|write)( to)?( me| us)?\\b|don'?t (e-?mail|contact|send|message|write)( to)? (me|us)|no more (e-?mails?|messages)|delete (my )?(e-?mail|address|data|details)|stop (these|the|your) (e-?mails?|messages)"
                    + "|abmelden|austragen|abbestellen|keine (weiteren )?(e-?mails?|nachrichten|werbung|mails) mehr|nicht mehr (kontaktieren|anschreiben|schreiben)|aus (dem|ihrem|ihrer) (verteiler|liste)"
                    + "|desinscri|desabonn|ne plus (me |nous )?(contacter|envoyer|ecrire)|ne m'envoyez plus|supprimer (mon|mes) (adresse|donnees)|retirez-moi"
                    + "|afmelden|uitschrijven|geen (e-?mails?|berichten) meer|verwijder (mij|ons|mijn)|niet meer (mailen|contacteren)"
                    + "|dar(se|me)? de baja|no (me |nos )?(contacten|envien|escriban|manden)|borrar (mi|mis)|elimin(en|a) (mi|mis)"
                    + "|cancella (la )?(mia )?iscrizione|disiscri|non (inviate|inviarci|contattateci|contattarci|scrivetemi|mandate)|rimuov(i|ete) (il mio|me)"
                    + "|cancelar (a )?(minha )?(subscricao|inscricao)|remover (meu|me)\\b|nao (me )?(envie|envien|contacte|contate)|pare de (enviar|mandar)", CI);

    // "not interested right now" is a polite no for today, not a refusal: needs a person, nothing is suppressed
    private static final Pattern SOFT_NO = Pattern.compile(
            "(not interested|no interest|no need|(do not|don'?t) need)[^.!?\\n]{0,30}\\b(right now|at the moment|at this time|at present|currently|for now|this year|this quarter|yet)\\b", CI);

    // ---- not interested: a clear no ----
    private static final Pattern NOT_INTERESTED = Pattern.compile(
            "not interested|no interest|no longer (interested|need|require|looking)|isn'?t (a )?(good |right )?fit|not (a |the )?(good |right )?fit|not for us|not relevant|no,? (thanks|thank you)\\b|thanks,? but no\\b|(we|i) (do not|don'?t|dont) (need|require|want) (this|it|that|your|any|another|more)|no need\\b|(we|i)('ve| have) already (got|have|use|using)|we already (have|use|work)|(we're|we are|i'm|i am) all (set|covered)|happy with (our|the|my) (current|existing)|(we|i)('ll| will| would) pass"
                    + "|kein interesse|nicht interessiert|kein bedarf|brauchen (wir )?(nicht|kein)|nein,? danke|danke,? nein|haben (wir )?(bereits|schon)"
                    + "|pas interess|non merci|pas besoin|ne nous interesse pas|nous n'avons pas besoin"
                    + "|geen interesse|niet geinteresseerd|nee,? bedankt|geen behoefte|hebben (al|reeds)"
                    + "|no (me |nos )?interesa|no estamos interesad|no tenemos (interes|necesidad)|no gracias|no necesitamos|ya tenemos"
                    + "|non siamo interessat|non ci interessa|no,? grazie|non abbiamo bisogno|ne abbiamo gia"
                    + "|nao (temos|estamos|tenho) interess|nao,? obrigad|nao precisamos|ja temos", CI);

    // ---- soft no / needs a person: not a clear refusal, so nothing is suppressed automatically ----
    private static final Pattern UNSURE = Pattern.compile(
            "not (right )?now|not at this time|not currently|for now\\b|bad timing|maybe (later|next)|perhaps (later|next)|next (year|quarter|month)|in the future|at some point|keep (me|us) (posted|in mind|on file)|reach out (later|again)|circle back|follow up (later|in)|not (sure|certain)|unsure|\\bbusy\\b"
                    + "|wrong (person|contact|department|address)|not the (right|correct) (person|contact)|(forwarded|passed) (this|it|your (e-?mail|message)) (on |along |to )?|forward (this|it) (on |along |to )|(contact|speak to|talk to|reach out to|write to) my (colleague|manager|boss|partner|team)|\\bcc'?d\\b"
                    + "|nicht (jetzt|im moment)|vielleicht|spater|falscher ansprechpartner|pas pour le moment|peut-etre|plus tard|niet nu|nu niet|misschien|ahora no|quizas|mas adelante|per ora no|forse|piu avanti|agora nao|talvez|mais tarde", CI);

    // ---- interested: a clear yes ----
    private static final Pattern POSITIVE = Pattern.compile(
            "\\b(interested|interesting|sounds (good|great|interesting|promising)|looks (good|great|interesting)|tell me more|more (info|information|details)"
                    + "|(send|share|forward|email) (me|us)? ?(the |your |some |an? )?(pricing|prices?|quote|details|information|info|brochure|catalog(ue)?|proposal|overview|demo|deck|presentation|samples?|case stud(y|ies))"
                    + "|(book|schedule|set up|arrange|plan) (a |an |the )?(call|meeting|demo|time|chat|appointment)|let'?s (talk|chat|connect|schedule|discuss|meet|set)|(give|ring) me a (call|ring)|call me"
                    + "|(happy|glad|keen|love|like) to (talk|chat|meet|connect|learn|hear|discuss|see|try)|(would|'d) (like|love) to (learn|know|see|discuss|talk|meet|try|hear)|(please )?(contact|call|reach) (me|us)|go ahead|when (can|could|are|would) (we|you)|i'?m (open|available)|we'?re (open|available))\\b"
                    + "|^\\s*yes\\b"
                    + "|interessiert|interessant|(senden|schicken|schick) sie (mir|uns)|bitte senden|(rufen|ruf) sie (mich|uns) an|termin\\b|angebot|mehr informationen|klingt (gut|interessant)|haben interesse|ja,? bitte"
                    + "|interess(e|ee|es|ant|ante)\\b|envoyez[- ]moi|appelez[- ]moi|rappelez|rendez-vous|devis|plus d'informations|plus de details|ca m'interesse|nous sommes interesses|oui,? (volontiers|merci)"
                    + "|geinteresseerd|stuur (mij|ons|me)|bel (mij|me|ons)|afspraak|offerte|meer informatie|klinkt (goed|interessant)|ja,? (graag|bedankt)"
                    + "|interesad[oa]s?|interesante|me interesa|nos interesa|envi(a|e|en)(me|nos)|llam(e|en)(me|nos)|llamame|reunion|cotizacion|presupuesto|mas informacion|suena (bien|interesante)|si,? por favor|(tenemos|tengo) interes|(nos|me) gustaria"
                    + "|interessat[oi]|ci interessa|mi interessa|inviat(e|emi)|mandatemi|chiam(i|ate|atemi)|appuntamento|preventivo|maggiori informazioni|suona bene|si,? grazie"
                    + "|interessad[oa]s?|temos interesse|tenho interesse|envie(-me)?|ligue(-me)?|reuniao|orcamento|mais informacoes|soa bem|sim,? por favor", CI);

    private static final Pattern QUESTION = Pattern.compile("[?\\u00bf]|^\\s*(how|what|which|who|when|where|why|can|could|do|does|did|is|are|will|would|may)\\b(?!')", CI);
    private static final Pattern GREETING = Pattern.compile("^(hi|hello|hey|dear|guten|bonjour|beste|hola|hallo|ciao|ola|good (morning|afternoon|evening))\\b", CI);

    /**
     * @param kind what the mail's own headers say: "bounce" (a delivery report), "auto" (an automatic reply), or null.
     *             Bounces and auto-replies are the one thing wording alone can miss, so the headers are trusted first.
     */
    public record Incoming(String from, String fromName, String subject, String text, String messageId, String date, String translation, boolean simulated, String kind) {
        public Incoming(String from, String fromName, String subject, String text, String messageId, String date, String translation, boolean simulated) {
            this(from, fromName, subject, text, messageId, date, translation, simulated, null);
        }
    }

    public record Analysis(String intent, String summary, String translation, String draft, String source) {}

    /** Labels a reply and writes a one-line summary. There is no suggested answer. */
    public Analysis analyse(Incoming m, LeadEntity lead, CampaignEntity c) {
        String raw = Text.blank(m.text()) ? "" : m.text().replace("\r\n", "\n").replace('\r', '\n');
        String fresh = newPart(raw);
        String from = fold(m.from()), subject = fold(m.subject());
        String all = fold(raw), body = fold(fresh);

        String intent;
        String summary = null;
        if ("bounce".equals(m.kind()) || BOUNCE_FROM.matcher(from).find() || BOUNCE_SUBJECT.matcher(subject).find() || BOUNCE_BODY.matcher(all).find()) {
            intent = "bounce";
            summary = "The email could not be delivered.";
        } else if ("auto".equals(m.kind()) || OOO_SUBJECT.matcher(subject).find() || (body.length() < 1500 && OOO_BODY.matcher(body).find())) {
            intent = "out_of_office";
            summary = "Automatic out-of-office reply.";
        } else if (UNSUB.matcher(body).find()) {
            intent = "unsubscribe";
        } else if (SOFT_NO.matcher(body).find()) {
            intent = "unclear";
        } else if (NOT_INTERESTED.matcher(body).find()) {
            intent = "not_interested";
        } else if (UNSURE.matcher(body).find()) {
            intent = "unclear";
        } else if (POSITIVE.matcher(body).find()) {
            intent = "interested";
        } else if (QUESTION.matcher(body).find()) {
            intent = "question";
        } else {
            intent = "unclear";
        }
        if (summary == null) summary = firstLines(fresh.isBlank() ? raw : fresh);
        // No suggested answer: whoever handles the reply writes their own.
        return new Analysis(intent, Text.cut(summary, 220), m.translation(), "", "rules");
    }

    /** The part the person wrote: the quoted copy of our email, forwarded headers and the signature are cut off. */
    static String newPart(String raw) {
        String t = raw;
        var q = QUOTE_START.matcher(t);
        if (q.find() && q.start() > 0) t = t.substring(0, q.start());
        var s = SIGNATURE.matcher(t);
        if (s.find() && s.start() > 0) t = t.substring(0, s.start());
        t = Arrays.stream(t.split("\n")).filter(l -> !l.trim().startsWith(">")).reduce((a, b) -> a + "\n" + b).orElse("");
        t = t.trim();
        return t.isEmpty() ? raw.trim() : t;      // a reply that is only a quote still gets read
    }

    /** Lower case, without accents, with plain apostrophes and single spaces, so one pattern covers all spellings. */
    static String fold(String s) {
        if (s == null) return "";
        String t = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        t = t.replace('’', '\'').replace('‘', '\'').replace('´', '\'').replace('`', '\'');
        return t.toLowerCase(java.util.Locale.ROOT).replaceAll("[ \\t]+", " ");
    }

    private static String firstLines(String text) {
        return Arrays.stream(text.split("\n")).map(String::trim)
                .filter(l -> !l.isBlank() && !GREETING.matcher(l).find()).limit(2)
                .reduce((a, b) -> a + " " + b).orElse("");
    }
}
