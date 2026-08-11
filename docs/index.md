# Feature Valves Docs

> Quick discovery index. Load individual pages for full context.

## Architecture

The design of the service: its component boundaries, technology choices, and how the reactive load pipeline and the request evaluation path interact.

- [Overview](architecture/overview.md) - How Feature Valves works as a Git-backed feature flag HTTP server and the stack it is built on.
- [Reactive Data Flow](architecture/reactive-data-flow.md) - The polling-driven refresh pipeline and the per-request evaluation path, both built on Project Reactor.

## Concepts

The domain mental models behind the request evaluation semantics, distinct from the source signatures.

- [Feature Valve](concepts/feature-valve.md) - How a valve, its tags, exposition level, and deterministic hashing decide whether a request gets flagged ON or OFF.

## Planning

- [Spring Boot 4.1.0 Upgrade Plan](plan/spring-boot-4-upgrade/README.md) - Phased implementation plan (test-first coverage gate, then the Boot 4.1.0 / JDK 25 upgrade) derived from the [SIA](sia/spring-boot-4-upgrade.md).