# API contract

All endpoints live below `/api/v1`. Success responses are `{ "data": ... }`;
errors are `{ "error": { "code", "message", "details?" } }`.

## Client

| Method | Route | Auth | Description |
| --- | --- | --- | --- |
| `POST` | `/auth/email/request` | public | create a challenge and deliver a six-digit email code |
| `POST` | `/auth/email/verify` | public | consume the one-time code and register the installation |
| `POST` | `/auth/login` | public | optional password login for provisioned accounts |
| `POST` | `/auth/refresh` | refresh token + installation ID | device-bound single-use rotation |
| `GET` | `/client/bootstrap` | access bearer | account, services, visible servers, Guardian, release and notices |
| `GET` | `/client/services/:id/config?nodeId=…` | access bearer | configuration for one eligible selected node |

## Admin

Admin routes use an HTTP-only, secure, strict SameSite session cookie. Only an
`ADMIN` can create privileged entities; `SUPPORT` is read-only.

| Method | Route | Description |
| --- | --- | --- |
| `POST/DELETE` | `/admin/session` | open or close an admin session |
| `GET/POST/PATCH` | `/admin/users` | list, create, suspend or change user access |
| `GET/POST/PATCH` | `/admin/services` | list, issue and change service limits/status |
| `GET/POST/PATCH` | `/admin/nodes` | list, create and change encrypted VPN nodes |
| `GET/POST` | `/admin/plans` | list or create plans |
| `GET/POST` | `/admin/regions` | list or create server regions |
| `POST` | `/admin/service-nodes` | assign a node to a service |
| `POST` | `/admin/settings` | upsert a JSON client setting |
| `POST` | `/admin/notifications` | compose and publish targeted notifications |
| `POST` | `/admin/releases` | publish a signed release manifest |

Secrets are excluded from list responses. Node payloads are encrypted using
AES-256-GCM and only the selected node is decrypted for an eligible,
non-expired service. Mutating admin calls also require a same-origin request.
Admin cookies are revalidated against the account's current database role and
status on every protected request, so suspensions and demotions take effect
without waiting for the cookie to expire.
