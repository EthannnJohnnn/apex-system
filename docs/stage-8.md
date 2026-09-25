# Stage 8: president account and login

The president account is separate from the PostgreSQL user apex_app.
Only one president account can exist. There is no public registration.

## Create your account

Use a VS Code **PowerShell** terminal on the Apex laptop.
The database password must already be saved as the Windows user variable APEX_DB_PASSWORD.

1. Go to the backend:

   `cd D:\apex_system\backend`

2. If this terminal was open before the variable was created, load it without displaying it:

   `$env:APEX_DB_PASSWORD = [Environment]::GetEnvironmentVariable('APEX_DB_PASSWORD', 'User')`

3. Build the application:

   `.\mvnw.cmd package`

4. Create the president account:

   `java -jar target\apex-0.0.1-SNAPSHOT.jar --apex.account=create`

5. Choose a username and enter your password twice. Password input is hidden.
   Use 3–64 letters/numbers/dots/underscores/hyphens for the username.
   Use at least 12 characters and at most 72 UTF-8 bytes for the password.
   Do not share the password or commit it to Git.

The command connects directly to local PostgreSQL, then exits; it starts no web server.
If the terminal does not provide a hidden password prompt, use Windows Terminal PowerShell.

## Run and check

1. In the backend terminal: `.\mvnw.cmd spring-boot:run`.
2. In a second terminal: `cd D:\apex_system\frontend`, then `npm run dev`.
3. Open the URL printed by Vite. Sign in with your president account.
4. Refresh: you should stay signed in.
5. Try Change password using the current password and matching new passwords.
   All old sessions become invalid; sign in again.
6. Sign out, then refresh: the login form should return.

## Forgotten password

On the laptop, run the same JAR with `--apex.account=reset`.
Enter the existing username and a new password twice. Old sessions are rejected
on their next request. This recovery requires local laptop/database access;
it is not offered through an HTTP endpoint.

## What the code does

- Flyway V2 creates president_account with a database constraint allowing only id 1.
- PresidentAccounts reads the account, hashes passwords with BCrypt, and changes them.
- Spring Security handles login, logout, session IDs, and CSRF protection.
- CredentialVersionFilter rejects sessions created before a password change/reset.
- AuthController provides CSRF tokens, current account information, and password changes.
- PresidentAccess displays login, sign out, password changes, and expired-session messages.
- The browser keeps its session ID in an HttpOnly cookie; no password is stored in localStorage.

## Endpoints

| Method | Path | Purpose |
| --- | --- | --- |
| GET | /api/v1/auth/csrf | Obtain a token for a POST request |
| POST | /api/v1/auth/login | Username/password form submission |
| GET | /api/v1/auth/me | Current president; requires login |
| POST | /api/v1/auth/logout | End session; requires CSRF token |
| POST | /api/v1/auth/password | Verify current password and change it |

Sessions expire after 30 minutes of inactivity or a backend restart.
The UI rechecks on returning to the tab and handles expired sessions during requests.
All business API routes require PRESIDENT authorization.

For this stage, the backend is bound to 127.0.0.1 and uses local HTTP.
HttpOnly and SameSite=Strict are enabled. The Secure cookie flag defaults to false
so local HTTP works. Set APEX_COOKIE_SECURE=true when local HTTPS is introduced
for the later phone-access stage; do not expose this development setup to the LAN.

## Verification

Run `.\mvnw.cmd test` in backend and `npm run build` / `npm run lint` in frontend.
Authentication tests use fictional credentials in an isolated in-memory database.
They do not create your real president account.

Reference: [Spring Security CSRF integration](https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html).
