# Apex frontend

React, TypeScript, Vite, and Material UI provide the local president interface.
The current screen provides president login, logout, and password changes.
Create the local account using [Stage 8 instructions](../docs/stage-8.md).
Member records will be implemented in Stage 9.

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
