# Software Impact Analysis: Upgrade to Spring Boot 4.1.0 (with pre-upgrade test coverage gate)

## Metadata

- **Author:** opencode
- **Date:** 2026-08-11
- **Target Branch / Environment:** `master` (current working tree)
- **Overall Risk Level:** **High** — platform generation jump (Boot 2.0.0.M4 / Spring 5 → Boot 4.1.0 / Spring 7), mandatory toolchain changes, removed Spring API, JUnit 4 removal. **Mitigated by a new mandatory Phase 0 test-coverage gate** (unit + full-context integration) added to the current codebase before any upgrade work begins.

---

## Executive Summary

This is a feature-flag HTTP server that backs its state on a Git repository (WebFlux + Reactor + JGit + Guava + SnakeYAML) sitting on **Spring Boot 2.0.0.M4 (2017)**, Gradle 4.1, Java 8, and JUnit 4. Test coverage today is effectively one JUnit 4 test class (`LocalFeatureFileRepositoryTest`); the core evaluation engine and the entire HTTP surface are untested. Because this app is a POC, we will **not upgrade until a coverage safety net is in place**: a pragmatic suite of JUnit 4 unit tests over the domain/engine, repository, factory, and service layers, plus **one full-application-context REST integration test** (`@SpringBootTest` + `WebTestClient`) that clones a real local Git repo over a `file://` URL and exercises `POST /feature_valves/{application}/{feature}/checks` end-to-end. That suite locks in current behavior so the subsequent **direct big-bang jump to Boot 4.1.0** (JDK 25) can be validated by "tests stay green," not by inspection. Pre-upgrade tests are JUnit 4 (Gradle 4.1 cannot run the JUnit Platform — `useJUnitPlatform()` only landed in Gradle 4.6); they are mechanically migrated to JUnit Jupiter as part of the upgrade. Hard upgrade blockers remain the removed `DataBufferUtils.read(AsynchronousFileChannel, …)` overload, the JUnit 4 removal in Spring Framework 7, the Gradle 4.1 → 9.x lift, and the Java 25 toolchain; functional code elsewhere (Jackson annotations at `com.fasterxml.jackson.annotation`, Reactor API usage, `InitializingBean`, `@Value`) is Boot 4-compatible. No new test dependencies are required (JGit is already a compile dep and is reused as the test fixture).

---

## 1. Requirement & Problem Space Analysis

- **Objectives:**
  1. Establish sufficient test coverage on the current stack (Boot 2.0.0.M4) **before** upgrading.
  2. Upgrade the app to Spring Boot 4.1.0 on Java 25 so it builds and runs on Spring Framework 7 / Jakarta EE 11.
- **Success Criteria:**
  - New unit + integration suite is committed and green on the current stack (`./gradlew build` on JDK 8 as CI does today).
  - Integration test hits the real REST endpoint against the full context, using a real local Git repo as the feature source.
  - After the upgrade: same suite (migrated to JUnit Jupiter) passes unchanged on Boot 4.1.0 / JDK 25, proving no behavioral regression.
- **Key Constraints:** Minimal-change scope; direct big-bang jump (no staged 3.5 detour); target **Boot 4.1.0** (4.0 OSS EOL Dec 2026); **JDK 25** (Zulu 25.0.4 installed). Pragmatic coverage (no JaCoCo gate). Pre-upgrade tests must run on the existing Gradle 4.1 / JUnit 4 stack.

---

## 2. Context & Findings

**Current stack & build:** `build.gradle` pins `springBootVersion='2.0.0.M4'`, applies `org.springframework.boot` + `io.spring.dependency-management`, `sourceCompatibility=1.8`, `compile`/`testCompile` configs; Gradle wrapper 4.1; CI (`main.yml`) on JDK 8 with retired GitHub actions.

**Test infrastructure facts (verified):**
- Boot 2.0.0.M4 supports `@SpringBootTest(webEnvironment=RANDOM_PORT)`, `@AutoConfigureWebTestClient`, `@Autowired WebTestClient`, and `@RunWith(SpringRunner.class)` under JUnit 4 — the full-context integration test is fully supported on the current stack.
- Gradle 4.1 cannot execute JUnit 5 (`useJUnitPlatform()` added in Gradle 4.6) ⇒ pre-upgrade tests are **JUnit 4**, matching the one existing test; migration to Jupiter happens once during the upgrade (Gradle 9.x has native JUnit Platform support).
- `spring-boot-starter-test` (JUnit 4, AssertJ, Mockito, Hamcrest) + `reactor-test` (already declared) cover all assertion/web-testing needs; JGit (a compile dependency) doubles as the integration-test fixture — **no new dependencies required**.

