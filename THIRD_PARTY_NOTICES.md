# Third-Party Notices

DSH AI Hub is licensed under the [MIT License](LICENSE). Third-party packages, services, and referenced projects keep their own licenses and terms.

## Direct runtime dependencies

| Component | License |
| --- | --- |
| Spring Boot, Spring Framework, Spring Security | Apache-2.0 |
| Flyway Community | Apache-2.0 |
| PostgreSQL JDBC Driver | BSD-2-Clause |
| H2 Database Engine | MPL-2.0 or EPL-1.0 |
| SnakeYAML | Apache-2.0 |
| React and React DOM | MIT |
| Lucide React | ISC |
| Cordis | MIT |

Build-only dependencies such as TypeScript, Vite, esbuild, and tsx remain under their respective licenses. Exact npm versions are recorded in `frontend/package-lock.json` and `extension-host/package-lock.json`; exact Maven versions are resolved from `backend/pom.xml` and its Spring Boot dependency management.

## Example artifacts

| Artifact | Upstream or service | Terms |
| --- | --- | --- |
| Agent Reach | [Panniantong/Agent-Reach](https://github.com/Panniantong/Agent-Reach) | MIT; adapted as a constrained managed plug-in. Exa MCP and Jina Reader are network services with separate service terms. |
| Vision Toolkit | [Anionex/dsh-vision-toolkit](https://github.com/Anionex/dsh-vision-toolkit) | MIT; workflow and implementation were reduced to a managed workspace-only surface. |
| WeCom Office | [WecomTeam/wecom-cli](https://github.com/WecomTeam/wecom-cli) | Official CLI is MIT; use of WeCom APIs remains subject to WeCom account and service terms. |

The corresponding MIT copyright notices are preserved next to each artifact in `UPSTREAM_LICENSE.md`.

## SBOM

Community CI generates CycloneDX SBOM files for the Maven and npm dependency trees. Before a tagged source release, regenerate them from the final lockfiles and review any dependency whose declared license is missing or non-standard.
