# Standard Java package layout implementation plan

> **For agentic workers:** Execute this focused package refactor inline.

**Goal:** Organize the application by conventional Spring layers without changing its HTTP, Kafka, security, or persistence behavior.

**Architecture:** Keep com.example.fundorders as the application root. Move REST endpoints to controller, processing and publishing to service, persistence access to repository, data types to model, and Spring wiring to config. Keep adapters in kafka and filter, and errors/advice in exception. Move tests alongside their corresponding layer.

**Tech Stack:** Existing Java 21, Spring Boot, JPA, Kafka, Testcontainers, Maven and JaCoCo.

**Spec:** User approved source packaging into controller/service/repository/model/config in chat.

## Constraints and review focus

- Preserve the executable JAR, REST contracts, JSON field shapes, Kafka listener bean/factory names, transactions, entity/table mappings, and security behavior.
- Extract the existing EventPage response record into model; expose only the existing entity conversion/hash methods needed across the new package boundary.
- Keep historical checkpoint records intact; update current README links and the generated coverage report.
- Verify source directory/package alignment, old application imports, focused compilation, complete local tests, and independent review.

### Task 1: Reorganize application and tests

- [ ] Move application classes and corresponding tests, updating declarations and imports.
- [ ] Update the README package map and security extension link.
- [ ] Run clean verify and git diff --check; inspect remaining references and package alignment.
- [ ] Obtain independent review and resolve Important/Critical findings.
- [ ] Record verification and refresh generated coverage before committing final records.

Release follows the user's checkpoint workflow: push branch, merge --no-ff into synchronized main, verify merged main, push main, and compare SHAs.
