---
type: concept
title: "Feature Valve"
description: "The domain mental model of a feature valve: tags as match conditions, exposition levels, deterministic hashing, and most-specific-valve-wins selection."
tags:
  - "concept"
  - "feature-flags"
  - "incremental-rollout"
  - "evaluation"
timestamp: "2026-08-11T00:00:00Z"
related:
  - "[Overview](../architecture/overview.md)"
  - "[Reactive Data Flow](../architecture/reactive-data-flow.md)"
  - "features/test/test-feature.yml"
resource:
  - "src/main/java/org/calipsoide/featurevalves/Feature.java"
  - "src/main/java/org/calipsoide/featurevalves/FeatureValve.java"
  - "src/main/java/org/calipsoide/featurevalves/HashingEvaluator.java"
---

# Feature Valve

A *feature valve* is the unit of rollout control: a named rule with a match condition and a level of exposition. Given a request's key-value data, the service decides whether the flag is **ON** or **OFF**.

## The Elements

- **Tags** — the raw request data: a set of key-value pairs. One pair (e.g. `animal: cat`) is a `Tag`.
- **Valve** (`FeatureValve`) — a named rule holding a map of required tags and an exposition percentage (`value`). A valve *matches* a request when every one of its required tags is present among the request tags (a strict subset requirement — no partial matches).
- **Evaluator** (`Evaluator`) — turns a request into a deterministic exposition level. `HashingEvaluator` concatenates the values of the tags listed under `eval` in a feature's definition, hashes them, and maps `hash % 100` onto an `ExpositionLevel` between 0 and 100. If none of the `eval` tags are supplied, the evaluator yields no level.
- **Exposition level** (`ExpositionLevel`) — an integer percentage in `[0, 100]`. A valve *allows* a request when `valve exposition > request exposition level`, i.e. the matched bucket is strictly below the valve's threshold.

## Evaluation Semantics

`Feature.execute(check)` decides the boolean result:

1. If the feature is not `active`, it always returns `false`.
2. Among the valves that match the request, it picks the one with the **most required tags** (highest cardinality). This is *most-specific-match-wins*: a narrow rule targeting `size:large` + `animal:cat` overrides a broader rule for `animal:cat`.
3. The evaluator computes the request's exposition level, and the chosen valve compares it against its configured exposition. No matching valve, or no determinable level, means `false`.

## Invariants and Consequences

- **Deterministic and sticky.** The same request data always maps to the same bucket, so a given user/entity is consistently exposed (or not) while a valve's threshold is unchanged.
- **Rollout by threshold move.** Increasing a valve's `value` widens exposure without any request-data changes — the mechanism behind incremental rollout, and the reason this service is called "valve" rather than "flag".
- **Order-independent.** Matching is set-like (tag containment), not positional, which keeps definitions declarative in YAML.

## Worked Example Outline

Given the bundled `features/test/test-feature.yml`: a request with `name: little.rose`, `size: large`, `animal: cat` matches only the `all.large.cats` valve (`size` + `animal` tags). Its `value: 0` means essentially no exposure at this state; raising the value incrementally would admit more hashes of `little.rose` until fully on at `value: 100`.