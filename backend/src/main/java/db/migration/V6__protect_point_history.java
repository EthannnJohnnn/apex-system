package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/** Production PostgreSQL enforces append-only history even for accidental direct SQL. */
public class V6__protect_point_history extends BaseJavaMigration {
    @Override public void migrate(Context context) throws Exception {
        // H2 is only a disposable test database; PostgreSQL integration tests verify these triggers.
        if (!context.getConnection().getMetaData().getDatabaseProductName().equals("PostgreSQL")) return;
        try (var statement = context.getConnection().createStatement()) {
            statement.execute("""
                CREATE FUNCTION reject_point_history_mutation() RETURNS trigger LANGUAGE plpgsql AS $$
                BEGIN
                    RAISE EXCEPTION 'Point history is append-only. Add a correction instead.';
                END;
                $$
                """);
            for (String table : new String[]{"point_entry", "point_request"}) {
                statement.execute("CREATE TRIGGER " + table + "_immutable BEFORE UPDATE OR DELETE OR TRUNCATE ON "
                    + table + " FOR EACH STATEMENT EXECUTE FUNCTION reject_point_history_mutation()");
            }
        }
    }
}
