---
type: architecture
title: "Feature Valves System Overview"
description: "How Feature Valves works as a Git-backed feature flag HTTP server, its component boundaries, and the technology stack it is built on."
tags:
  - "architecture"
  - "feature-flags"
  - "spring-webflux"
  - "eventual-consistency"
timestamp: "2026-08-13T00:00:00Z"
related:
  - "[Reactive Data Flow](reactive-data-flow.md)"
  - "[Feature Valve](../concepts/feature-valve.md)"
  - "src/main/java/org/calipsoide/featurevalves/application"
  - "src/main/java/org/calipsoide/featurevalves/domain"
  - "src/main/java/org/calipsoide/featurevalves/web"
  - "src/main/java/org/calipsoide/featurevalves/infra"
  - "src/main/resources/application.yaml"
resource:
  - "build.gradle"
  - "src/main/java/org/calipsoide/featurevalves"
---

# Feature Valves System Overview

## Purpose

Feature Valves is a standalone feature flag service. Feature definitions live as YAML files in a Git repository, are pulled down and parsed periodically, cached, and then evaluated on every incoming HTTP request. It is designed for incremental rollouts: each flag exposes a feature to a deterministic fraction of matching traffic — hence "valve". See the [Feature Valve concept](../concepts/feature-valve.md) for the evaluation semantics.

## Technology Stack

- **Spring Boot 4.1.0 (spring-webflux, Spring Framework 7)** and **Project Reactor** — the application and request layer are fully reactive (`Mono`/`Flux`).
- **Jackson 3** — JSON binding via `tools.jackson` on the Spring Framework 7 baseline.
- **Caffeine** — the in-memory store that holds parsed features with a configurable time-to-live, exposed to the application layer as a plain `ConcurrentMap` (the live `asMap()` view of a Caffeine cache) rather than through Spring's cache abstraction.
- **Lombok** — generates getters/`toString` for the behavior-bearing classes.
- **JGit** — cloning and refreshing the local mirror of the remote repository that stores feature definition files (shallow clone by default, refreshed by fetch + hard reset).
- **SnakeYAML** — parsing feature definition files into the domain model.
- **Java 25** — source and target compatibility.
- **Gradle 9.7 + JUnit Jupiter** — build and the upgraded test suite (see the regression-gated migration below).

## Components and Boundaries

The application is split into two broad, deliberately decoupled paths:

