package com.kitepost.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Writes a one-sheet Excel file (.xlsx) with no extra library: a bold header row, text cells and sensible column widths.
 * Every value is stored as text, so a phone number keeps its "+" and leading zeros, and nothing is ever read as a formula.
 */
public final class XlsxWriter {
    private XlsxWriter() {}

    public static byte[] write(String sheetName, List<String> header, List<List<String>> rows, int[] widths) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (ZipOutputStream zip = new ZipOutputStream(bytes, StandardCharsets.UTF_8)) {
                put(zip, "[Content_Types].xml", """
                        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                        <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">\
                        <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>\
                        <Default Extension="xml" ContentType="application/xml"/>\
                        <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>\
                        <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>\
                        <Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>\
                        </Types>""");
                put(zip, "_rels/.rels", """
                        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                        <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">\
                        <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>\
                        </Relationships>""");
                put(zip, "xl/workbook.xml", """
                        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                        <workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">\
                        <sheets><sheet name="%s" sheetId="1" r:id="rId1"/></sheets></workbook>""".formatted(esc(sheetName)));
                put(zip, "xl/_rels/workbook.xml.rels", """
                        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                        <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">\
                        <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>\
                        <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>\
                        </Relationships>""");
                put(zip, "xl/styles.xml", """
                        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                        <styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">\
                        <fonts count="2"><font><sz val="11"/><name val="Calibri"/></font><font><b/><sz val="11"/><name val="Calibri"/></font></fonts>\
                        <fills count="2"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill></fills>\
                        <borders count="1"><border><left/><right/><top/><bottom/><diagonal/></border></borders>\
                        <cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>\
                        <cellXfs count="2"><xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/><xf numFmtId="0" fontId="1" fillId="0" borderId="0" xfId="0" applyFont="1"/></cellXfs>\
                        </styleSheet>""");

                StringBuilder s = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                        + "<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">"
                        + "<sheetViews><sheetView workbookViewId=\"0\"><pane ySplit=\"1\" topLeftCell=\"A2\" activePane=\"bottomLeft\" state=\"frozen\"/></sheetView></sheetViews><cols>");
                for (int i = 0; i < widths.length; i++) s.append("<col min=\"").append(i + 1).append("\" max=\"").append(i + 1).append("\" width=\"").append(widths[i]).append("\" customWidth=\"1\"/>");
                s.append("</cols><sheetData>");
                row(s, 1, header, 1);
                for (int r = 0; r < rows.size(); r++) row(s, r + 2, rows.get(r), 0);
                s.append("</sheetData></worksheet>");
                put(zip, "xl/worksheets/sheet1.xml", s.toString());
            }
            return bytes.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void row(StringBuilder s, int n, List<String> cells, int style) {
        s.append("<row r=\"").append(n).append("\">");
        for (int c = 0; c < cells.size(); c++) {
            String v = cells.get(c);
            if (v == null || v.isEmpty()) continue;
            s.append("<c r=\"").append(column(c)).append(n).append("\" t=\"inlineStr\"").append(style == 1 ? " s=\"1\"" : "")
                    .append("><is><t xml:space=\"preserve\">").append(esc(v)).append("</t></is></c>");
        }
        s.append("</row>");
    }

    private static String column(int i) {
        StringBuilder b = new StringBuilder();
        for (int n = i; n >= 0; n = n / 26 - 1) b.insert(0, (char) ('A' + n % 26));
        return b.toString();
    }

    /** XML-safe text: escapes the markup characters and drops characters XML cannot hold. */
    static String esc(String v) {
        StringBuilder b = new StringBuilder(v.length() + 8);
        v.codePoints().forEach(cp -> {
            if (cp == '&') b.append("&amp;");
            else if (cp == '<') b.append("&lt;");
            else if (cp == '>') b.append("&gt;");
            else if (cp == '"') b.append("&quot;");
            else if (cp == '\t' || cp == '\n' || cp == '\r' || (cp >= 0x20 && cp <= 0xD7FF) || (cp >= 0xE000 && cp <= 0xFFFD) || cp >= 0x10000) b.appendCodePoint(cp);
        });
        return b.toString();
    }

    private static void put(ZipOutputStream zip, String name, String content) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }
}
