# Phase 0: Test Coverage Gate on Current Stack (JUnit 4, JDK 8)

## Overview

This phase establishes the regression safety net **before** any upgrade work, on the existing stack: Spring Boot 2.0.0.M4, Gradle 4.1, JDK 8, JUnit 4. It adds a pragmatic unit suite over the engine/domain/repository/factory/service layers plus one full-context REST integration test (`@SpringBootTest` + `WebTestClient`) driven by a real local Git repo. The suite locks in current behavior and becomes the acceptance harness for the Boot 4.1.0 jump (Phases 1–2). This phase is a single green commit.

**Constraints verified against the codebase:**
- JUnit 4 only (Gradle 4.1 cannot run the JUnit Platform; `useJUnitPlatform()` landed in Gradle 4.6).
- Boot 2.0.0.M4 supports `@SpringBootTest(webEnvironment = RANDOM_PORT)` + `@AutoConfigureWebTestClient` + `@RunWith(SpringRunner.class)`.
- No new test dependencies: JGit (compile dep) is the integration fixture; `reactor-test` + `spring-boot-starter-test` already declared.
- Tests live in `org.calipsoide.featurevalves` to reach package-private members (`ExpositionLevel.ofPercentage`, `FeatureValve.matches/allows/getCardinality`, `Feature.execute`).

**Toolchain:** `sdk install java <latest-zulu-8>`; run `./gradlew build` with `JAVA_HOME` → Zulu 8. Gradle 4.1 will not run on the installed JDK 21/25.

---

## Task Details

### Environment

#### 0.1. Install Zulu 8 and verify baseline build

- **Prerequisites / Dependencies:** None.
- **Affected Files:** None (environment only).
- **Affected Symbols:** None.
- **Description:** Install the latest Zulu 8 JDK via sdkman (`sdk list java` → pick `8.0.4xx-zulu`), then run `./gradlew build` with `JAVA_HOME` pointing at it. Confirm the existing build and the single `LocalFeatureFileRepositoryTest` pass before adding tests.
- **Acceptance Criteria:**
  - [ ] `sdk list java` shows an installed Zulu 8; `java -version` reports `1.8.0_...` under that `JAVA_HOME`.
  - [ ] `./gradlew build` succeeds (BUILD SUCCESSFUL) on the untouched tree.

### Domain & Engine Unit Tests

#### 0.2. Add `ExpositionLevelTest`

- **Prerequisites / Dependencies:** None.
- **Affected Files:** `src/test/java/org/calipsoide/featurevalves/ExpositionLevelTest.java` (NEW)
- **Affected Symbols:** `ExpositionLevel.ofPercentage`, `ExpositionLevel.ZERO`, `ExpositionLevel.compareTo`
- **Description:** Boundary validation via the package-private `ofPercentage` factory (0 and 100 valid; −1 and 101 throw `IllegalArgumentException`), plus `ZERO` identity, equals/hashCode, `compareTo` ordering, and `toString` (`"0"`/`"100"`).
- **Acceptance Criteria:**
  - [ ] `ofPercentage(0)` and `ofPercentage(100)` construct; `ofPercentage(-1)` and `ofPercentage(101)` throw `IllegalArgumentException`.
  - [ ] `ExpositionLevel.ZERO` equals `ofPercentage(0)`; `compareTo` orders 0 < 50 < 100; `toString` returns the numeric string.

#### 0.3. Add `HashingEvaluatorTest`

- **Prerequisites / Dependencies:** None.
- **Affected Files:** `src/test/java/org/calipsoide/featurevalves/HashingEvaluatorTest.java` (NEW)
- **Affected Symbols:** `HashingEvaluator`, `Evaluator.evaluate`
- **Description:** Verify `evaluate` is deterministic and returns a level in `[0, 99]`; only tags whose `code` is in the configured `tagNames` are hashed (e.g. `eval: [name]` ignores an `age` tag); empty resulting values → `Optional.empty`. Expected value is `Math.abs(Joiner.on(":").join(values).hashCode()) % 100`.
- **Acceptance Criteria:**
  - [ ] Same check evaluated twice yields the same level; level is within `[0, 99]`.
  - [ ] Adding a non-`eval` tag does not change the result.
  - [ ] Check with no `eval`-listed tags → `Optional.empty`.

