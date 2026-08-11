# Phase 2: Test-Suite Migration to JUnit Jupiter

## Overview

Boot 4.1.0 ships on Spring Framework 7, which removed JUnit 4 support (`org.springframework.test.context.junit4.SpringRunner`) and whose `spring-boot-starter-test` brings JUnit Jupiter 6. This phase mechanically migrates the Phase 0 regression suite from JUnit 4 to Jupiter so it runs under Gradle 9's JUnit Platform. **The goal is zero behavioral change**: assertions, StepVerifier/AssertJ/WebTestClient usage, and expectations must be identical; only imports and JUnit mechanics change. This phase lands in the same atomic commit as Phase 1 — it is the full green gate on Boot 4.1.0 / JDK 25.

---

## Task Details

### Mechanical Migration

#### 2.1. Migrate all unit test classes to JUnit Jupiter

- **Prerequisites / Dependencies:** Phase 1 (Boot 4.1.0 build).
- **Affected Files:** every test class authored in Phase 0:
  - `src/test/java/org/calipsoide/featurevalves/ExpositionLevelTest.java`
  - `src/test/java/org/calipsoide/featurevalves/HashingEvaluatorTest.java`
  - `src/test/java/org/calipsoide/featurevalves/FeatureValveTest.java`
  - `src/test/java/org/calipsoide/featurevalves/FeatureTest.java`
  - `src/test/java/org/calipsoide/featurevalves/ClientApplicationIdTest.java`
  - `src/test/java/org/calipsoide/featurevalves/FeatureIdTest.java`
  - `src/test/java/org/calipsoide/featurevalves/FeatureCheckRequestTest.java`
  - `src/test/java/org/calipsoide/featurevalves/FeatureCheckResponseTest.java`
  - `src/test/java/org/calipsoide/featurevalves/CachingFeatureServiceTest.java`
  - `src/test/java/org/calipsoide/featurevalves/YamlFileFeatureFactoryTest.java`
  - `src/test/java/org/calipsoide/featurevalves/LocalFeatureFileRepositoryTest.java`
- **Affected Symbols:** test methods only.
- **Description:** Apply the mechanical mapping per class:
  - `org.junit.Test` → `org.junit.jupiter.api.Test`
  - `org.junit.Before` → `org.junit.jupiter.api.BeforeEach`
  - `@Rule public TemporaryFolder` → `@TempDir Path` (method or instance field) with `Path`-based setup; Guava `Files.newWriter` → `java.nio.file.Files.write` where needed.
- **Acceptance Criteria:**
  - [ ] No test class imports `org.junit.Test`, `org.junit.Before`, or `org.junit.Rule` anymore.
  - [ ] No assertion bodies changed.

#### 2.2. Migrate `FeatureCheckControllerIntegrationTest`

- **Prerequisites / Dependencies:** Phase 1.
- **Affected Files:** `src/test/java/org/calipsoide/featurevalves/FeatureCheckControllerIntegrationTest.java` (MODIFY)
- **Affected Symbols:** test lifecycle, `WebTestClient` request API.
- **Description:** Drop `@RunWith(SpringRunner.class)` (Boot auto-detects `@SpringBootTest`). Replace `@ClassRule TemporaryFolder` + `@BeforeClass` with `static @TempDir Path` + `@BeforeAll` (still set `feature.valves.test.*` system properties before the context loads). Replace `.syncBody(...)` with `.bodyValue(...)` (available since Spring 5.1).
- **Acceptance Criteria:**
  - [ ] The four assertions (always-on `true`, always-off `false`, no-match `false`, unknown `404`) are byte-identical to Phase 0.
  - [ ] Test runs under the JUnit Platform on JDK 25.

#### 2.3. Update Jackson mapper imports if needed

- **Prerequisites / Dependencies:** Phase 1.
- **Affected Files:**
  - `src/test/java/org/calipsoide/featurevalves/FeatureCheckRequestTest.java`
  - `src/test/java/org/calipsoide/featurevalves/FeatureCheckResponseTest.java`
- **Affected Symbols:** `ObjectMapper` import only.
- **Description:** If Boot 4.1.0's Jackson 3 moved the databind package, change `import com.fasterxml.jackson.databind.ObjectMapper` → `import tools.jackson.databind.ObjectMapper`. The assertion strings (`{"result":true}` / `{"result":false}` / tags map) must not change — this is the Jackson 3 regression probe.
- **Acceptance Criteria:**
  - [ ] Both tests compile and the exact JSON assertions still pass on Jackson 3.
  - [ ] If `com.fasterxml.jackson.databind.ObjectMapper` resolves without change, no edit is made.

### Phase Gate

#### 2.4. Full build green on JDK 25 and commit (with Phase 1)

- **Prerequisites / Dependencies:** Tasks 2.1–2.3 and all Phase 1 tasks.
- **Affected Files:** Phase 1 + Phase 2 changes (single commit).
- **Affected Symbols:** None.
- **Description:** With JDK 25: `./gradlew build`. The entire migrated suite must pass with **no assertion changes** compared with Phase 0. Commit Phase 1 + Phase 2 together as one atomic upgrade commit.
- **Acceptance Criteria:**
  - [ ] `./gradlew build` on JDK 25: BUILD SUCCESSFUL, all migrated tests green.
  - [ ] A diff of Phase 0 tests vs. the migrated tests shows only import/lifecycle/API-mechanical changes, never assertion changes.
  - [ ] Single commit containing `build.gradle`, `gradle/wrapper/*`, `gradlew*`, `.github/workflows/main.yml`, `LocalFeatureFileRepository.java`, and all `src/test/**` changes.

---

## Verification Plan

1. `sdk use java 25.0.4-zulu`; `./gradlew build` — BUILD SUCCESSFUL.
2. Confirm the CI workflow (Phase 1) would produce the same result on `ubuntu-latest` with JDK 25.
3. Spot-check that `FeatureCheckControllerIntegrationTest` still exercises the real `file://` Git clone path end-to-end.
4. Review the test diff to confirm zero behavioral/assertion changes.