**Boot 4.1.0 upgrade findings:** Spring Framework 7 baseline, Java 17–25/26, Gradle 8.14+/9.x, `compile`/`testCompile` gone, JUnit 4 removed (Jupiter 6 only), Jackson 3 default with **`jackson-annotations` unchanged at `com.fasterxml.jackson.annotation`**, `DataBufferUtils.read(AsynchronousFileChannel, …)` removed since Spring 5.1 (breaks `LocalFeatureFileRepository.java:74`), `io.spring.dependency-management` no longer provided by the Boot plugin, and the biggest Boot 4 breaking areas (Spring Security 7 CSRF, Undertow removal, Boot-internal package moves) do **not** apply.

**Coverage gaps (production classes, current state):** `ExpositionLevel`, `HashingEvaluator`, `FeatureValve`, `Feature`, `FeatureCheck`, `Tag`, `ClientApplicationId`, `FeatureId`, `FeatureCheckRequest`/`Response` (Jackson), `CachingFeatureService`, `YamlFileFeatureFactory`, `FeatureCheckController`, `FeatureLoader`, `GitRepoManager`/`GitFeatureFileRepository`, and the REST endpoint — all untested. Only `LocalFeatureFileRepository` has tests (2 methods).

---

## 3. Proposed Solution Approaches

### Approach A: Test-first, then direct big-bang upgrade (RECOMMENDED)

- **Description:** **Phase 0** — on the current Boot 2.0.0.M4 / JUnit 4 stack, add a pragmatic unit-test suite for the engine/domain layers, Jackson POJOs, service, and factory, extend `LocalFeatureFileRepositoryTest`, and add one full-context `@SpringBootTest`+`WebTestClient` integration test driven by a local `file://` Git repo. **Phase 1** — perform the Boot 4.1.0 jump (Gradle 9, JDK 25, `DataBufferUtils` fix, drop `io.spring.dependency-management`, CI refresh). **Phase 2** — mechanically migrate the whole test suite (now a regression harness) to JUnit Jupiter; all tests must pass green on Boot 4.1.0 without behavioral changes.
- **Pros:**
  - The suite is the objective, repeatable "no-regression" proof for a high-risk jump; failures localize exactly where behavior drifted (esp. Jackson 3 serialization, JGit on JDK 25).
  - Cheap to add now; JUnit 4 tests exercise the same production code the upgrade will touch.
  - No new test dependencies; fixture uses already-declared JGit.
- **Cons:**
  - Double-write cost: tests authored in JUnit 4 then migrated to Jupiter (mechanical, small).
  - Integration test depends on JGit clone behavior and needs a bounded wait/poll for async `FeatureLoader` population.
- **Risk Level & Complexity:** Medium. Complexity moderate (test authoring); upgrade risk substantially reduced by the pre-existing green suite.

### Approach B: Staged migration via Boot 3.5.x, tests added en route

- **Description:** Upgrade 2.0.M4 → 2.7 → 3.5 → 4.1 while adding tests at each stage.
- **Pros:** Each leap smaller; tests catch issues per stage.
- **Cons:** 3+ migration cycles and the same JUnit 4→5 rewrite anyway; far slower; overkill for a 22-class app.
- **Risk Level & Complexity:** Medium risk, High effort.

---

## 4. Recommended Way Forward & Design Decisions

- **Chosen Approach: A** — test-first on the current stack, then the direct jump, then suite migration. Rationale: the value of the upgrade is only provable against a green regression suite; authoring it now is cheap because JGit is already available and Boot 2.0.0.M4 fully supports the full-context WebTestClient pattern.
- **Security & Performance Impact:** No new dependencies, no auth/data changes. The integration test adds a JGit clone of a local repo in CI (fast, hermetic, no network). No production behavior changes in Phase 0. Upgrade phase improves the runtime stack (Netty/Jackson 3) with the suite guarding the HTTP contract.
- **Clarifying Questions Resolved:** Target **4.1.0**; path **direct big-bang**; JDK **25**; scope **minimal**; coverage gate **pragmatic (no JaCoCo)**; integration test **real local Git repo via `file://`**.
- **Remaining Open Questions:** None blocking. Contingencies (resolved at runtime, not now): bump JGit to 7.x if clone/pull breaks on JDK 25; bump Guava if any `NoSuchMethodError` appears (unlikely for `Cache`/`Immutable*`).

