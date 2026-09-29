package ph.edu.slsu.psim.apex.warning;

import java.math.BigDecimal;
import java.security.Principal;
import java.util.*;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/warnings")
public class WarningController {
    public record Body(UUID requestId,UUID termId,UUID memberId,String incident,WarningService.Severity severity,
        UUID activityId,String assignedRole,BigDecimal deduction,String reason) {}
    private final WarningService service;
    public WarningController(WarningService service) { this.service=service; }
    @GetMapping public List<WarningService.Warning> list(@RequestParam UUID termId) { return service.list(termId); }
    @GetMapping("/{id}") public WarningService.View view(@PathVariable UUID id) { return service.view(id); }
    @PostMapping public WarningService.View create(@RequestBody Body b,Principal user) {
        Integer amount;
        try { amount=b.deduction()==null ? null : b.deduction().intValueExact(); }
        catch (ArithmeticException e) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Deduction must be a whole number from 0 to 1000."); }
        return service.create(new WarningService.Create(b.requestId(),b.termId(),b.memberId(),b.incident(),b.severity(),b.activityId(),b.assignedRole(),amount,b.reason()),user.getName());
    }
    @PostMapping("/{id}/resolve") public WarningService.View resolve(@PathVariable UUID id,@RequestBody WarningService.Change b,Principal user) { return service.change(id,b,user.getName(),false,false); }
    @PostMapping("/{id}/cancel") public WarningService.View cancel(@PathVariable UUID id,@RequestBody WarningService.Change b,Principal user) { return service.change(id,b,user.getName(),true,false); }
    @PostMapping("/{id}/closed-cancel") public WarningService.View closedCancel(@PathVariable UUID id,@RequestBody WarningService.Change b,Principal user) { return service.change(id,b,user.getName(),true,true); }
    @ExceptionHandler(ResponseStatusException.class) ResponseEntity<?> invalid(ResponseStatusException e) { return ResponseEntity.status(e.getStatusCode()).body(Map.of("message",e.getReason())); }
    @ExceptionHandler(HttpMessageNotReadableException.class) ResponseEntity<?> malformed() { return ResponseEntity.badRequest().body(Map.of("message","Check the incident, severity, member and deduction.")); }
}