#### 0.4. Add `FeatureValveTest`

- **Prerequisites / Dependencies:** None.
- **Affected Files:** `src/test/java/org/calipsoide/featurevalves/FeatureValveTest.java` (NEW)
- **Affected Symbols:** `FeatureValve.matches`, `FeatureValve.allows`, `FeatureValve.getCardinality`
- **Description:** `matches(check)` is true only when the check tags are non-empty and contain all valve tags; `allows(level)` is `exposition > level` (a `value: 100` valve allows any level, `value: 0` allows none); `getCardinality()` returns the valve tag count.
- **Acceptance Criteria:**
  - [ ] `matches` returns true for a check containing all valve tags and false for an empty check or a missing tag.
  - [ ] `allows` returns true iff `exposition > level`; `value: 100` allows level 99, `value: 0` allows none.
  - [ ] `getCardinality` equals the number of valve tags.

#### 0.5. Add `FeatureTest`

- **Prerequisites / Dependencies:** None.
- **Affected Files:** `src/test/java/org/calipsoide/featurevalves/FeatureTest.java` (NEW)
- **Affected Symbols:** `Feature.execute`
- **Description:** With `active=false` → `false`. With `active=true`: the highest-cardinality matching valve is selected and its `allows` applied against the evaluator result; no matching valve → `false`; evaluator returning `Optional.empty` → `false`. Use a lambda implementing `Evaluator` (same package) returning a fixed `ExpositionLevel`.
- **Acceptance Criteria:**
  - [ ] Inactive feature returns `false` regardless of check.
  - [ ] Two matching valves of different cardinality select the higher one's `exposition` for the allow decision.
  - [ ] No matching valve or empty evaluator result → `false`.

### Identifier & Value Object Tests

#### 0.6. Add `ClientApplicationIdTest`, `FeatureIdTest`, `Tag`/`FeatureCheck` coverage

- **Prerequisites / Dependencies:** None.
- **Affected Files:**
  - `src/test/java/org/calipsoide/featurevalves/ClientApplicationIdTest.java` (NEW)
  - `src/test/java/org/calipsoide/featurevalves/FeatureIdTest.java` (NEW)
- **Affected Symbols:** `ClientApplicationId.of`, `FeatureId`, `Tag`, `FeatureCheck`
- **Description:** `ClientApplicationId.of("Foo")` normalizes to `"foo"` and equals `of("FOO")`; `FeatureId` lowercases the feature code (`"MY-FEATURE"` → `"my-feature"`) and equals/hashCode on both fields. Include Tag/FeatureCheck equality checks (they underpin valve matching) within these files.
- **Acceptance Criteria:**
  - [ ] `ClientApplicationId.of("Foo").toString()` is `"foo"`; equals/hashCode consistent with `of("FOO")`.
  - [ ] `FeatureId.of(appId, "MY-FEATURE").getFeatureCode()` is `"my-feature"`; different codes/apps are not equal.
  - [ ] `Tag` and `FeatureCheck` equals/hashCode match on same content and differ on changed content.

### Jackson POJO Tests (pre-Jackson-3 lock-in)

#### 0.7. Add `FeatureCheckRequestTest`