---

## 5. Affected Components

### Phase 0 — new/modified test files (current stack, JUnit 4)

| File / Component Path | Action | Description |
| :--- | :--- | :--- |
| `src/test/java/.../ExpositionLevelTest.java` | NEW | Boundary validation (0/100 valid, -1/101 throw), `ZERO`, equals/hashCode, `compareTo`, `toString`. |
| `src/test/java/.../HashingEvaluatorTest.java` | NEW | Deterministic hash in `[0,99]`; only `eval`-listed tags used; empty → `Optional.empty`. |
| `src/test/java/.../FeatureValveTest.java` | NEW | `matches` (all tags contained; empty check → false), `allows` (level < exposition), `getCardinality`. |
| `src/test/java/.../FeatureTest.java` | NEW | `active=false` → false; highest-cardinality valve selected; no match → false; evaluator empty → false. |
| `src/test/java/.../ClientApplicationIdTest.java` | NEW | Lowercasing, equals/hashCode. |
| `src/test/java/.../FeatureIdTest.java` | NEW | Lowercased feature code, equals/hashCode. |
| `src/test/java/.../FeatureCheckRequestTest.java` | NEW | Jackson deserialize JSON `{"tags":{...}}` → POJO (locks pre-Jackson-3 behavior). |
| `src/test/java/.../FeatureCheckResponseTest.java` | NEW | Jackson serialize `true` → `{"result":true}` (locks pre-Jackson-3 output; becomes a Jackson 3 probe). |
| `src/test/java/.../CachingFeatureServiceTest.java` | NEW | accept+`findBy` round-trip; miss → empty Mono; short-TTL expiry (construct with `@Value`-style ttl string). |
| `src/test/java/.../YamlFileFeatureFactoryTest.java` | NEW | Parse README-style YAML → expected valves/eval/active; default `active=true`; missing `valves` → empty; malformed YAML → error Mono. |
| `src/test/java/.../LocalFeatureFileRepositoryTest.java` | MODIFY | Keep existing 2 tests; add `.yml`/`.yaml` both picked up, filename ordering, non-directory entries skipped, missing root path → `Flux.error`. |
| `src/test/java/.../FeatureCheckControllerIntegrationTest.java` | NEW | Full-context REST test — see below. |

**Integration test design (`FeatureCheckControllerIntegrationTest`):**
- `@RunWith(SpringRunner.class) @SpringBootTest(webEnvironment = RANDOM_PORT) @AutoConfigureWebTestClient` with `@Autowired WebTestClient` (or `@LocalServerPort` + `bindToServer()`).
- `@TestPropertySource` overrides: `features.git.remote.url=file://<tmpRepo>`, `features.git.local.path=<tmpCloneDir>`, `features.git.remote.branch=master`, `features.cache.ttl=PT1H`, `features.refresh.interval=PT1H`.
- Fixture (`@BeforeClass`): JGit `init` a temp repo, commit deterministic feature YAMLs (e.g. a valve with `value: 100` ⇒ always ON, one with `value: 0` ⇒ always OFF, plus `eval: [name]`), set `PersonIdent` for the commit.
- Bounded polling loop (no new deps) until `CachingFeatureService` reports the seeded feature (async `FeatureLoader` population).
- Cases: matched tag → `200 {"result":true}`; zero-exposition valve → `200 {"result":false}`; no matching tags → `404`; unknown feature → `404`.

### Phase 1 — upgrade

