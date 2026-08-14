# Feature Valves Docs

> Quick discovery index. Load individual pages for full context.

## Architecture

The design of the service: its component boundaries, technology choices, and how the reactive load pipeline and the request evaluation path interact.

- [Overview](architecture/overview.md) - How Feature Valves works as a Git-backed feature flag HTTP server and the stack it is built on.
- [Reactive Data Flow](architecture/reactive-data-flow.md) - The polling-driven refresh pipeline and the per-request evaluation path, both built on Project Reactor.

## Concepts

The domain mental models behind the request evaluation semantics, distinct from the source signatures.

- [Feature Valve](concepts/feature-valve.md) - How a valve, its tags, exposition level, and deterministic hashing decide whether a request gets flagged ON or OFF.

## Operations

How the service is built, packaged, and run.

- [Container Image Build](operations/container-image.md) - How the OCI image is built with Spring Boot buildpacks, the Spring AOT processing baked in, and the base-image rationale.
- [Release Process](operations/release.md) - How to release a new version with the Release workflow, what it does, and its dependencies.