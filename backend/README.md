# Apex backend

Spring Boot API for the local Apex system.

For president account setup, login, and recovery, follow [Stage 8](../docs/stage-8.md).

## Requirements

- Java 24
- PostgreSQL 18
- Database: `apex_db`
- Database user: `apex_app`

## Local configuration

Create the `APEX_DB_PASSWORD` Windows user environment variable and set it to the password of `apex_app`. Never save the real password in Git.

The remaining settings have safe local defaults. See `.env.example` for every supported variable.

## Run

From the `backend` folder:

```powershell
.\mvnw.cmd spring-boot:run
```

Then open <http://localhost:8080/api/v1/health>. A working response looks like:

```json
{
  "status": "UP",
  "application": "Apex",
  "database": "UP",
  "schemaVersion": 1
}
```

## Test

```powershell
.\mvnw.cmd test
```

Tests use a temporary in-memory database. The real PostgreSQL password is not required for tests.
