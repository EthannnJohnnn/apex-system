package ph.edu.slsu.psim.apex.report;

import java.time.OffsetDateTime;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import ph.edu.slsu.psim.apex.organization.OrganizationService;
import ph.edu.slsu.psim.apex.activity.ActivityService;

@Service
@Transactional(readOnly=true,isolation=org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
public class ReportService {
    public record Ranked(int rank,UUID memberId,String memberCode,String name,long total) {}
    public record Leaderboard(OrganizationService.Term term,String eligibilityBasis,int eligibleCount,List<Ranked> members) {}
    public record Counts(int totalMembers,int activeMembers,int activeEligible,long netPoints,int openWarnings) {}
    public record Change(UUID id,String memberName,int amount,String kind,OffsetDateTime recordedAt) {}
    public record Dashboard(OrganizationService.Term term,Counts counts,ActivityService.Summary attendance,List<Ranked> leaders,List<Change> changes) {}
    private record Candidate(UUID id,String code,String name,long total) {}
    private final JdbcTemplate jdbc;
    private final OrganizationService terms;
    private final ActivityService activities;
    public ReportService(JdbcTemplate jdbc,OrganizationService terms,ActivityService activities) { this.jdbc=jdbc; this.terms=terms; this.activities=activities; }

    public Leaderboard leaderboard(UUID termId,String view) {
        if (!Set.of("top10","all").contains(view)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Choose top10 or all.");
        var term=terms.get(termId);
        boolean closed=term.status().equals("CLOSED");
        OffsetDateTime cutoff=closed ? jdbc.queryForObject("SELECT changed_at FROM term_history WHERE term_id=? AND action='CLOSED' ORDER BY term_version LIMIT 1",OffsetDateTime.class,termId) : null;
        String eligible=closed ? "EXISTS (SELECT 1 FROM member_eligibility_history h WHERE h.member_id=m.id AND h.eligible=TRUE AND h.effective_at<=? AND NOT EXISTS (SELECT 1 FROM member_eligibility_history later WHERE later.member_id=h.member_id AND later.effective_at<=? AND (later.effective_at>h.effective_at OR (later.effective_at=h.effective_at AND later.member_version>h.member_version))))" : "m.active=TRUE AND m.eligible=TRUE";
        String sql="SELECT m.id,m.member_code,m.name,COALESCE(SUM(p.amount),0) AS total FROM member m LEFT JOIN point_entry p ON p.member_id=m.id AND p.term_id=? WHERE m.category<>'PRESIDENT' AND "+eligible+" GROUP BY m.id,m.member_code,m.name ORDER BY total DESC,LOWER(m.name),m.member_code,m.id";
        Object[] args=closed ? new Object[]{termId,cutoff,cutoff} : new Object[]{termId};
        var rows=jdbc.query(sql,(r,n) -> new Candidate(r.getObject("id",UUID.class),r.getString("member_code"),r.getString("name"),r.getLong("total")),args);
        List<Ranked> ranked=new ArrayList<>();
        int rank=0;
        long previous=0;
        for(int i=0;i<rows.size();i++) {
            var row=rows.get(i);
            if(i==0 || row.total()!=previous) rank=i+1;
            previous=row.total();
            // Include every member tied at the top-ten boundary.
            if(view.equals("all") || rank<=10) ranked.add(new Ranked(rank,row.id(),row.code(),row.name(),row.total()));
        }
        return new Leaderboard(term,closed ? "Eligibility at term closure; inactive former members retained; president excluded." : "Currently active, point-eligible members; president excluded.",rows.size(),ranked);
    }
    public Dashboard dashboard(UUID requestedTerm) {
        var term=requestedTerm!=null ? terms.get(requestedTerm) : terms.terms().stream().filter(t -> t.status().equals("ACTIVE")).findFirst().orElse(null);
        int total=jdbc.queryForObject("SELECT COUNT(*) FROM member",Integer.class);
        int active=jdbc.queryForObject("SELECT COUNT(*) FROM member WHERE active=TRUE",Integer.class);
        int eligible=jdbc.queryForObject("SELECT COUNT(*) FROM member WHERE active=TRUE AND eligible=TRUE AND category<>'PRESIDENT'",Integer.class);
        if(term==null) return new Dashboard(null,new Counts(total,active,eligible,0,0),new ActivityService.Summary(List.of(),List.of()),List.of(),List.of());
        long net=jdbc.queryForObject("SELECT COALESCE(SUM(amount),0) FROM point_entry WHERE term_id=?",Long.class,term.id());
        int warnings=jdbc.queryForObject("SELECT COUNT(*) FROM warning_record WHERE term_id=? AND severity IS NOT NULL AND status='OPEN'",Integer.class,term.id());
        // No reason, warning identifier, assigned role, severity, or warning narrative leaves this summary.
        var changes=jdbc.query("SELECT p.id,m.name,p.amount,p.kind,p.recorded_at FROM point_entry p JOIN member m ON m.id=p.member_id WHERE p.term_id=? AND m.category<>'PRESIDENT' ORDER BY p.sequence_no DESC LIMIT 5",
            (r,n) -> new Change(r.getObject("id",UUID.class),r.getString("name"),r.getInt("amount"),r.getString("kind"),r.getObject("recorded_at",OffsetDateTime.class)),term.id());
        return new Dashboard(term,new Counts(total,active,eligible,net,warnings),activities.summary(term.id()),leaderboard(term.id(),"top10").members(),changes);
    }
}
