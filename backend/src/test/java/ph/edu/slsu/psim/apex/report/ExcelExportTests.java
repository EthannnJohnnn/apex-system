package ph.edu.slsu.psim.apex.report;

import java.io.ByteArrayInputStream;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import ph.edu.slsu.psim.apex.activity.ActivityService;
import ph.edu.slsu.psim.apex.member.MemberService;
import ph.edu.slsu.psim.apex.organization.OrganizationService;
import ph.edu.slsu.psim.apex.points.PointService;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:excel_export_test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa", "spring.datasource.password="
})
@AutoConfigureMockMvc
@Transactional
class ExcelExportTests {
    @Autowired ExcelExportService exports;
    @Autowired MemberService members;
    @Autowired OrganizationService terms;
    @Autowired PointService points;
    @Autowired ActivityService activities;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    UUID term, member;
    OffsetDateTime at;

    @BeforeEach void setup() {
        at = OffsetDateTime.now(ZoneOffset.ofHours(8)).minusHours(1).withNano(0);
        var t = terms.create(new OrganizationService.Details("Practice term", at.toLocalDate().minusDays(10), at.toLocalDate().plusDays(10), null, null), "president");
        term = t.id(); terms.transition(term, new OrganizationService.Change(0L), "president", true);
        member = members.create(new MemberService.Details("00001", "Alex Rivera", "Member", MemberService.Category.MEMBER, null, "=1+1", null, null), "president").id();
        jdbc.update("UPDATE member_eligibility_history SET effective_at=? WHERE member_id=?", at.minusDays(1), member);
    }

    @Test void memberTotalsRespectCorrectionsAndPreserveFullLedger() throws Exception {
        var original = points.award(new PointService.Command(UUID.randomUUID(), term, member, 10, "Volunteer work"), "president").getFirst();
        points.correct(original.id(), new PointService.Correction(UUID.randomUUID(), 6, "Corrected award"), "president", false);
        points.award(new PointService.Command(UUID.randomUUID(), term, member, -2, "Late submission"), "president");
        members.status(member, new MemberService.Status(false, 0L));
        byte[] bytes = exports.members(term);
        try (var book = open(bytes)) {
            assertEquals(2, book.getNumberOfSheets());
            var row = book.getSheet("Members").getRow(5);
            assertEquals("00001", row.getCell(0).getStringCellValue());
            assertEquals("@", row.getCell(0).getCellStyle().getDataFormatString());
            assertEquals("Inactive", row.getCell(4).getStringCellValue());
            assertEquals(6, row.getCell(6).getNumericCellValue());
            assertEquals(2, row.getCell(7).getNumericCellValue());
            assertEquals(4, row.getCell(8).getNumericCellValue());
            assertEquals(CellType.STRING, row.getCell(9).getCellType());
            assertEquals("=1+1", row.getCell(9).getStringCellValue());
            var history = book.getSheet("Point history");
            assertEquals(8, history.getLastRowNum());
            double total = 0;
            for (int i = 5; i <= 8; i++) total += history.getRow(i).getCell(3).getNumericCellValue();
            assertEquals(4, total);
            assertTrue(DateUtil.isCellDateFormatted(history.getRow(5).getCell(2)));
            assertEquals(CellType.BLANK, history.getRow(5).getCell(6).getCellType());
            assertTrue(book.getSheet("Members").getPaneInformation().isFreezePane());
            assertTrue(book.getSheet("Members").getCTWorksheet().isSetAutoFilter());
        }
        saveSample("members", bytes);
    }

