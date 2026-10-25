# Third-Party Notices

TestHub is distributed under the GNU Affero General Public License v3.0 (see `LICENSE`).
This file records the upstream projects we build against or integrate with.

## MeterSphere

- Project: MeterSphere (`metersphere/metersphere`, branch `v3.x`)
- License: **GNU GPL v3 with additional terms** (see the upstream `LICENSE`)
- How TestHub uses it: the MeterSphere backend is built from the source provided by the
  project owner and runs as a separate service. TestHub integrates through MeterSphere's
  HTTP APIs (functional case detail, case comment, test-plan functional case run).
  No MeterSphere source code is copied into the TestHub source tree.
- If TestHub later embeds MeterSphere code as a Maven module, the combined work must be
  distributed under GPLv3-compatible terms and the additional terms (logos/copyright,
  contributor terms) must be preserved.

## Sonic

- `sonic-driver-core` — **Apache License 2.0**. Used as a Maven dependency
  (`io.github.soniccloudorg:sonic-driver-core:1.1.33`) by `device-agent` in `mode=sonic`.
- `sonic-agent`, `sonic-server` — **AGPL-3.0**. Not linked or copied; only their device
  management design informed the adapter. Any future linking requires AGPL compliance
  (including the network-use clause).

## Runtime dependencies (selected)

| Component | License |
|---|---|
| Spring Boot / Spring Framework / Spring Data / Spring Kafka | Apache-2.0 |
| H2 Database | MPL-2.0 / EPL-1.0 |
| MySQL Connector/J | GPLv2 with FOSS exception |
| Redis (server) | RSALv2 / SSPLv1 (server), used as an external service |
| MinIO (server) | AGPL-3.0, used as an external service |
| Apache Kafka | Apache-2.0 |
| MinIO Java SDK | Apache-2.0 |
| React / React DOM | MIT |
| Vite / TypeScript | MIT / Apache-2.0 |
| FastAPI / Pydantic / httpx / uvicorn | MIT / BSD-3-Clause |
| Apache Maven plugins | Apache-2.0 |

External services (MySQL, Redis, MinIO, Kafka) are used over the network and are not
redistributed as part of this repository.
