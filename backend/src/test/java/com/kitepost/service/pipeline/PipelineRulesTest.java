package com.kitepost.service.pipeline;

import com.kitepost.entity.Models.Profile;
import com.kitepost.entity.Models.Signals;
import com.kitepost.entity.Models.Strategy;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.URI;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PipelineRulesTest {
    private static final Strategy LABELS = new Strategy("LX-200, a print & apply labeller", "print & apply labeller", "rules",
            List.of(new Profile("p0", "Distributors", "", List.of("print & apply labeller distributors", "packaging machinery distributors"), true)),
            Map.of(), new Signals(List.of("labeller"), List.of(), List.of()));

    @Test
    void findsTheProductInTheDescription() {
        assertEquals("print & apply labeller", StrategyBuilder.coreTerm(StrategyBuilder.offerFrom("We make the LX-200, a print & apply labeller for packaging.", "x")));
        assertEquals("scheduling software", StrategyBuilder.coreTerm(StrategyBuilder.offerFrom("We build scheduling software for dental clinics.", "x")));
        assertEquals("online scheduling and reminder", StrategyBuilder.coreTerm(StrategyBuilder.offerFrom("We build ChairTime, online scheduling and reminder software for dental clinics.", "x")));
    }

    @Test
    void buildsProfilesFromTheAudience() {
        Strategy s = new StrategyBuilder(null, null).byRules(new StrategyBuilder.Input("x",
                "We make the LX-200, a print & apply labeller for end-of-line packaging. We are looking for distributors and dealers who already sell packaging machinery to food producers.",
                "Sales", List.of("US")));
        assertEquals(List.of("Distributors", "Dealers"), s.profiles().stream().map(Profile::name).toList());
        assertTrue(s.profiles().get(0).keywords().contains("packaging machinery distributors"));
    }

    @Test
    void classifiesByRules() {
        assertEquals("customer", Classifier.byRules("Acme Distribution", "", "We are a packaging machinery distributor. Our distributors network serves food producers.", LABELS).category());
        assertEquals("competitor", Classifier.byRules("Labelco", "", "We manufacture print & apply labeller systems in our own factory.", LABELS).category());
        assertEquals("vendor", Classifier.byRules("Parts Inc", "", "We supply manufacturers with components and spare parts for machines.", LABELS).category());
        assertEquals("irrelevant", Classifier.byRules("Top 50 packaging distributors | Business directory", "", "listings", LABELS).category());
    }

    @Test
    void extractsTheBestEmails() {
        assertEquals(List.of("sales@acme.com", "info@acme.com", "jane@gmail.com"),
                HtmlParser.extractEmails(List.of("jane@gmail.com logo@2x.png info@acme.com noreply@acme.com sales@acme.com"), "acme.com"));
    }

    @Test
    void parsesHtml() {
        HtmlParser.Parsed p = HtmlParser.parse("<html lang=\"de\"><head><title>Müller &amp; Co | Händler</title><script>x=1</script></head>"
                + "<body><a href=\"/kontakt\">Kontakt</a> <a href=\"mailto:vertrieb@mueller.de\">mail</a></body></html>", URI.create("https://mueller.de/"));
        assertEquals("Müller & Co | Händler", p.title());
        assertEquals("de", p.lang());
        assertFalse(p.text().contains("x=1"));
        assertEquals("https://mueller.de/kontakt", p.links().get(0).href());
        assertEquals(List.of("vertrieb@mueller.de"), p.mailto());
    }

    @Test
    void blocksInternalAddresses() throws Exception {
        for (String ip : List.of("127.0.0.1", "10.1.2.3", "172.16.0.5", "192.168.1.1", "169.254.169.254", "100.64.0.1", "0.0.0.0", "::1", "fd00::1"))
            assertTrue(SsrfGuard.isInternal(InetAddress.getByName(ip)), ip);
        assertFalse(SsrfGuard.isInternal(InetAddress.getByName("93.184.216.34")));
        assertThrows(SsrfGuard.BlockedException.class, () -> SsrfGuard.check(URI.create("http://localhost/")));
        assertThrows(SsrfGuard.BlockedException.class, () -> SsrfGuard.check(URI.create("http://127.0.0.1/")));
        assertThrows(SsrfGuard.BlockedException.class, () -> SsrfGuard.check(URI.create("ftp://example.org/")));
        assertThrows(SsrfGuard.BlockedException.class, () -> SsrfGuard.check(URI.create("http://example.org:8080/")));
    }

    @Test
    void makesDomainSlugs() {
        assertEquals("mueller-and-co", MockCompanies.slug("Müller & Co"));
    }
}