    @Test void eventsIncludeFinalizedDraftCancelledAndUnmarkedWithoutMutatingData() throws Exception {
        var v = create("Community outreach", ActivityService.Kind.EVENT);
        v = activities.saveDraft(v.activity().id(), new ActivityService.Draft(UUID.randomUUID(), v.activity().version(), List.of(new ActivityService.Mark(member, ActivityService.Status.PRESENT))), "president");
        var done = activities.finalizeAttendance(v.activity().id(), new ActivityService.Change(UUID.randomUUID(), v.activity().version(), ""), "president");
        create("Planning meeting", ActivityService.Kind.MEETING);
        var cancelled = create("Cancelled meeting", ActivityService.Kind.MEETING);
        activities.cancel(cancelled.activity().id(), new ActivityService.Change(UUID.randomUUID(), 0L, "Rescheduled"), "president");
        long before = points.ledger(term).entries().size();
        byte[] bytes = exports.events(term);
        try (var book = open(bytes)) {
            var event = find(book.getSheet("Events"), "Community outreach");
            assertEquals("Finalized", event.getCell(4).getStringCellValue());
            assertEquals(1, event.getCell(6).getNumericCellValue());
            assertEquals(done.activity().presentPoints(), event.getCell(11).getNumericCellValue());
            assertEquals(at.toLocalDateTime(), event.getCell(2).getLocalDateTimeCellValue());
            var draft = find(book.getSheet("Attendance"), "Planning meeting");
            assertEquals("Unmarked", draft.getCell(5).getStringCellValue());
            assertEquals("Not finalized", draft.getCell(6).getStringCellValue());
            assertEquals(0, draft.getCell(7).getNumericCellValue());
            assertEquals("Cancelled", find(book.getSheet("Events"), "Cancelled meeting").getCell(4).getStringCellValue());
            assertEquals(7, book.getSheet("Attendance").getLastRowNum());
        }
        assertEquals(before, points.ledger(term).entries().size());
        saveSample("events", bytes);
    }

    @Test void emptyAndClosedTermsDoNotLeakOtherTerms() throws Exception {
        points.award(new PointService.Command(UUID.randomUUID(), term, member, 8, "Award"), "president");
        terms.transition(term, new OrganizationService.Change(1L), "president", false);
        try (var book = open(exports.members(term))) { assertEquals(8, book.getSheet("Members").getRow(5).getCell(8).getNumericCellValue()); }
        var empty = terms.create(new OrganizationService.Details("Empty term", at.toLocalDate().plusDays(11), at.toLocalDate().plusDays(30), null, null), "president");
        try (var book = open(exports.members(empty.id()))) {
            assertEquals(0, book.getSheet("Members").getRow(5).getCell(8).getNumericCellValue());
            assertEquals(4, book.getSheet("Point history").getLastRowNum());
        }
        try (var book = open(exports.events(empty.id()))) { assertEquals(4, book.getSheet("Events").getLastRowNum()); }
    }

    @Test void downloadsRequirePresidentAndReturnRealXlsxAndSafeHeaders() throws Exception {
        for (String kind : List.of("members", "events")) {
            String url = "/api/v1/exports/" + kind;
            mvc.perform(get(url).param("termId", term.toString())).andExpect(status().isUnauthorized());
            mvc.perform(get(url).param("termId", term.toString()).with(user("member").roles("MEMBER"))).andExpect(status().isForbidden());
            var response = mvc.perform(get(url).accept("application/json").param("termId", term.toString()).with(user("president").roles("PRESIDENT")))
                .andExpect(status().isOk()).andExpect(content().contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"apex-" + kind + "-" + term + ".xlsx\""))
                .andReturn().getResponse();
            try (var book = open(response.getContentAsByteArray())) { assertEquals(2, book.getNumberOfSheets()); }
            mvc.perform(get(url).param("termId", UUID.randomUUID().toString()).with(user("president").roles("PRESIDENT"))).andExpect(status().isNotFound());
            mvc.perform(get(url).param("termId", "invalid").with(user("president").roles("PRESIDENT"))).andExpect(status().isBadRequest());
        }
    }
    private ActivityService.View create(String title, ActivityService.Kind kind) {
        return activities.create(new ActivityService.Details(UUID.randomUUID(), term, title, kind, at, "Organization room", "Practice event details", List.of(member), null), "president");
    }
    private static XSSFWorkbook open(byte[] bytes) throws Exception { return new XSSFWorkbook(new ByteArrayInputStream(bytes)); }
    private static Row find(Sheet sheet, String title) {
        for (int i = 5; i <= sheet.getLastRowNum(); i++) if (sheet.getRow(i).getCell(0).getStringCellValue().equals(title)) return sheet.getRow(i);
        throw new AssertionError("Missing row: " + title);
    }
    private static void saveSample(String name, byte[] bytes) throws Exception {
        if (Boolean.getBoolean("apex.exportSamples")) {
            var dir = Path.of("target", "excel-samples"); Files.createDirectories(dir); Files.write(dir.resolve(name + ".xlsx"), bytes);
        }
    }
}
