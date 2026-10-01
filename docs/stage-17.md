# Stage 17 — Backups and recovery

GitHub protects Apex's source code. It does **not** contain the members, attendance, warnings or point ledger saved in PostgreSQL. The president is responsible for backing up those records.

## Routine backup (Git Bash)

Run from the project root; PostgreSQL must be running. The script reads `APEX_DB_PASSWORD` from the current process or your Windows user environment. Never paste a password into a command or commit it.

```bash
cd /d/apex_system
powershell.exe -NoProfile -File scripts/Backup-Apex.ps1
```

The default destination is `%LOCALAPPDATA%\Apex\backups`, outside the application/project installation folder. The script prints the exact path. It creates **two files**: a compressed `.dump` and its `.dump.json` manifest (time, source database, schema version, SHA-256 checksum and size). Keep them together. It verifies the archive's table of contents and checksum before reporting success; a restore test is still needed to prove recovery.

The default connection is `127.0.0.1:5432`, database `apex_db`, role `apex_app`. If your application uses a different database/port, supply `-Database` / `-Port` explicitly; the tool does not parse `APEX_DB_URL`. `-PgBin` selects a PostgreSQL tools directory if the tools are not on PATH. Use PostgreSQL 18 tools for the current system.

The **14 newest** backups for each source database remain in the main folder. Older verified pairs move into its `archive` subfolder; nothing is permanently deleted automatically. `-KeepRecent` changes this number. Archive storage will grow: review it monthly and remove obsolete copies manually only after confirming recent, tested external copies. A failed backup must not cause old copies to be discarded. `.partial` files are incomplete and must not be used for recovery.

### External-drive copy

After connecting your own protected external drive, replace the example paths below with the actual printed backup path and drive letter:

```bash
powershell.exe -NoProfile -File scripts/Copy-ApexBackup.ps1 -BackupFile 'C:\Users\Ethan\AppData\Local\Apex\backups\YOUR-BACKUP.dump' -DestinationDirectory 'E:\Apex Backups'
```

This copies both files, verifies the destination checksum, and refuses to overwrite an existing pair. A different folder on the same laptop is useful for testing but is **not protection against laptop loss**. Disconnect the drive safely afterward.

Backups contain private member records, warnings and president password hashes. They are compressed, **not encrypted**. Use a protected/encrypted drive where available. Do not upload them to the public GitHub repository, messaging apps or public storage. Restore only backups you trust: a checksum detects accidental changes, not a malicious replacement of both files.

## Before every update

1. Stop Apex and finish any data-entry work. Do not run a database migration while making this backup.
2. Run the backup and require a successful result:

```bash
powershell.exe -NoProfile -File scripts/Backup-Apex.ps1 -Reason pre-update
```

3. Copy the pair to the external drive and confirm `External copy verified`.
4. Only then replace/update the application. Keep the previous application version and its matching backup until the new version is verified. If backup/copy fails, **stop the update**. The Stage 18 installation/update procedure must preserve this gate.

This stage supplies the pre-update backup command and policy; it does not install an updater or scheduled task.

## Safe restore procedure

Do not restore over `apex_db`. Stop Apex before recovery. Preserve the original database and make a current backup if it is still readable.

1. An administrator creates a **new, empty** recovery database owned by `apex_app`. In Git Bash (enter the local PostgreSQL administrator password when prompted):

```bash
createdb -h 127.0.0.1 -U postgres -O apex_app apex_restore_20261001
```

Use a new name if that one already exists. The application role does not need `CREATEDB` or superuser privileges. On a replacement laptop, install PostgreSQL and recreate the restricted `apex_app` login first, using the setup instructions; cluster roles/passwords are not included in this application backup.

2. Restore a trusted pair (replace the example filename):

```bash
cd /d/apex_system
powershell.exe -NoProfile -File scripts/Restore-Apex.ps1 -BackupFile 'E:\Apex Backups\YOUR-BACKUP.dump' -TargetDatabase apex_restore_20261001
```

