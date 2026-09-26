package ph.edu.slsu.psim.apex.member;

import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/members")
public class MemberController {
    private final MemberService members;
    public MemberController(MemberService members) { this.members = members; }
    @GetMapping public List<MemberService.Member> list() { return members.list(); }
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public MemberService.Member create(@RequestBody MemberService.Details input, Principal actor) {
        return members.create(input, actor.getName());
    }
    @PutMapping("/{id}")
    public MemberService.Member edit(@PathVariable UUID id, @RequestBody MemberService.Details input, Principal actor) {
        return members.edit(id, input, actor.getName());
    }
    @PostMapping("/{id}/eligibility")
    public MemberService.Member eligibility(@PathVariable UUID id, @RequestBody MemberService.Eligibility input, Principal actor) {
        return members.eligibility(id, input, actor.getName());
    }
    @PostMapping("/{id}/status")
    public MemberService.Member status(@PathVariable UUID id, @RequestBody MemberService.Status input) {
        return members.status(id, input);
    }
    @GetMapping("/{id}/eligibility-history")
    public List<MemberService.History> history(@PathVariable UUID id) { return members.history(id); }

    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<?> invalid(ResponseStatusException error) {
        return ResponseEntity.status(error.getStatusCode()).body(Map.of("message", error.getReason()));
    }
    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<?> duplicate() {
        return ResponseEntity.status(409).body(Map.of("message", "Student / Member ID already exists or conflicts with a record rule."));
    }
    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<?> malformed() {
        return ResponseEntity.badRequest().body(Map.of("message", "Check the member fields and position category."));
    }
}
