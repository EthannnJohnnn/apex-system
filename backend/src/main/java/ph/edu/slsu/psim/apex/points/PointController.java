package ph.edu.slsu.psim.apex.points;

import java.math.BigDecimal;
import java.security.Principal;
import java.util.*;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/points")
public class PointController {
    public record Input(UUID requestId, UUID termId, UUID memberId, BigDecimal amount, String reason) {}
    private final PointService points;
    public PointController(PointService points) { this.points=points; }
    @GetMapping public PointService.Ledger ledger(@RequestParam UUID termId) { return points.ledger(termId); }
    @PostMapping public List<PointService.Entry> award(@RequestBody Input input, Principal actor) {
        return points.award(new PointService.Command(input.requestId(),input.termId(),input.memberId(),whole(input.amount()),input.reason()),actor.getName());
    }
    @PostMapping("/{id}/corrections") public List<PointService.Entry> correct(@PathVariable UUID id,@RequestBody Input input,Principal actor) {
        return points.correct(id,new PointService.Correction(input.requestId(),whole(input.amount()),input.reason()),actor.getName(),false);
    }
    @PostMapping("/{id}/closed-corrections") public List<PointService.Entry> correctClosed(@PathVariable UUID id,@RequestBody Input input,Principal actor) {
        return points.correct(id,new PointService.Correction(input.requestId(),whole(input.amount()),input.reason()),actor.getName(),true);
    }
    private static Integer whole(BigDecimal value) {
        if(value==null) return null;
        try { return value.intValueExact(); }
        catch(ArithmeticException error) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Points must be whole numbers from -1000 to 1000."); }
    }
    @ExceptionHandler(ResponseStatusException.class) ResponseEntity<?> invalid(ResponseStatusException e) {
        return ResponseEntity.status(e.getStatusCode()).body(Map.of("message",e.getReason()));
    }
    @ExceptionHandler(HttpMessageNotReadableException.class) ResponseEntity<?> malformed() {
        return ResponseEntity.badRequest().body(Map.of("message","Check the member, term, request ID and whole-number points."));
    }
}
