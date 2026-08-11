# Phase 1: Boot 4.1.0 / Gradle 9 / JDK 25 Upgrade

## Overview

This phase performs the direct big-bang jump from Boot 2.0.0.M4 / Spring 5 / Gradle 4.1 / Java 8 to **Spring Boot 4.1.0 / Spring Framework 7 / Gradle 9.x / Java 25**, as decided in the SIA. It rewrites the build, regenerates the Gradle wrapper, fixes the one removed Spring API, and refreshes CI. Functional code elsewhere (Jackson annotations at `com.fasterxml.jackson.annotation`, Reactor usage, `InitializingBean`, `@Value`, `features.*` properties) is Boot 4-compatible and stays unchanged.

**Landing note:** Phase 1 and Phase 2 are a **single atomic commit**. Boot 4.1 removes Spring's JUnit 4 support (`SpringRunner`) and drops JUnit 4 from `spring-boot-starter-test`, so a Phase 1-only commit compiles but cannot run the existing suite. A Phase 1-only verification is: app boots + `compileJava`/`compileTestJava` green + manual curl; the full green gate is Phase 2.

**Toolchain:** `sdk install gradle <latest-9.x>`; JDK 25.0.4-zulu. The current wrapper (4.1) cannot run on JDK 25, so the wrapper is regenerated with a standalone Gradle 9.

---

## Task Details

### Build

#### 1.1. Rewrite `build.gradle` for Boot 4.1.0

- **Prerequisites / Dependencies:** None.
- **Affected Files:** `build.gradle` (MODIFY)
- **Affected Symbols:** Spring Boot Gradle plugin, dependency management, `sourceCompatibility`
- **Description:** Replace the `buildscript` block with a plugins block: `id 'java'`, `id 'org.springframework.boot' version '4.1.0'`. Remove `io.spring.dependency-management`; manage versions via `implementation platform('org.springframework.boot:spring-boot-dependencies:4.1.0')`. Replace `compile` → `implementation` and `testCompile` → `testImplementation` (Gradle 9 removed those configurations). Set `sourceCompatibility = '25'` and `targetCompatibility = '25'`. Remove the snapshot/milestone repositories (Boot 4 GA is on Maven Central). Add `tasks.named('test') { useJUnitPlatform() }`.
- **Acceptance Criteria:**
  - [ ] `./gradlew compileJava` on JDK 25 succeeds with no unresolved dependencies.
  - [ ] `io.spring.dependency-management` no longer appears anywhere in `build.gradle`.

#### 1.2. Regenerate the Gradle wrapper to 9.x

- **Prerequisites / Dependencies:** Task 1.1 (not strictly required, but keeps versions consistent).
- **Affected Files:**
  - `gradle/wrapper/gradle-wrapper.properties` (MODIFY)
  - `gradle/wrapper/gradle-wrapper.jar` (REGENERATE)
  - `gradlew`, `gradlew.bat` (REGENERATE)
- **Affected Symbols:** None.
- **Description:** With JDK 25 active, run `gradle wrapper --gradle-version <latest-9.x>` using the sdkman-installed Gradle 9 (the 4.1 wrapper cannot bootstrap on JDK 25). Verify `gradle-wrapper.properties` points at `gradle-<9.x>-bin.zip`.
- **Acceptance Criteria:**
  - [ ] `./gradlew --version` reports Gradle 9.x on JDK 25.
  - [ ] `gradle-wrapper.properties` `distributionUrl` references a Gradle 9.x distribution.

#### 1.3. Fix removed `DataBufferUtils.read(AsynchronousFileChannel, …)` overload

- **Prerequisites / Dependencies:** None.
- **Affected Files:** `src/main/java/org/calipsoide/featurevalves/LocalFeatureFileRepository.java` (MODIFY, line 74)
- **Affected Symbols:** `LocalFeatureFileRepository.read`
- **Description:** Replace `DataBufferUtils.read(channel, new DefaultDataBufferFactory(), BUFFER_SIZE)` with `DataBufferUtils.read(path, new DefaultDataBufferFactory(), BUFFER_SIZE)`. The `path` overload (available since Spring 5.1) opens and closes the channel itself, so remove the `AsynchronousFileChannel` and its `open(...)` static import.
- **Acceptance Criteria:**
  - [ ] `./gradlew compileJava` succeeds; the `AsynchronousFileChannel` import is gone.
  - [ ] `LocalFeatureFileRepositoryTest` (Phase 2) still reads files correctly.

### CI

#### 1.4. Refresh `.github/workflows/main.yml`

- **Prerequisites / Dependencies:** None.
- **Affected Files:** `.github/workflows/main.yml` (MODIFY)
- **Affected Symbols:** None.
- **Description:** Replace the retired actions and JDK 8: `actions/checkout@v4`, `actions/setup-java@v4` with `distribution: zulu`, `java-version: '25'`, and `gradle/actions/setup-gradle@v4`; run `./gradlew build` instead of the `gradle-build-action` wrapper.
- **Acceptance Criteria:**
  - [ ] Workflow uses only `@v4` actions and JDK 25; no `v1`/`v2` action references remain.

### Verification & Contingencies

#### 1.5. Verify app boots on JDK 25 and contract is unchanged

- **Prerequisites / Dependencies:** Tasks 1.1–1.4.
- **Affected Files:** None.
- **Affected Symbols:** `Application`, `FeatureCheckController`
- **Description:** With JDK 25: `./gradlew compileJava compileTestJava` green; boot the app and confirm Netty starts, `features.*` bind, and no `DataBufferUtils`/JUnit errors. `curl -X POST localhost:8080/feature_valves/test/test-feature/checks -H 'Content-Type: application/json' -d '{"tags":{}}'` returns the 404 shape (unknown feature), matching the Phase 0 integration-test expectation.
- **Acceptance Criteria:**
  - [ ] App boots without exceptions on JDK 25.
  - [ ] Curl smoke test returns the expected 404 for an unknown feature.

#### 1.6. Apply contingency dependency fixes if the suite fails

- **Prerequisites / Dependencies:** Phase 2 test run.
- **Affected Files:** `build.gradle` (MODIFY, conditional)
- **Affected Symbols:** None.
- **Description:** Only if the Phase 2 build/test run reveals them: (a) add explicit `implementation 'org.yaml:snakeyaml:2.x'` if `YamlFileFeatureFactory`'s `org.yaml.snakeyaml.Yaml` import is no longer on the classpath (Spring Framework 7 may move to `snakeyaml-engine`); (b) bump `org.eclipse.jgit:org.eclipse.jgit` to 7.x if the integration test's clone/pull fails on JDK 25; (c) bump `guava` if a `NoSuchMethodError` surfaces.
- **Acceptance Criteria:**
  - [ ] Contingency bumps applied only when a real failure is observed, and the full suite passes afterward.

---

## Verification Plan

1. `sdk install gradle <latest-9.x>`; `sdk use java 25.0.4-zulu`.
2. `./gradlew compileJava compileTestJava` — green.
3. Boot the app; confirm startup and run the manual curl smoke test (Task 1.5).
4. Full green gate is reached in Phase 2 (same commit): `./gradlew build`.
