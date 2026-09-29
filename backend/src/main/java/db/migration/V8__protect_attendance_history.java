package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/** Attendance audit records and retry receipts must remain append-only in PostgreSQL. */
public class V8__protect_attendance_history extends BaseJavaMigration {
    @Override public void migrate(Context context) throws Exception {
        if (!context.getConnection().getMetaData().getDatabaseProductName().equals("PostgreSQL")) return;
        try (var statement=context.getConnection().createStatement()) {
            statement.execute("""
                CREATE FUNCTION reject_attendance_history_mutation() RETURNS trigger LANGUAGE plpgsql AS $$
                BEGIN
                    RAISE EXCEPTION 'Attendance history is append-only. Add a correction instead.';
                END;
                $$
                """);
            for(String table:new String[]{"attendance_history","activity_action"})
                statement.execute("CREATE TRIGGER "+table+"_immutable BEFORE UPDATE OR DELETE OR TRUNCATE ON "+table+" FOR EACH STATEMENT EXECUTE FUNCTION reject_attendance_history_mutation()");
        }
    }
}
