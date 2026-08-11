---
type: architecture
title: "Feature Valves System Overview"
description: "How Feature Valves works as a Git-backed feature flag HTTP server, its component boundaries, and the technology stack it is built on."
tags:
  - "architecture"
  - "feature-flags"
  - "spring-webflux"
  - "eventual-consistency"
timestamp: "2026-08-11T00:00:00Z"
related:
  - "[Reactive Data Flow](reactive-data-flow.md)"
  - "[Feature Valve](../concepts/feature-valve.md)"
  - "src/main/java/org/calipsoide/featurevalves"
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
- **Guava** — the `Cache` used to hold parsed features with a configurable time-to-live.
- **JGit** — cloning and pulling the remote repository that stores feature definition files.
- **SnakeYAML** — parsing feature definition files into the domain model.
- **Java 25** — source and target compatibility.
- **Gradle 9.7 + JUnit Jupiter** — build and the upgraded test suite (see the regression-gated migration below).

## Components and Boundaries

The application is split into two broad, deliberately decoupled paths:

- **Load / refresh pipeline** — `FeatureLoader` polls on a fixed interval, delegates to `GitFeatureFileRepository` (which first updates the local clone via `GitRepoManager`, then reads files via `LocalFeatureFileRepository`), converts each `FeatureFile` into a `Feature` via `YamlFileFeatureFactory`, and pushes the result into `CachingFeatureService`.
- **Request evaluation path** — `FeatureCheckController` accepts a feature check request, resolves a cached `Feature`, and lets the `Feature` domain object execute the check against a set of request tags.

## Design Highlights

- **Eventual consistency, no lock.** The feature state is refreshed asynchronously on a timer; the request path only ever reads the in-memory cache, never touching Git or disk.
- **Reactive end to end.** File reads use the reactive `DataBufferUtils.read(Path, …)` overload (the `AsynchronousFileChannel` variant was removed in Spring 5.1) with reactive buffers, so even the loading path avoids blocking threads.
- **Pluggable sourcing.** `FeatureFileRepository` is an interface; the Git-backed implementation merely decorates the local filesystem one with a git update step. That seam was added later to allow alternate file providers.
- **Regression-gated migration.** The jump from Boot 2.0 (2017) to Boot 4.1 / Java 25 was staged behind a pre-upgrade test suite — unit tests over the domain/engine and a full-context WebTestClient REST integration test driven by a real local JGit repo. The same suite, migrated from JUnit 4 to Jupiter, proves the upgrade changed no behavior.

## Configuration

Behavior is controlled via `application.yaml` (`features.*`):

- `features.git.remote.url` / `branch` — upstream repository holding feature definitions.
- `features.git.local.path` / `data` — where the clone lives and where definition files are read from.
- `features.cache.ttl` — `java.time.Duration` (e.g. `PT10M`) controlling how long a parsed feature stays cached.
- `features.refresh.interval` — `java.time.Duration` (e.g. `PT1M`) controlling the polling period of the load pipeline.