- **Prerequisites / Dependencies:** None.
- **Affected Files:** `src/test/java/org/calipsoide/featurevalves/FeatureCheckRequestTest.java` (NEW)
- **Affected Symbols:** `FeatureCheckRequest`
- **Description:** Use `new ObjectMapper()` (Jackson 2 on the Boot 2 classpath) to deserialize `{"a":"1","b":"2"}` into `FeatureCheckRequest` and assert the `tags` map. The single-arg `@JsonCreator(Map)` is a *delegate* creator (parameter names are not retained in the JDK 8 build), so the request body is a **flat tag map** — not the `{"tags":{...}}` shape claimed in the original SIA (see README correction #3). This locks the pre-upgrade binding contract and becomes a Jackson 3 probe.
- **Acceptance Criteria:**
  - [ ] Deserializing `{"a":"1","b":"2"}` yields a request whose `getTags()` equals `{a:1, b:2}`.

#### 0.8. Add `FeatureCheckResponseTest`

- **Prerequisites / Dependencies:** None.
- **Affected Files:** `src/test/java/org/calipsoide/featurevalves/FeatureCheckResponseTest.java` (NEW)
- **Affected Symbols:** `FeatureCheckResponse`
- **Description:** Use `new ObjectMapper()` to serialize `new FeatureCheckResponse(true)` and `new FeatureCheckResponse(false)`, asserting exact JSON `{"result":true}` / `{"result":false}`. This locks the HTTP response shape and is the primary Jackson 3 regression probe.
- **Acceptance Criteria:**
  - [ ] Serialization yields `{"result":true}` and `{"result":false}` byte-for-byte.

### Service & Factory Unit Tests

#### 0.9. Add `CachingFeatureServiceTest`

- **Prerequisites / Dependencies:** None.
- **Affected Files:** `src/test/java/org/calipsoide/featurevalves/CachingFeatureServiceTest.java` (NEW)
- **Affected Symbols:** `CachingFeatureService.findBy`, `CachingFeatureService.accept`
- **Description:** Construct directly with a TTL string (e.g. `"PT1H"`). `accept(feature)` then `findBy(id)` round-trips; unknown id → empty `Mono` (`justOrEmpty`); short TTL (`"PT1S"`) expiry verified with a generous `Thread.sleep(1200)` then `findBy` → empty.
- **Acceptance Criteria:**
  - [ ] Accepted feature is returned by `findBy` for the same `FeatureId`.
  - [ ] Unknown `FeatureId` → empty `Mono`.
  - [ ] After sleeping past a 1-second TTL, `findBy` returns empty.

#### 0.10. Add `YamlFileFeatureFactoryTest`

- **Prerequisites / Dependencies:** None.
- **Affected Files:** `src/test/java/org/calipsoide/featurevalves/YamlFileFeatureFactoryTest.java` (NEW)
- **Affected Symbols:** `YamlFileFeatureFactory.read`
- **Description:** Build `FeatureFile`s from the README-style YAML (active, `eval: [name]`, two valves with tags/values) and assert id, `active`, valve names, tag sets, and exposition values; missing `active` defaults `true`; missing `valves` → empty valve list; **malformed YAML throws synchronously on `read(...)` invocation** (the parse is eager — `assertThatThrownBy`, not StepVerifier).
- **Acceptance Criteria:**
  - [ ] README-style YAML yields a `Feature` with correct id, `active=true`, one `HashingEvaluator` over `[name]`, and expected valves.
  - [ ] YAML without `active` → `active=true`; YAML without `valves` → empty valve list.
  - [ ] Malformed YAML throws on invocation of `read(...)`.

### Repository Tests

#### 0.11. Extend `LocalFeatureFileRepositoryTest`

- **Prerequisites / Dependencies:** None.
- **Affected Files:** `src/test/java/org/calipsoide/featurevalves/LocalFeatureFileRepositoryTest.java` (MODIFY)
- **Affected Symbols:** `LocalFeatureFileRepository.loadAll`, `LocalFeatureFileRepository.filesOf`
- **Description:** Keep the two existing tests. Add: `.yml` and `.yaml` files in the same app folder are both picked up in filename order; a root-level non-directory file is skipped; a missing root path yields `Flux.error` (via `Files.newDirectoryStream` on a non-existent path).
- **Acceptance Criteria:**
  - [ ] A folder containing `a.yml` and `b.yaml` emits two `FeatureFile`s in filename order (`.yml` and `.yaml` both matched).
  - [ ] A file directly under the root path is not treated as an app directory.
  - [ ] `new LocalFeatureFileRepository(<nonexistent>)` produces `Flux.error` on subscribe.

### Integration Test

#### 0.12. Add `FeatureCheckControllerIntegrationTest`

- **Prerequisites / Dependencies:** None.
- **Affected Files:** `src/test/java/org/calipsoide/featurevalves/FeatureCheckControllerIntegrationTest.java` (NEW)
- **Affected Symbols:** `FeatureCheckController.check`, `GitRepoManager`, `GitFeatureFileRepository`, `FeatureLoader`, `CachingFeatureService`
- **Description:** Full-context test. Annotations: `@RunWith(SpringRunner.class)`, `@SpringBootTest(webEnvironment = RANDOM_PORT)`, `@AutoConfigureWebTestClient`, `@Autowired WebTestClient`, `@ClassRule TemporaryFolder`.
  - **Fixture (`@BeforeClass`):** JGit `init` a temp origin repo; create `features/app/always-on.yml` (`active: true`, `eval: [name]`, valve `{name: on, tags: {t: x}, value: 100}`) and `features/app/always-off.yml` (same but `value: 0`); stage all and commit with an explicit `PersonIdent` (author + committer).
  - **Properties:** set system properties in `@BeforeClass` (`feature.valves.test.remote`, `.clone`, `.clone/features`), referenced from `@TestPropertySource` via `${...}` placeholders:
    `features.git.remote.url`, `features.git.local.path=<clone>`, **`features.git.local.data=<clone>/features`**, `features.git.remote.branch=master`, `features.cache.ttl=PT1H`, `features.refresh.interval=PT1H`.
  - **Async wait:** bounded poll (no new deps) re-POSTing `/feature_valves/app/always-on/checks` until a non-404 status or ~15s timeout, since `FeatureLoader` populates the cache asynchronously at startup.
  - **Request API note:** on Boot 2.0.0.M4 use `.syncBody(...)` (`.bodyValue` exists only from Spring 5.1).
- **Acceptance Criteria:**
  - [ ] `POST /feature_valves/app/always-on/checks` with `{"name":"x","t":"x"}` → `200` with body `$.result == true`.
  - [ ] Same request against `always-off` → `200` with `$.result == false`.
  - [ ] Existing feature, non-matching tags (`{"other":"y"}`) → `200` with `$.result == false` (corrected SIA behavior — not 404).
  - [ ] Unknown feature (`app/nope`) → `404`.

### Phase Gate

#### 0.13. Run full build on JDK 8 and commit

- **Prerequisites / Dependencies:** Tasks 0.1–0.12.
- **Affected Files:** All Phase 0 test files (committed together) plus one `src/main` fix (see deviations below).
- **Affected Symbols:** None (test files) except the `LocalFeatureFileRepository.filesOf` ordering fix.
- **Description:** With `JAVA_HOME` → Zulu 8, run `./gradlew build`. All unit tests + integration test must pass. Commit the entire Phase 0 suite as one reviewable commit.
- **Deviations from the plan (approved):**
  - `LocalFeatureFileRepository.filesOf` was rewritten to a single-pass, order-preserving `flatMapSequential`; the old `zipWith` over async `flatMap` reads was racy and made the pre-existing `testLoadAll` intermittently fail.
  - The request-body contract is a flat tag map, not `{"tags":{...}}` (README correction #3).
- **Acceptance Criteria:**
  - [ ] `./gradlew build` on JDK 8: BUILD SUCCESSFUL with all new tests green.
  - [ ] Single commit containing only `src/test/**` changes plus the `filesOf` fix and plan-doc corrections.

---

## Verification Plan

1. `sdk install java <latest-zulu-8>`; `sdk use java 8.0.x-zulu`.
2. `./gradlew build` — BUILD SUCCESSFUL; all unit tests and the integration test pass.
3. Confirm the only `src/main` change is the `LocalFeatureFileRepository.filesOf` ordering fix; the rest is `src/test`.
