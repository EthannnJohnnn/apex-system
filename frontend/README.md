# Apex frontend

React, TypeScript, Vite, and Material UI provide the local president interface.
The current screen checks whether the Spring Boot backend and PostgreSQL database are ready; login and member records are not active yet.

## Run locally

Start the backend first from `../backend`:

```powershell
.\mvnw.cmd spring-boot:run
```

Then start the frontend from this folder:

```bash
npm install
npm run dev
```

Open the local address printed by Vite. Vite forwards relative `/api` requests to the backend during development. The application is not hosted online.

## Check changes

```bash
npm run build
npm run lint
```

The red Apex logos and local Outfit font live in `public/`. The original editable
logo package remains in `../branding/apex/`.
