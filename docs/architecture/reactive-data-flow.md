---
type: architecture
title: "Reactive Data Flow"
description: "How the polling-driven refresh pipeline and the per-request evaluation path work, and why the design is fully reactive with an in-memory cache."
tags:
  - "architecture"
  - "project-reactor"
  - "data-flow"
  - "caching"
timestamp: "2026-08-11T00:00:00Z"
related:
  - "[Overview](overview.md)"
  - "[Feature Valve](../concepts/feature-valve.md)"
resource:
  - "src/main/java/org/calipsoide/featurevalves/FeatureLoader.java"
  - "src/main/java/org/calipsoide/featurevalves/CachingFeatureService.java"
  - "src/main/java/org/calipsoide/featurevalves/FeatureCheckController.java"
---

# Reactive Data Flow

The service runs two independent flows: a background refresh that keeps the cache warm, and a request path that only reads from that cache.

## Refresh Pipeline

`FeatureLoader` (an `InitializingBean`) kicks off a single unbounded reactive stream at startup:

1. `Flux.concat(now, timer)` emits once immediately, then on every configured `features.refresh.interval`.
2. Each emission triggers `GitFeatureFileRepository.loadAll()`, which:
   - calls `GitRepoManager.update()` to clone (first run) or pull (later runs) the configured remote branch into a local directory via JGit;
   - delegates to `LocalFeatureFileRepository.loadAll()` to scan that directory.
3. `LocalFeatureFileRepository` walks `features/<application>/` folders, lists `*.yml`/`*.yaml` files in filename order, and reads each with the reactive `DataBufferUtils.read(Path, …)` overload into `DataBuffer`s (in Spring 4 the old `AsynchronousFileChannel` variant is gone), yielding `FeatureFile` objects carrying the file content and a derived `FeatureId`.
4. `YamlFileFeatureFactory.read(file)` parses the YAML and assembles a `Feature` (valves, evaluator, active flag). Parsing errors surface as errors in the reactive stream.
5. The stream is subscribed to `CachingFeatureService` (a `Consumer<Feature>`), which inserts each parsed feature into a Caffeine cache (wrapped by Spring's `CaffeineCache`) with a write-side TTL (`features.cache.ttl`).

A misfire or exception in the stream does not take the service down; the cache simply stops being refreshed until the next successful tick.

## Request Evaluation Path

`FeatureCheckController` exposes `POST /feature_valves/{application}/{feature}/checks`:

1. The incoming request body (a map of tag name → value) is normalized into `FeatureCheckRequest` tags.
2. `application` and `feature` path variables become a `ClientApplicationId` and a lower-cased `FeatureId`.
3. `CachingFeatureService.findBy(id)` looks the feature up in the cache, returning a `Mono<Feature>`.
4. A request for an uncached (or expired) feature yields `404 Not Found` via `defaultIfEmpty`; otherwise `Feature.execute(check)` runs the evaluation (see the [Feature Valve concept](../concepts/feature-valve.md)) and the boolean result is returned as `{ "result": true|false }`.

## Why These Choices

- **Reads never touch the source of truth.** The request path is a pure in-memory lookup, keeping latency low and independent of Git or disk availability.
- **Reload-on-write semantics.** A refreshed feature replaces what is served immediately after the cache TTL elapses, bounding how stale a rollback or rollout can be.
- **Non-blocking everywhere.** Both flows are reactive, so a slow file read or burst of requests does not consume a thread per operation.