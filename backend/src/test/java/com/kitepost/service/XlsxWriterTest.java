package com.kitepost.service;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.*;

/** The export is a real .xlsx: a zip with the sheet parts, text kept exactly as written. */
class XlsxWriterTest {
    private static Map<String, String> unzip(byte[] bytes) throws Exception {
        Map<String, String> out = new LinkedHashMap<>();
        try (ZipInputStream z = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            for (ZipEntry e; (e = z.getNextEntry()) != null; ) out.put(e.getName(), new String(z.readAllBytes(), StandardCharsets.UTF_8));
        }
        return out;
    }

    @Test
    void writesTheSheetPartsWithHeaderAndRows() throws Exception {
        byte[] x = XlsxWriter.write("Leads", List.of("Company", "Email", "Contact number"),
                List.of(Arrays.asList("Acme & Sons <Dental>", "info@acme.com", "+49 30 1234567"), Arrays.asList("No Mail Ltd", "", "0044 20 7946 0000")), new int[]{30, 30, 20});
        Map<String, String> parts = unzip(x);
        assertTrue(parts.keySet().containsAll(List.of("[Content_Types].xml", "_rels/.rels", "xl/workbook.xml", "xl/_rels/workbook.xml.rels", "xl/styles.xml", "xl/worksheets/sheet1.xml")));
        String sheet = parts.get("xl/worksheets/sheet1.xml");
        assertTrue(sheet.contains(">Company<") && sheet.contains(">Contact number<"));
        assertTrue(sheet.contains("Acme &amp; Sons &lt;Dental&gt;"), "markup characters are escaped");
        assertTrue(sheet.contains(">+49 30 1234567<") && sheet.contains(">0044 20 7946 0000<"), "phone numbers stay exactly as written");
        assertTrue(sheet.contains("r=\"A3\"") && !sheet.contains("r=\"B3\""), "an empty cell is left out, the next one keeps its column");
        assertTrue(sheet.contains("r=\"C3\""));
    }

    @Test
    void textThatLooksLikeAFormulaIsStoredAsText() throws Exception {
        String sheet = unzip(XlsxWriter.write("Leads", List.of("A"), List.of(List.of("=HYPERLINK(\"http://x\")")), new int[]{20})).get("xl/worksheets/sheet1.xml");
        assertTrue(sheet.contains("t=\"inlineStr\"") && !sheet.contains("<f>"));
    }

    @Test
    void columnLettersContinuePastZ() {
        List<String> wide = new ArrayList<>();
        for (int i = 0; i < 28; i++) wide.add("v" + i);
        assertDoesNotThrow(() -> assertTrue(unzip(XlsxWriter.write("S", wide, List.of(), new int[0])).get("xl/worksheets/sheet1.xml").contains("r=\"AB1\"")));
    }

    @Test
    void controlCharactersAreDropped() {
        assertEquals("ab", XlsxWriter.esc("a\u0000b"));
    }
}
