package com.kitepost.service;

import com.kitepost.controller.LeadController;
import com.kitepost.entity.CampaignEntity;
import com.kitepost.entity.LeadEntity;
import com.kitepost.exception.ApiException;
import com.kitepost.repository.CampaignRepository;
import com.kitepost.repository.LeadRepository;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.ResponseEntity;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** The Review page's Export button: customer leads of one campaign or all campaigns, as an Excel sheet. */
class LeadExportTest {
    private LeadRepository leads;
    private CampaignRepository campaigns;
    private LeadService service;

    private static CampaignEntity campaign(String id, String name) { CampaignEntity c = new CampaignEntity(); c.id = id; c.name = name; return c; }

    private static LeadEntity lead(String campaignId, String company, String email, String phone, String status) {
        LeadEntity l = new LeadEntity();
        l.campaignId = campaignId; l.company = company; l.email = email; l.phone = phone; l.status = status; l.url = "https://" + company.toLowerCase().replace(' ', '-') + ".example/";
        return l;
    }

    @BeforeEach
    void setUp() {
        leads = mock(LeadRepository.class);
        campaigns = mock(CampaignRepository.class);
        service = new LeadService(leads, campaigns, null, null, null, null, null, null, null, null);
        when(campaigns.findAll()).thenReturn(List.of(campaign("c1", "Dental US"), campaign("c2", "Dental Germany")));
    }

    private static String sheet(byte[] xlsx) throws Exception {
        try (ZipInputStream z = new ZipInputStream(new ByteArrayInputStream(xlsx))) {
            for (var e = z.getNextEntry(); e != null; e = z.getNextEntry()) if (e.getName().equals("xl/worksheets/sheet1.xml")) return new String(z.readAllBytes(), StandardCharsets.UTF_8);
        }
        return fail("no sheet in the file");
    }

    @SuppressWarnings("unchecked")
    @Test
    void oneCampaignExportsItsCustomersWithCompanyEmailAndPhone() throws Exception {
        when(leads.findAll(any(Specification.class), any(Sort.class))).thenReturn(List.of(
                lead("c1", "Smile Clinic", "info@smile.example", "+1 512 555 0142", "review"),
                lead("c1", "No Mail Dental", null, "+1 512 555 0199", "no_email")));
        LeadService.Export x = service.exportLeads("c1");
        assertEquals("kitepost-leads-dental-us-" + java.time.LocalDate.now() + ".xlsx", x.filename());
        String s = sheet(x.bytes());
        assertTrue(s.contains(">Company<") && s.contains(">Email<") && s.contains(">Contact number<"));
        assertTrue(s.contains(">Smile Clinic<") && s.contains(">info@smile.example<") && s.contains(">+1 512 555 0142<"));
        assertTrue(s.contains(">No Mail Dental<") && s.contains(">+1 512 555 0199<") && s.contains(">No email<"), "a lead without an email still lists its number");
        assertTrue(s.contains(">Dental US<"));

        // only customers of that campaign are asked for
        ArgumentCaptor<Specification<LeadEntity>> spec = ArgumentCaptor.forClass(Specification.class);
        verify(leads).findAll(spec.capture(), any(Sort.class));
        Root<LeadEntity> root = mock(Root.class);
        CriteriaBuilder cb = mock(CriteriaBuilder.class);
        Path<Object> category = mock(Path.class), campaignId = mock(Path.class);
        when(root.get("category")).thenReturn(category);
        when(root.get("campaignId")).thenReturn(campaignId);
        when(cb.equal(any(Path.class), any(Object.class))).thenReturn(mock(Predicate.class));
        spec.getValue().toPredicate(root, mock(CriteriaQuery.class), cb);
        verify(cb).equal(category, "customer");
        verify(cb).equal(campaignId, "c1");
    }

    @SuppressWarnings("unchecked")
    @Test
    void allCampaignsExportsEveryCampaignWithItsName() throws Exception {
        when(leads.findAll(any(Specification.class), any(Sort.class))).thenReturn(List.of(
                lead("c1", "Smile Clinic", "info@smile.example", "+1 512 555 0142", "sent"),
                lead("c2", "Zahnarzt Berlin", "kontakt@zahn.example", "+49 30 1234567", "review")));
        LeadService.Export x = service.exportLeads("all");
        assertEquals("kitepost-leads-all-campaigns-" + java.time.LocalDate.now() + ".xlsx", x.filename());
        String s = sheet(x.bytes());
        assertTrue(s.contains(">Dental US<") && s.contains(">Dental Germany<") && s.contains(">+49 30 1234567<") && s.contains(">Sent<"));

        ArgumentCaptor<Specification<LeadEntity>> spec = ArgumentCaptor.forClass(Specification.class);
        verify(leads).findAll(spec.capture(), any(Sort.class));
        Root<LeadEntity> root = mock(Root.class);
        CriteriaBuilder cb = mock(CriteriaBuilder.class);
        when(root.get(any(String.class))).thenReturn(mock(Path.class));
        when(cb.equal(any(Path.class), any(Object.class))).thenReturn(mock(Predicate.class));
        spec.getValue().toPredicate(root, mock(CriteriaQuery.class), cb);
        verify(cb, times(1)).equal(any(Path.class), any(Object.class));   // customers only, no campaign filter
        verify(cb).equal(any(Path.class), eq("customer"));
        assertEquals(service.exportLeads(null).filename(), x.filename(), "no campaign means all campaigns");
    }

    @Test
    void anUnknownCampaignIsRefused() {
        assertThrows(ApiException.class, () -> service.exportLeads("nope"));
    }

    @SuppressWarnings("unchecked")
    @Test
    void theEndpointSendsAnExcelDownload() {
        when(leads.findAll(any(Specification.class), any(Sort.class))).thenReturn(List.of());
        ResponseEntity<byte[]> r = new LeadController(service).export("c2");
        assertEquals(200, r.getStatusCode().value());
        assertEquals("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", String.valueOf(r.getHeaders().getContentType()));
        assertTrue(r.getHeaders().getFirst("Content-Disposition").startsWith("attachment; filename=\"kitepost-leads-dental-germany-"));
        assertEquals("PK", new String(r.getBody(), 0, 2, StandardCharsets.ISO_8859_1), "an .xlsx is a zip file");
    }
}
