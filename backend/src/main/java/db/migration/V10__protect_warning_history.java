package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

public class V10__protect_warning_history extends BaseJavaMigration {
    @Override public void migrate(Context context) throws Exception {
        if (!context.getConnection().getMetaData().getDatabaseProductName().equals("PostgreSQL")) return;
        try (var statement=context.getConnection().createStatement()) {
            for (String table:new String[]{"warning_history","warning_action"})
                statement.execute("CREATE TRIGGER "+table+"_immutable BEFORE UPDATE OR DELETE OR TRUNCATE ON "+table+" FOR EACH STATEMENT EXECUTE FUNCTION reject_attendance_history_mutation()");
        }
    }
}
