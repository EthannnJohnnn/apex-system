package ph.edu.slsu.psim.apex.activity;

import java.security.Principal;
import java.util.*;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/activities")
public class ActivityController {
    private final ActivityService service;
    public ActivityController(ActivityService service) { this.service=service; }
    @GetMapping public List<ActivityService.Activity> list(@RequestParam UUID termId) { return service.list(termId); }
    @GetMapping("/summary") public ActivityService.Summary summary(@RequestParam UUID termId) { return service.summary(termId); }
    @GetMapping("/{id}") public ActivityService.View get(@PathVariable UUID id) { return service.view(id); }
    @PostMapping public ActivityService.View create(@RequestBody ActivityService.Details body,Principal user) { return service.create(body,user.getName()); }
    @PutMapping("/{id}") public ActivityService.View edit(@PathVariable UUID id,@RequestBody ActivityService.Details body,Principal user) { return service.edit(id,body,user.getName()); }
    @PutMapping("/{id}/attendance") public ActivityService.View draft(@PathVariable UUID id,@RequestBody ActivityService.Draft body,Principal user) { return service.saveDraft(id,body,user.getName()); }
    @PostMapping("/{id}/finalize") public ActivityService.View finalizeAttendance(@PathVariable UUID id,@RequestBody ActivityService.Change body,Principal user) { return service.finalizeAttendance(id,body,user.getName()); }
    @PostMapping("/{id}/cancel") public ActivityService.View cancel(@PathVariable UUID id,@RequestBody ActivityService.Change body,Principal user) { return service.cancel(id,body,user.getName()); }
    @PostMapping("/{id}/corrections") public ActivityService.View correct(@PathVariable UUID id,@RequestBody ActivityService.Correction body,Principal user) { return service.correct(id,body,user.getName(),false); }
    @PostMapping("/{id}/closed-corrections") public ActivityService.View correctClosed(@PathVariable UUID id,@RequestBody ActivityService.Correction body,Principal user) { return service.correct(id,body,user.getName(),true); }
    @ExceptionHandler(ResponseStatusException.class) ResponseEntity<?> invalid(ResponseStatusException e) { return ResponseEntity.status(e.getStatusCode()).body(Map.of("message",e.getReason())); }
    @ExceptionHandler(HttpMessageNotReadableException.class) ResponseEntity<?> malformed() { return ResponseEntity.badRequest().body(Map.of("message","Check the activity, schedule, attendees and attendance status.")); }
}
