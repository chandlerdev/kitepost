package com.kitepost.service.pipeline;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Phone numbers read from a company website. */
class HtmlParserPhoneTest {
    @Test
    void telLinkIsTheFirstChoice() {
        var p = HtmlParser.parse("<html><body><a href=\"tel:+49 30 1234567\">Call</a> <a href='tel:+4930123456?x=1'>x</a></body></html>", URI.create("https://a.com/"));
        assertEquals(List.of("+49 30 1234567", "+4930123456"), HtmlParser.extractPhones(p.tel(), List.of()));
    }

    @Test
    void numbersAfterALabelAreFoundInSeveralLanguages() {
        assertEquals(List.of("(555) 123-4567"), HtmlParser.extractPhones(List.of(), List.of("Contact us. Phone: (555) 123-4567 Mon-Fri")));
        assertEquals(List.of("+49 30 1234567"), HtmlParser.extractPhones(List.of(), List.of("Impressum Telefon +49 30 1234567")));
        assertEquals(List.of("+34 91 123 45 67"), HtmlParser.extractPhones(List.of(), List.of("Teléfono: +34 91 123 45 67")));
        assertEquals(List.of("01 23 45 67 89"), HtmlParser.extractPhones(List.of(), List.of("Tél. 01 23 45 67 89")));
    }

    @Test
    void theSameNumberIsListedOnceAndJunkIsIgnored() {
        assertEquals(List.of("+49 30 1234567"), HtmlParser.extractPhones(List.of("+49 30 1234567", "+49301234567"), List.of("Tel: +49 30 1234567")));
        assertEquals(List.of(), HtmlParser.extractPhones(List.of(), List.of("Founded in 2012. Order 20240115 shipped. Price 1234567 EUR")));   // no label, no tel link
        assertEquals(List.of(), HtmlParser.extractPhones(List.of("0000000", "12"), List.of()));
    }

    @Test
    void noMoreThanThree() {
        assertEquals(3, HtmlParser.extractPhones(List.of("+1 212 555 0101", "+1 212 555 0102", "+1 212 555 0103", "+1 212 555 0104"), List.of()).size());
    }
}
