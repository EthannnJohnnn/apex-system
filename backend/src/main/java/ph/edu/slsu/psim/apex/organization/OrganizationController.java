package ph.edu.slsu.psim.apex.organization;

import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1")
public class OrganizationController {
    public record SettingsRequest(String organizationName, java.math.BigDecimal meetingPresent,
        java.math.BigDecimal meetingLate, java.math.BigDecimal eventPresent, java.math.BigDecimal eventLate, Long version) {}
    private final OrganizationService service;
    public OrganizationController(OrganizationService service) { this.service = service; }
    @GetMapping("/settings") public OrganizationService.Settings settings() { return service.settings(); }
    @PutMapping("/settings") public OrganizationService.Settings save(@RequestBody SettingsRequest input) {
        return service.saveSettings(new OrganizationService.Settings(input.organizationName(), whole(input.meetingPresent()),
            whole(input.meetingLate()), whole(input.eventPresent()), whole(input.eventLate()), input.version()));
    }
    private static Integer whole(java.math.BigDecimal value) {
        if (value == null) return null;
        try { return value.intValueExact(); }
        catch (ArithmeticException error) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Points must be whole numbers from 0 to 1000."); }
    }
    @GetMapping("/terms") public List<OrganizationService.Term> terms() { return service.terms(); }
    @PostMapping("/terms") @ResponseStatus(HttpStatus.CREATED)
    public OrganizationService.Term create(@RequestBody OrganizationService.Details input, Principal actor) { return service.create(input, actor.getName()); }
    @PutMapping("/terms/{id}")
    public OrganizationService.Term edit(@PathVariable UUID id, @RequestBody OrganizationService.Details input, Principal actor) { return service.edit(id, input, actor.getName(), false); }
    @PostMapping("/terms/{id}/corrections")
    public OrganizationService.Term correct(@PathVariable UUID id, @RequestBody OrganizationService.Details input, Principal actor) { return service.edit(id, input, actor.getName(), true); }
    @PostMapping("/terms/{id}/activate")
    public OrganizationService.Term activate(@PathVariable UUID id, @RequestBody OrganizationService.Change input, Principal actor) { return service.transition(id, input, actor.getName(), true); }
    @PostMapping("/terms/{id}/close")
    public OrganizationService.Term close(@PathVariable UUID id, @RequestBody OrganizationService.Change input, Principal actor) { return service.transition(id, input, actor.getName(), false); }
    @GetMapping("/terms/{id}/history") public List<OrganizationService.History> history(@PathVariable UUID id) { return service.history(id); }
    @ExceptionHandler(ResponseStatusException.class) ResponseEntity<?> invalid(ResponseStatusException error) {
        return ResponseEntity.status(error.getStatusCode()).body(Map.of("message", error.getReason()));
    }
    @ExceptionHandler(DataIntegrityViolationException.class) ResponseEntity<?> conflict() {
        return ResponseEntity.status(409).body(Map.of("message", "Term name already exists or another term is active. Reload and check your entries."));
    }
    @ExceptionHandler(HttpMessageNotReadableException.class) ResponseEntity<?> malformed() {
        return ResponseEntity.badRequest().body(Map.of("message", "Check the dates, text and whole-number point values."));
    }
}