| File / Component Path | Action | Description |
| :--- | :--- | :--- |
| `build.gradle` | MODIFY | Boot `4.1.0`, `sourceCompatibility=25`, drop `io.spring.dependency-management`, `compile`→`implementation` / `testCompile`→`testImplementation`; drop snapshot/milestone repos (GA is on Maven Central). |
| `gradle/wrapper/*` | MODIFY | Wrapper → Gradle 9.x (regenerate `gradlew`/`gradle-wrapper.jar`). |
| `.github/workflows/main.yml` | MODIFY | JDK 8→25; `actions/checkout@v2`→`@v4`; `actions/setup-java@v1`→`@v4`; `gradle-build-action@v1.3.3`→`gradle/actions/setup-gradle@v4`. |
| `src/main/java/.../LocalFeatureFileRepository.java` | MODIFY | `LocalFeatureFileRepository.java:74` — replace removed `DataBufferUtils.read(AsynchronousFileChannel, …)` with `readAsynchronousFileChannel(() -> channel, …)` (preferred `DataBufferUtils.read(path, …)`, which also closes the channel). |
| `FeatureCheckRequest/Response.java` | NO CHANGE (verify) | Annotations stay valid on Jackson 3; re-verify via the new Jackson tests + integration test. |
| All other `src/main` classes & `application.yaml` | NO CHANGE | Boot 4-compatible APIs; custom `features.*` props unchanged. |

### Phase 2 — test-suite migration to JUnit Jupiter (Boot 4.1.0)

| File / Component Path | Action | Description |
| :--- | :--- | :--- |
| All `src/test` classes | MODIFY | JUnit 4→Jupiter: `org.junit.Test`→`org.junit.jupiter.api.Test`, `@Before`→`@BeforeEach`, `@Rule TemporaryFolder`→`@TempDir`, `@RunWith(SpringRunner.class)`→`@ExtendWith(SpringExtension.class)` (or drop, Boot auto-detects). Keep StepVerifier/AssertJ/WebTestClient usage. Expect zero behavioral changes. |

### Database & Schema Changes

None — no persistence layer.

### API & Contract Changes

None — endpoint, payload, and response shape are unchanged and are now locked by tests.

### Backward Compatibility & Migration Strategy

No clients change. The pre-upgrade suite documents current behavior; post-upgrade the same suite (migrated) must stay green. No data migration.

### Rollback Plan

Revert the git changes (tests and/or upgrade) and redeploy the prior artifact. Tests are additive and safe to keep during rollback.

---

## 6. Verification & Testing Plan

### Automated Tests (Phase 0 — pre-upgrade, current stack)

- Run: `./gradlew build` (JDK 8, as CI does today) — all new unit tests + integration test green.
- New unit tests: engine/domain (`ExpositionLevel`, `HashingEvaluator`, `FeatureValve`, `Feature`, `FeatureId`, `ClientApplicationId`, `Tag`/`FeatureCheck`), Jackson POJO round-trips, `CachingFeatureService`, `YamlFileFeatureFactory`, extended `LocalFeatureFileRepositoryTest`.
- New integration test: `FeatureCheckControllerIntegrationTest` — full context, real `file://` Git repo, `WebTestClient` asserts 200/404 and exact JSON body.
- No new dependencies; no JaCoCo gate (pragmatic coverage per user decision).

### Automated Tests (Phase 2 — post-upgrade, Boot 4.1.0 / JDK 25)

- Run: `./gradlew build` (JAVA_HOME → Zulu 25.0.4; `useJUnitPlatform()` for the migrated suite).
- Entire suite (now JUnit Jupiter) must pass with **no test-body changes** — any edit needed flags a behavior change to review (Jackson 3 output, Netty startup, JGit on JDK 25).

### Manual Verification Steps (post-upgrade)

1. `sdk use java 25.0.4-zulu`; `./gradlew clean build`.
2. Boot the app; confirm Netty starts, profile + `features.*` bind, no `DataBufferUtils`/JUnit errors.
3. `curl -X POST localhost:8080/feature_valves/test/test-feature/checks -H 'Content-Type: application/json' -d '{}'` → `{"result":true|false}` matching the pre-upgrade integration-test assertion.
4. (Contingency) Exercise the Git path; bump JGit to 7.x if clone/pull fails on JDK 25.

### Potential Regression Risks

- **JGit 4.8 on JDK 25** — highest runtime risk; integration test (Phase 0) already exercises clone/pull, so the failure surfaces in CI immediately.
- **Jackson 3 defaults** — JSON formatting/null handling may differ; the locked Jackson round-trip tests + integration test catch any drift.
- **Async loader timing** — integration test uses a bounded poll; keep the timeout generous to avoid flakes in CI.
- **Gradle 9 / wrapper regeneration** and **Reactor/Netty version jump** — low impact for the small used API surface; covered by the green suite.