The tool checks the checksum and size, requires a target named `apex_restore_...`, rejects the source database and any occupied target, and restores in one transaction. It does not use `--create`, change Apex's configuration, or drop the live database. The `--clean` flag is only used after the recovery target is verified empty, to handle its initially empty `public` schema. Do not point another application at the target during restoration.

3. Inspect the restored database before switching:

```bash
psql -U apex_app -h 127.0.0.1 -d apex_restore_20261001 -c "SELECT 'members' AS records, count(*) FROM member UNION ALL SELECT 'attendance', count(*) FROM attendance UNION ALL SELECT 'warnings', count(*) FROM warning_record UNION ALL SELECT 'point entries', count(*) FROM point_entry;"
```

Compare counts with records known at the backup time, then test with the **matching Apex version**. In a new Git Bash backend terminal:

```bash
cd /d/apex_system/backend
export APEX_DB_URL='jdbc:postgresql://127.0.0.1:5432/apex_restore_20261001'
./mvnw.cmd spring-boot:run
```

Open the frontend normally. Sign in with the president credentials valid when the backup was made; check members, term selection, attendance, warning/cancellation history and ledger totals. The recovered president password hash is part of the backup; the database role password is not. Follow Stage 8 account recovery if the president password is unknown.

4. Only after verification should the maintainer set the installed application's `APEX_DB_URL` to the verified recovery database and restart Apex. Do not rename or delete the original automatically. To abandon this development test, stop the test backend and `unset APEX_DB_URL` in that Git Bash terminal. This removes only the temporary shell override.

The archive includes the entire Apex `public` schema, Flyway history, constraints, identity sequences and audit triggers. It deliberately excludes unrelated schemas, PostgreSQL roles, Windows settings, source code and application binaries. Keep installer/configuration recovery instructions separately.

## Responsibilities

- **President:** back up at the end of each day with changes; make an external copy after important attendance/points sessions and at least weekly. Check that the commands report success.
- **Developer/maintainer:** back up before every update; perform a restore drill monthly and before major upgrades; help recover accounts and verify totals after an incident.
- **Handover:** demonstrate backup, external copying and recovery, identify the actual drive/location, and record who will carry out the routine. Do not assume a backup exists because a source-code push succeeded.

## Repeatable recovery test

```bash
cd /d/apex_system
powershell.exe -NoProfile -File scripts/Test-ApexBackup.ps1
```

This developer-only test requires PostgreSQL 18 tools, Java 24 and Maven wrapper access. It starts its own temporary, password-protected PostgreSQL cluster on loopback at a random non-5432 port. It does not touch the installed PostgreSQL service or real `apex_db`. It creates separate source and recovery databases, uses a non-superuser `apex_app` role, seeds fictional records via actual Apex services, and exercises the scripts above.

Checks cover:

- Custom-format backup, checksum/size validation, retention and a simulated external-folder copy.
- Refusal of a live database name, an occupied recovery target, a duplicate copy and a corrupt archive.
- Failed authentication leaving retained backups intact.
- Exact row-count/content fingerprints for **every** restored public table, including members, attendance, warnings, ledger, account hash and histories.
- Restored append-only trigger protection, application startup, expected summaries and continued identity sequencing.

The temporary cluster is stopped afterward. Fictional test artifacts remain under ignored `tmp/backup-test-*` for inspection; no permanent test service or task is installed. The copy test uses another folder, not a physically connected external drive.

Verification on **1 October 2026**: the recovery drill passed, with exact content matches across all **17 public tables**, successful restored-app checks, retention/copy checks and all refusal checks. The temporary server was confirmed stopped afterward. No live organization records were modified by the recovery drill.

Implementation references: PostgreSQL's [pg_dump](https://www.postgresql.org/docs/18/app-pgdump.html) and [pg_restore](https://www.postgresql.org/docs/18/app-pgrestore.html) documentation describe the archive format and transaction-based restore options.

Next: **Stage 18 — laptop packaging and installation**, without phone access.
