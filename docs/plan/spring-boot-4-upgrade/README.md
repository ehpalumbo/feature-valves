# Implementation Plan: Spring Boot 4.1.0 Upgrade with Pre-Upgrade Test Coverage Gate

## Metadata

- **Author:** opencode
- **Date:** 2026-08-11
- **Target Branch / Environment:** `feature/spring-boot-4-upgrade` → `master`
- **Phased Implementation:** Yes (3 phases)
- **Overall Risk Level:** **High**, mitigated by a mandatory Phase 0 test-coverage gate.

### References

- [Software Impact Analysis: Upgrade to Spring Boot 4.1.0](../../sia/spring-boot-4-upgrade.md)

---

## Executive Summary & Architecture

This plan converts the [SIA](../../sia/spring-boot-4-upgrade.md) into a phased, reviewable implementation. The strategy is **test-first on the current stack, then a direct big-bang upgrade**:

1. **Phase 0** adds a pragmatic JUnit 4 suite (unit + one full-context integration test) on the existing Boot 2.0.0.M4 / Gradle 4.1 / JDK 8 / JUnit 4 stack. This suite is the objective "no-regression" proof for the upgrade. Verified green via `./gradlew build` on JDK 8 (Zulu 8, installed via sdkman — not currently present).
2. **Phase 1** jumps directly to Boot 4.1.0 / Spring Framework 7 / Gradle 9.x / JDK 25: rewrite `build.gradle`, regenerate the wrapper, fix the removed `DataBufferUtils.read(AsynchronousFileChannel, …)` overload, refresh the CI workflow, and prove the app boots and the HTTP contract is unchanged.
3. **Phase 2** mechanically migrates the regression suite from JUnit 4 to JUnit Jupiter. **It lands in the same commit as Phase 1**: Boot 4.1 removes Spring's JUnit 4 support (`SpringRunner`) and drops JUnit 4 from the test starter, so a Phase 1-only commit cannot run the suite.

### Corrections to the SIA (baked into this plan)

1. **`features.git.local.data` override is required** in the integration test. `LocalFeatureFileRepository` binds `${features.git.local.data}` (`LocalFeatureFileRepository.java:32`), not `features.git.local.path`; the test must set it to `<cloneDir>/features` or the loader reads the wrong directory.
2. **"No matching tags" is not a 404.** `FeatureCheckController.check` returns 404 only when the feature is absent from the cache; when the feature exists but no valve matches, `Feature.execute` returns `false` → `200 {"result":false}` (`FeatureCheckController.java:47`, `Feature.java:42`).
3. **Request body is `{"tags":{...}}`, not a flat map.** `FeatureCheckRequest` binds a `tags` map via `@JsonCreator` (`FeatureCheckRequest.java:16`), contrary to the flat README example.
4. **Malformed YAML throws eagerly.** `YamlFileFeatureFactory.read` parses before returning the `Mono` (`YamlFileFeatureFactory.java:24`), so the test asserts a thrown exception on invocation, not an error Mono.

---

## Phase Index

- **[Phase 0: Test coverage gate on current stack (JUnit 4, JDK 8)](phases/phase0.md)** — Author 12 unit tests plus one full-context REST integration test; `./gradlew build` green on JDK 8; single commit.
- **[Phase 1: Boot 4.1.0 / Gradle 9 / JDK 25 upgrade](phases/phase1.md)** — Rewrite `build.gradle`, regenerate wrapper, fix `DataBufferUtils`, refresh CI, verify app boot + HTTP contract.
- **[Phase 2: JUnit 4 → Jupiter migration](phases/phase2.md)** — Mechanical migration of the whole test suite; full `./gradlew build` green on JDK 25 with zero assertion changes; lands with Phase 1.

---

## Configuration & Environment Updates

- **Environment Variables:** None.
- **Feature Flags:** None.
- **External Dependencies:** None new. Contingencies (resolved at runtime by the suite, not pre-emptively):
  - JGit 4.8.0 → 7.x if clone/pull breaks on JDK 25 (proven by the integration test).
  - Guava 23.0 → newer if any `NoSuchMethodError` appears (unlikely for `Cache`/`Immutable*`).
  - Explicit `org.yaml:snakeyaml` dependency if Boot 4 no longer brings it (Spring Framework 7 may move YAML support to `snakeyaml-engine`).
- **Local toolchain setup:**
  - Phase 0: `sdk install java <latest-zulu-8>`; `JAVA_HOME` → Zulu 8 (Gradle 4.1 cannot run on JDK 21/25).
  - Phase 1/2: `sdk install gradle <latest-9.x>` (to regenerate the wrapper); JDK → Zulu 25.0.4.

---

## Verification Plan (Whole Feature Verification)

### Automated Tests

- **Phase 0 (JDK 8):** `./gradlew build` — new unit suite + `FeatureCheckControllerIntegrationTest` green; existing `LocalFeatureFileRepositoryTest` still green.
- **Phase 2 (JDK 25):** `./gradlew build` — the same suite, migrated to Jupiter, passes **with no assertion changes** (only imports/mechanics), proving no behavioral regression across the Boot 4.1.0 jump.

### Manual Verification Steps

1. Phase 0: `sdk use java 8.0.x-zulu`; `./gradlew build`.
2. Phase 1: `sdk use java 25.0.4-zulu`; boot the app, confirm Netty starts and `features.*` bind.
3. `curl -X POST localhost:8080/feature_valves/test/test-feature/checks -H 'Content-Type: application/json' -d '{"tags":{}}'` → `{"result":false}` (feature absent → 404 shape matches pre-upgrade assertions).
4. Phase 2: full `./gradlew build` green on JDK 25.

### Definition of Done (DoD)

- [ ] Phase 0 suite committed and green on JDK 8.
- [ ] Phase 1 + Phase 2 committed together and green on JDK 25.
- [ ] No test-body assertion changes between stacks — only imports and JUnit mechanics.
- [ ] CI workflow (`main.yml`) runs the build on JDK 25.
- [ ] Documentation updated (this plan + SIA already committed).
