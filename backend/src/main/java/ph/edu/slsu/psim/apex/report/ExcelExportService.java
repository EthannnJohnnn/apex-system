package ph.edu.slsu.psim.apex.report;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import ph.edu.slsu.psim.apex.activity.ActivityService;
import ph.edu.slsu.psim.apex.member.MemberService;
import ph.edu.slsu.psim.apex.organization.OrganizationService;
import ph.edu.slsu.psim.apex.points.PointService;

/** Read-only snapshots: exporting never finalizes attendance or changes the ledger. */
@Service
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class ExcelExportService {
    private static final ZoneId ZONE = ZoneId.of("Asia/Manila");
    private final MemberService members;
    private final PointService points;
    private final ActivityService activities;
    private final OrganizationService terms;

    public ExcelExportService(MemberService members, PointService points, ActivityService activities, OrganizationService terms) {
        this.members = members; this.points = points; this.activities = activities; this.terms = terms;
    }

    public byte[] members(UUID termId) throws IOException {
        var ledger = points.ledger(termId);
        var people = members.list();
        var byId = people.stream().collect(Collectors.toMap(MemberService.Member::id, m -> m));
        var events = activities.list(termId).stream().collect(Collectors.toMap(ActivityService.Activity::id, a -> a));
        var reversed = ledger.entries().stream().map(PointService.Entry::reversesId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<UUID, long[]> totals = new HashMap<>();
        for (var entry : ledger.entries()) {
            // Reversals cancel old entries; they are not new awards or deductions.
            if (entry.kind().equals("REVERSAL") || reversed.contains(entry.id())) continue;
            var sum = totals.computeIfAbsent(entry.memberId(), id -> new long[2]);
            if (entry.amount() > 0) sum[0] += entry.amount(); else sum[1] -= entry.amount();
        }
        try (var book = new XSSFWorkbook()) {
            var styles = new Styles(book);
            var summary = sheet(book, styles, "Members", ledger.term().name(),
                "All members, including inactive. Current member details; points for this term after corrections.",
                "Member ID", "Name", "Position", "Category", "Status", "Point eligible", "Points earned", "Deductions", "Net points", "Notes");
            for (var member : people) {
                var sum = totals.getOrDefault(member.id(), new long[2]);
                row(summary, styles, member.memberCode(), member.name(), member.position(), label(member.category()),
                    member.active() ? "Active" : "Inactive", member.eligible() ? "Yes" : "No", sum[0], sum[1], sum[0] - sum[1], member.notes());
            }
            var history = sheet(book, styles, "Point history", ledger.term().name(),
                "All transactions, including reversals. Signed amounts sum to net points. Times are Asia/Manila.",
                "Member ID", "Name", "Recorded at", "Points (+/-)", "Type", "Reason", "Event", "Recorded by", "Entry ID", "Reverses entry", "Replaces entry", "Warning ID");
            for (var entry : ledger.entries().reversed()) {
                var member = byId.get(entry.memberId());
                var event = events.get(entry.activityId());
                row(history, styles, member.memberCode(), member.name(), entry.recordedAt(), entry.amount(), label(entry.kind()),
                    entry.reason(), event == null ? "" : event.title(), entry.actor(), entry.id(), entry.reversesId(), entry.replacesId(), entry.warningId());
            }
            finish(summary); finish(history);
            return bytes(book);
        }
    }

    public byte[] events(UUID termId) throws IOException {
        var term = terms.get(termId);
        try (var book = new XSSFWorkbook()) {
            var styles = new Styles(book);
            var events = sheet(book, styles, "Events", term.name(),
                "All meetings and events in this term. Times are Asia/Manila. Drafts and cancelled activities award no points.",
                "Event", "Type", "Scheduled at", "Location", "Status", "Expected", "Present", "Late", "Excused", "Absent", "Unmarked", "Points awarded", "Present points", "Late points", "Description", "Event ID");
            var attendance = sheet(book, styles, "Attendance", term.name(),
                "Saved attendance only. Unmarked is not absent. Eligibility is captured when attendance is finalized.",
                "Event", "Scheduled at", "Event status", "Member ID", "Name", "Attendance", "Eligible at finalization", "Points awarded", "Event ID");
            for (var activity : activities.list(termId)) {
                var roster = activities.view(activity.id()).attendees();
                row(events, styles, activity.title(), label(activity.kind()), activity.scheduledAt(), activity.location(), label(activity.status()),
                    roster.size(), count(roster, ActivityService.Status.PRESENT), count(roster, ActivityService.Status.LATE),
                    count(roster, ActivityService.Status.EXCUSED), count(roster, ActivityService.Status.ABSENT), count(roster, null),
                    roster.stream().mapToLong(a -> a.points()).sum(), activity.presentPoints(), activity.latePoints(), activity.description(), activity.id());
                for (var person : roster) row(attendance, styles, activity.title(), activity.scheduledAt(), label(activity.status()),
                    person.memberCode(), person.name(), person.status() == null ? "Unmarked" : label(person.status()),
                    person.eligibleSnapshot() == null ? "Not finalized" : person.eligibleSnapshot() ? "Yes" : "No", person.points(), activity.id());
            }
            finish(events); finish(attendance);
            return bytes(book);
        }
    }

    private static long count(List<ActivityService.Attendee> people, ActivityService.Status status) {
        return people.stream().filter(p -> p.status() == status).count();
    }
    private static String label(Object value) {
        String text = value.toString().toLowerCase(Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }
    private static Sheet sheet(XSSFWorkbook book, Styles styles, String name, String term, String note, String... headers) {
        var sheet = book.createSheet(name);
        sheet.setDisplayGridlines(false);
        var title = sheet.createRow(1).createCell(0);
        title.setCellValue("Apex " + name.toLowerCase(Locale.ROOT) + " — " + term); title.setCellStyle(styles.title);
        sheet.createRow(2).createCell(0).setCellValue(note);
        var header = sheet.createRow(4); header.setHeightInPoints(32);
        for (int i = 0; i < headers.length; i++) {
            var cell = header.createCell(i); cell.setCellValue(headers[i]); cell.setCellStyle(styles.header);
            int width = switch (headers[i]) {
                case "Name", "Event" -> 28;
                case "Notes", "Reason", "Description" -> 48;
                case "Location", "Position" -> 24;
                case "Recorded at", "Scheduled at" -> 23;
                case "Entry ID", "Reverses entry", "Replaces entry", "Warning ID", "Event ID" -> 38;
                default -> 18;
            };
            sheet.setColumnWidth(i, width * 256);
        }
        sheet.createFreezePane(2, 5);
        sheet.setRepeatingRows(new CellRangeAddress(4, 4, -1, -1));
        sheet.getPrintSetup().setLandscape(true);
        return sheet;
    }
    private static void row(Sheet sheet, Styles styles, Object... values) {
        var row = sheet.createRow(sheet.getLastRowNum() + 1);
        int lines = 1;
        for (int i = 0; i < values.length; i++) {
            var cell = row.createCell(i);
            Object value = values[i];
            cell.setCellStyle(styles.body);
            if (value == null) continue;
            if (value instanceof Number number) {
                cell.setCellValue(number.doubleValue()); cell.setCellStyle(styles.number);
            } else if (value instanceof OffsetDateTime date) {
                cell.setCellValue(date.atZoneSameInstant(ZONE).toLocalDateTime()); cell.setCellStyle(styles.date);
            } else {
                // Explicit string cells prevent member-provided text from becoming Excel formulas.
                String text = value.toString();
                if (text.isEmpty()) continue;
                cell.setCellValue(text);
                int width = Math.max(1, sheet.getColumnWidth(i) / 256 - 2);
                int cellLines = Arrays.stream(text.split("\\R", -1)).mapToInt(s -> Math.max(1, (s.length() + width - 1) / width)).sum();
                lines = Math.max(lines, cellLines);
            }
        }
        row.setHeightInPoints(Math.min(409, Math.max(24, lines * 15 + 6)));
    }
    private static void finish(Sheet sheet) {
        sheet.setAutoFilter(new CellRangeAddress(4, Math.max(4, sheet.getLastRowNum()), 0, sheet.getRow(4).getLastCellNum() - 1));
    }
    private static byte[] bytes(XSSFWorkbook book) throws IOException {
        var output = new ByteArrayOutputStream(); book.write(output); return output.toByteArray();
    }
    private static class Styles {
        final CellStyle body, number, date, header, title;
        Styles(XSSFWorkbook book) {
            var font = book.createFont(); font.setFontName("Arial"); font.setFontHeightInPoints((short) 10);
            body = book.createCellStyle(); body.setFont(font); body.setVerticalAlignment(VerticalAlignment.TOP); body.setWrapText(true); body.setAlignment(HorizontalAlignment.LEFT); body.setIndention((short) 1); body.setDataFormat(book.createDataFormat().getFormat("@"));
            number = book.createCellStyle(); number.cloneStyleFrom(body); number.setAlignment(HorizontalAlignment.RIGHT); number.setDataFormat(book.createDataFormat().getFormat("#,##0"));
            date = book.createCellStyle(); date.cloneStyleFrom(body); date.setDataFormat(book.createDataFormat().getFormat("mmm d, yyyy h:mm AM/PM"));
            var bold = book.createFont(); bold.setFontName("Arial"); bold.setFontHeightInPoints((short) 10); bold.setBold(true); bold.setColor(IndexedColors.WHITE.getIndex());
            header = book.createCellStyle(); header.cloneStyleFrom(body); header.setIndention((short) 0); header.setFont(bold); header.setFillForegroundColor(IndexedColors.DARK_RED.getIndex()); header.setFillPattern(FillPatternType.SOLID_FOREGROUND); header.setAlignment(HorizontalAlignment.CENTER); header.setVerticalAlignment(VerticalAlignment.CENTER);
            var large = book.createFont(); large.setFontName("Arial"); large.setFontHeightInPoints((short) 14); large.setBold(true);
            title = book.createCellStyle(); title.setFont(large);
        }
    }
}
