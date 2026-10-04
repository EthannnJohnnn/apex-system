package ph.edu.slsu.psim.apex.report;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/exports")
public class ExcelExportController {
    private final ExcelExportService exports;
    public ExcelExportController(ExcelExportService exports) { this.exports = exports; }

    @GetMapping("/members")
    public ResponseEntity<byte[]> members(@RequestParam UUID termId) throws IOException {
        return download(exports.members(termId), "apex-members-" + termId + ".xlsx");
    }
    @GetMapping("/events")
    public ResponseEntity<byte[]> events(@RequestParam UUID termId) throws IOException {
        return download(exports.events(termId), "apex-events-" + termId + ".xlsx");
    }
    private static ResponseEntity<byte[]> download(byte[] bytes, String name) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
            .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(name).build().toString())
            .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
            .body(bytes);
    }
    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<?> invalid(ResponseStatusException error) {
        return ResponseEntity.status(error.getStatusCode()).body(Map.of("message", error.getReason()));
    }
}
