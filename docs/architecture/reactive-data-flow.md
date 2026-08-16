---
type: architecture
title: "Reactive Data Flow"
description: "How the polling-driven refresh pipeline and the per-request evaluation path work, and why the design is fully reactive with an in-memory cache."
tags:
  - "architecture"
  - "project-reactor"
  - "data-flow"
  - "caching"
timestamp: "2026-08-12T00:00:00Z"
related:
  - "[Overview](overview.md)"
  - "[Feature Valve](../concepts/feature-valve.md)"
resource:
  - "src/main/java/org/calipsoide/featurevalves/application/FeatureLoader.java"
  - "src/main/java/org/calipsoide/featurevalves/application/InMemoryFeatureService.java"
  - "src/main/java/org/calipsoide/featurevalves/infra/scheduling/FeatureRefreshScheduler.java"
  - "src/main/java/org/calipsoide/featurevalves/web/FeatureCheckController.java"
---

# Reactive Data Flow

The service runs two independent flows: a background refresh that keeps the cache warm, and a request path that only reads from that cache.

## Refresh Pipeline

`FeatureLoader` exposes a single `load()` that runs one full refresh of the pipeline described below. Its cadence and lifecycle live in `FeatureRefreshScheduler` (infra), a `SmartLifecycle` component: its `start()` runs an initial tick that blocks startup — retrying with exponential backoff (`features.refresh.backoff.min`/`max`, `max-attempts` total attempts) until it succeeds or `features.refresh.startup-timeout` elapses, so the app is not ready before the cache is populated — and then a single unbounded reactive loop runs one tick per completed tick plus a fixed delay of `features.refresh.interval` (`Mono.delay(interval).then(tick).repeat()`). Because each tick only starts after the previous one has completed, ticks never overlap even when a refresh is slow.

1. A tick calls `GitFeatureFileRepository.loadAll()`, which:
   - calls `GitRepoManager.update()` to fetch just the tracked branch (a branch-scoped refspec, so only that branch's tip is transferred on multi-branch repositories) and hard-reset the local clone's working tree to its remote tip via JGit (a read-only mirror, so no merge base is needed and shallow clones work end to end), offloaded to a bounded-elastic scheduler so the blocking call never holds a reactive pipeline thread; the clone that set up the tracked branch happened earlier in a startup initialization phase (`GitRepoManager` is an `InitializingBean`, cloning shallowly by default, resolving the remote's default branch via `ls-remote` — a probe needed to name the branch so `branchesToClone` can narrow the clone to a single ref, since JGit clones every remote branch otherwise) that re-checks a configured `features.git.remote.branch` override and switches the local checkout when it changed;
   - delegates to `LocalFeatureFileRepository.loadAll()` to scan that directory.
2. `LocalFeatureFileRepository` walks `features/<application>/` folders and lists `*.yml`/`*.yaml` files in filename order; the blocking directory enumeration runs on a bounded-elastic scheduler, while file content is read with the reactive `DataBufferUtils.read(Path, …)` overload into `DataBuffer`s (in Spring 4 the old `AsynchronousFileChannel` variant is gone), yielding `FeatureFile` objects carrying the file content and a derived `FeatureId`.
3. `YamlFileFeatureFactory.read(file)` parses the YAML and assembles a `Feature` (valves, evaluator, active flag). Parsing errors surface as errors in the reactive stream; `FeatureLoader` wraps each read in `Mono.defer(...).onErrorResume(...)` so a single malformed file is skipped without aborting the tick.
4. The tick's stream is subscribed to `InMemoryFeatureService` (a `Subscriber<Feature>`), which inserts each parsed feature into a Caffeine-backed `ConcurrentMap` (exposed by `CacheConfig` as the live `asMap()` view of a Caffeine cache) and records it as seen in the current tick. When the tick completes, every cached feature that was not seen is evicted — so features deleted from the repository stop being served on the next refresh. A failed tick (e.g. the git update errors) leaves the cache untouched, and the entry TTL (`features.cache.ttl`) remains as a backstop.

The loop keeps running across misfires: the scheduler resumes each failed tick with an empty signal (its error is delivered to the subscriber, which declines to evict), so the fixed-delay loop simply proceeds to the next interval.

## Request Evaluation Path

`FeatureCheckController` exposes `POST /feature_valves/{application}/{feature}/checks`:

1. The incoming request body (a map of tag name → value) is normalized into `FeatureCheckRequest` tags.
2. `application` and `feature` path variables become a `ClientApplicationId` and a lower-cased `FeatureId`.
3. `InMemoryFeatureService.findBy(id)` looks the feature up in the map, returning a `Mono<Feature>`.
4. A request for an uncached (or expired) feature yields `404 Not Found` via `defaultIfEmpty`; otherwise `Feature.execute(check)` runs the evaluation (see the [Feature Valve concept](../concepts/feature-valve.md)) and the boolean result is returned as `{ "result": true|false }`.

## Why These Choices

- **Reads never touch the source of truth.** The request path is a pure in-memory lookup, keeping latency low and independent of Git or disk availability.
- **Reconcile-on-refresh semantics.** Every completed refresh tick makes the cache exactly match the repository: changed values and removed features are served (or dropped) on the next tick, bounding how stale a rollout or rollback can be. The TTL is a backstop for failed refreshes, not the primary staleness bound.
- **Non-blocking everywhere.** Both flows are reactive, so a slow file read or burst of requests does not consume a thread per operation.
