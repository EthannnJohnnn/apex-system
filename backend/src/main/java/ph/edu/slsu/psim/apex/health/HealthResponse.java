package ph.edu.slsu.psim.apex.health;

public record HealthResponse(
        String status,
        String application,
        String database,
        int schemaVersion) {
}