- **Load / refresh pipeline** — `FeatureLoader` exposes a single `load()` that reloads all files, delegates to `GitFeatureFileRepository` (which refreshes the local clone via `GitRepoManager` — fetch + hard reset to the tracked branch's remote tip — then reads files via `LocalFeatureFileRepository`), converts each `FeatureFile` into a `Feature` via `YamlFileFeatureFactory`, and pushes the result into `InMemoryFeatureService`, which evicts features no longer seen on load completion. `FeatureRefreshScheduler` (infra) drives it: one initial tick blocks startup (retrying with exponential backoff until it succeeds, so the app is only ready once the cache is populated and reconciled with the repository — `load()` only completes after the eviction runs) and then one tick per completed tick plus a fixed delay. `GitRepoManager` clones once at startup (shallow by default) and refreshes the tracked branch on every tick.
- **Request evaluation path** — `FeatureCheckController` accepts a feature check request, resolves a cached `Feature`, and lets the `Feature` domain object execute the check against a set of request tags.

## Package / Layer Structure

The source tree is organized into architectural layers under the `org.calipsoide.featurevalves` package, with `Application.java` kept at the root as the composition root:

- **`domain/`** — the pure domain model and evaluation engine: value objects (`ClientApplicationId`, `FeatureId`, `Tag`, `ExpositionLevel`), the `Feature`/`FeatureValve` semantics, and the `Evaluator`/`HashingEvaluator`. This layer carries no framework or infrastructure dependencies; everything else depends on it.
- **`application/`** — application services and port definitions that orchestrate the load and evaluation flows, including the `FeatureFileRepository` / `FeatureReader` interfaces and `FeatureLoader`/`InMemoryFeatureService` implementations.
- **`web/`** — the reactive HTTP boundary: `FeatureCheckController` plus its request/response DTOs.
- **`infra/`** — infrastructure adapters implementing the ports: `git/` (repository handling), `yaml/` (feature definition parsing via `YamlFileFeatureFactory`), `caching/` (Caffeine cache bean config), and `scheduling/` (`FeatureRefreshScheduler`, which owns the refresh cadence and lifecycle).

This layering keeps the domain independent of transport and persistence details and gives each layer a single, clearly named package to search. The two flows described above map onto these layers: the refresh pipeline runs through `application` + `infra`, while the request path runs `web` → `application` → `domain`.

## Design Highlights

- **Eventual consistency, no lock.** The feature state is refreshed asynchronously on a timer; the request path only ever reads the in-memory cache, never touching Git or disk.
- **Reactive end to end.** File reads use the reactive `DataBufferUtils.read(Path, …)` overload (the `AsynchronousFileChannel` variant was removed in Spring 5.1) with reactive buffers; the remaining blocking filesystem and git operations are offloaded to a bounded-elastic scheduler, so even the loading path avoids blocking threads.
- **Pluggable sourcing.** `FeatureFileRepository` is an interface; the Git-backed implementation merely decorates the local filesystem one with a git update step. That seam was added later to allow alternate file providers.
- **Single-lifecycle git sourcing.** `GitRepoManager` clones the remote once at startup, resolving the remote's default branch via `ls-remote` when no override is configured (an explicit `features.git.remote.branch` override is applied at clone time instead), then only refreshes that branch on every tick (and closes the repository on shutdown). The default-branch probe is required because the branch name must be known to narrow the clone via `branchesToClone` — JGit clones every remote branch otherwise. Each refresh fetches only the tracked branch via a branch-scoped refspec and hard-resets the working tree to its remote tip, so multi-branch repositories transfer just that branch's tip. The initial clone is **shallow by default** (`features.git.clone.depth`, default `1`) so only the latest commit is transferred — a read-only mirror that never needs history or a merge base, so shallow clones work end to end. A configured branch override is re-checked at each startup on an existing clone and the local checkout is switched when it changed.
- **Regression-gated migration.** The jump from Boot 2.0 (2017) to Boot 4.1 / Java 25 was staged behind a pre-upgrade test suite — unit tests over the domain/engine and a full-context WebTestClient REST integration test driven by a real local JGit repo. The same suite, migrated from JUnit 4 to Jupiter, proves the upgrade changed no behavior.

## Configuration

Behavior is controlled via `application.yaml` (`features.*`):

- `features.git.remote.url` / `branch` — upstream repository holding feature definitions; `url` is **mandatory** in any environment other than local development (startup aborts with a clear error when it is missing; the `dev` profile supplies it for local runs), while `branch` is optional and defaults to the remote's default branch. A configured `branch` override is re-checked at every startup and the local checkout is switched to the new branch when it changed; without an override the tracked branch is fixed at clone time, so a change to the remote's default branch requires deleting the local clone.
- `features.git.local.path` / `data` — where the clone lives and where definition files are read from.
- `features.git.clone.depth` — how many commits are fetched at clone time: `1` (default) clones shallowly for a faster startup, while `0` clones the full history. The depth is preserved across refresh fetches.
- `features.cache.ttl` — `java.time.Duration` (e.g. `PT10M`) controlling how long a parsed feature stays cached; serves as a backstop for failed refresh ticks (a successful tick evicts removed features immediately, TTL notwithstanding).
- `features.refresh.interval` — `java.time.Duration` (e.g. `PT1M`) controlling the polling period of the load pipeline.
- `features.refresh.startup-timeout` — `java.time.Duration` (default `PT1M`) bounding how long the blocking startup tick may run before startup aborts.
- `features.refresh.stop-timeout` — `java.time.Duration` (default `PT5S`) bounding how long a stop waits for an in-flight tick before proceeding with shutdown.
- `features.refresh.backoff.min` / `max` — `java.time.Duration` (defaults `PT1S` / `PT1M`) controlling the exponential backoff of the initial tick's retry; `max-attempts` (default `5`) is the total number of attempts (that is, `max-attempts - 1` retries) before startup aborts.
