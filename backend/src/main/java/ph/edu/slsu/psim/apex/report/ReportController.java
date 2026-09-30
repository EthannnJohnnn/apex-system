package ph.edu.slsu.psim.apex.report;

import java.util.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1")
public class ReportController {
    private final ReportService reports;
    public ReportController(ReportService reports) { this.reports=reports; }
    @GetMapping("/leaderboard") public ReportService.Leaderboard leaderboard(@RequestParam UUID termId,@RequestParam(defaultValue="top10") String view) { return reports.leaderboard(termId,view); }
    @GetMapping("/dashboard") public ReportService.Dashboard dashboard(@RequestParam(required=false) UUID termId) { return reports.dashboard(termId); }
    @ExceptionHandler(ResponseStatusException.class) ResponseEntity<?> invalid(ResponseStatusException e) { return ResponseEntity.status(e.getStatusCode()).body(Map.of("message",e.getReason())); }
}
