<h1 align="center">DSH AI Hub</h1>

<p align="center">
  <strong>A lightweight control plane for internally managed Harness workbenches.</strong><br>
  It centralizes identity integration, model access, capability authorization, managed instructions, and audit records.
</p>

<p align="center"><sub>Independent community project built for managed DeepSeek Harness deployments. It is not affiliated with, sponsored by, or endorsed by DeepSeek AI.</sub></p>

English | [中文](README.md)

## What this repository is

DSH AI Hub is the control-plane companion to [Harness Enterprise](https://github.com/bloodtmai-cmyk/dsh-harness-enterprise). Together they provide a governed internal AI workbench entry point: the Hub maintains configuration and authorization, while Harness Enterprise provides the user-facing desktop runtime.

The Hub does not proxy normal model requests or MCP Tool calls. It is not an identity provider and does not replace LDAP, SSO, business systems, data sources, or their authorization rules.

The current code covers:

- Administrator sign-in, Session/CSRF protection, and administrative audit logs.
- Conversation audit ingestion and queries by identity, time, and status.
- Admission, publishing, requests, and grants for MCP/Tool, Skill, Bundle, and desktop plug-ins.
- Read-only, versioned enterprise `AGENTS.md` instructions applied to all managed users.
- Desktop release metadata and managed artifact delivery APIs.
- Model-access requests, administrator provisioning, and one-time Key retrieval.

Identity is verified by a replaceable Enterprise Gateway. The Gateway enforces current Hub grants at both `tools/list` and `tools/call`; downstream systems remain responsible for final data authorization.

## Companion project

[Harness Enterprise](https://github.com/bloodtmai-cmyk/dsh-harness-enterprise) consumes the policies, managed artifacts, model endpoint metadata, and release information exposed by this Hub. The repositories remain separate so organizations can replace either side without turning the Hub into a traffic proxy.

## Repository layout

```text
backend/         Spring Boot 3, Java 21, Spring Security, JPA, Flyway
frontend/        React, TypeScript, Vite administration interface
extension-host/  Controlled Cordis Provider/Connector host
artifacts/       Publicly reviewable example plug-ins and instructions
docs/            Architecture, API, and trust-boundary documentation
deploy/          Generic build and deployment examples
```

## Managed model access

Managed desktop users cannot enter model credentials manually. When an administrator approves access, the Hub stores these values as one authorization record:

- Provider identifier;
- OpenAI-compatible model gateway URL;
- API Key.

The Key is encrypted and can be retrieved only once. Provider and gateway metadata remain available while the authorization is active. Legacy grants without endpoint metadata fail closed and must be reissued. Model selection, budgets, and expiry remain the responsibility of the upstream Provider. LiteLLM is one possible gateway implementation, not a dependency.

Standard Harness still supports user-configured third-party models. This restriction applies only to the managed desktop mode.

## Run locally

Prerequisites: Java 21, Maven, Node.js 22+, and npm.

```bash
cd extension-host
npm ci
npm test
npm run build

cd ../frontend
npm ci
npm run build

cd ../backend
mvn spring-boot:run
```

The backend listens on `http://127.0.0.1:8090` by default and the local profile uses H2. The administration frontend can be run separately:

```bash
cd frontend
npm run dev
```

See [.env.example](.env.example), the [deployment examples](deploy/), and the [architecture documentation](docs/architecture.md) for PostgreSQL and reverse-proxy configuration. Production deployments should use trusted HTTPS endpoints and a secret-management system.

## Verification

```bash
cd backend && mvn test
cd ../frontend && npm run build
cd ../extension-host && npm test && npm run build
cd .. && node --test artifacts/*/tests/*.test.mjs
./scripts/verify-community-sanitization.sh
```

This repository publishes source code only. It does not provide macOS or Windows installers for Harness Enterprise.

## Documentation and maintenance

- [Architecture and trust boundaries](docs/architecture.md)
- [API integration](docs/api.md)
- [Contributing](CONTRIBUTING.md)
- [Security policy](SECURITY.md)

The project is maintained by `clanie`. Report security issues privately by following [SECURITY.md](SECURITY.md).

## License

Licensed under the [MIT License](LICENSE). Third-party dependencies and example artifacts retain their own licenses and service terms, documented in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
