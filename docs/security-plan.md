# Security plan

Phase 1 deliberately provides a deny-by-default security boundary without implementing authentication. Health endpoints are public, development-only API documentation is profile-gated, sessions are stateless, form login and HTTP Basic are disabled, and CORS accepts only the configured frontend origin.

## Phase 2 and later controls

- Authenticate with short-lived signed JWT access tokens and a carefully designed refresh-token lifecycle.
- Apply role-based authorization at request and application-service boundaries; restaurant scope must be checked independently of role.
- Hash passwords with an adaptive algorithm such as Argon2id or bcrypt and an appropriate work factor. Never store plaintext passwords.
- Validate request structure and domain invariants server-side, even when the frontend also validates forms with Zod and React Hook Form.
- Keep CORS origins explicit per environment and never use credentialed wildcard origins.
- Add rate limits to authentication and abuse-sensitive endpoints using infrastructure appropriate to the deployment model.
- Record security-relevant actions in append-focused audit logs without leaking secrets or unnecessary personal data.
- Supply database passwords, signing keys, and third-party credentials through environment-specific secret management. Rotate them and keep them out of source control and logs.
- Require TLS, secure headers, least-privilege database users, restricted Actuator exposure, production-safe logging, and dependency scanning before deployment.

JWT key management, logout semantics, token revocation, account recovery, multi-restaurant authorization, and privacy retention rules must be decided before authentication is considered production-ready.
