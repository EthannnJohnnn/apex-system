package ph.edu.slsu.psim.apex.health;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class DatabaseHealthService {

    private final JdbcTemplate jdbcTemplate;

    public DatabaseHealthService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public HealthResponse check() {
        return jdbcTemplate.queryForObject(
                "SELECT application_name, schema_version FROM app_metadata WHERE id = 1",
                (resultSet, rowNumber) -> new HealthResponse(
                        "UP",
                        resultSet.getString("application_name"),
                        "UP",
                        resultSet.getInt("schema_version")));
    }
}
