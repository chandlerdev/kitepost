package com.kitepost.service.pipeline;

import com.kitepost.entity.CampaignEntity;
import com.kitepost.entity.LeadEntity;
import com.kitepost.entity.Models.Draft;
import com.kitepost.entity.Models.Sender;
import com.kitepost.entity.TemplateEntity;
import com.kitepost.service.ReferenceData;
import org.springframework.stereotype.Component;

import java.util.Objects;

/** Fills a campaign template with a lead's details (no AI), and builds the unsubscribe footer. Plain text only. */
@Component
public class Drafter {
    private final ReferenceData ref;

    public Drafter(ReferenceData ref) { this.ref = ref; }

    public static String language(CampaignEntity c, LeadEntity l) {
        return "english".equals(c.emailLanguage) ? "en" : (Text.blank(l.lang) ? "en" : l.lang);
    }

    private static String fill(String t, CampaignEntity c, LeadEntity l) {
        Sender s = c.sender;
        return t.replace("{company}", Objects.toString(l.company, "")).replace("{domain}", Objects.toString(l.domain, ""))
                .replace("{sender}", s.name()).replace("{senderCompany}", s.company())
                .replace("{role}", Text.blank(s.role()) ? "" : s.role()).replace("{pitch}", Objects.toString(c.pitch, ""));
    }

    /** Greeting + body of the chosen template, with the lead's and the sender's details filled in. */
    public Draft fromTemplate(TemplateEntity t, CampaignEntity c, LeadEntity l) {
        String body = fill(t.greeting, c, l) + "\n\n" + fill(t.body, c, l);
        return new Draft(fill(t.subject, c, l), body, t.language, "template");
    }

    public String footer(CampaignEntity c, LeadEntity l) {
        String lang = l.draft == null ? "en" : l.draft.language();
        String line = ref.templates().footer().getOrDefault(lang, ref.templates().footer().get("en")).replace("{company}", l.company);
        Sender s = c.sender;
        return "\n\n--\n" + line + (Text.blank(s.address()) ? "" : "\n" + s.company() + ", " + s.address());
    }
}
