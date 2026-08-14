---
type: operations
title: "Container Image Build"
description: "How the Feature Valves OCI image is built with Spring Boot buildpacks, the AOT processing baked in, and the rationale behind the chosen base image."
tags:
  - "operations"
  - "docker"
  - "oci-image"
  - "cloud-native-buildpacks"
  - "spring-aot"
timestamp: "2026-08-13T00:00:00Z"
related:
  - "../architecture/overview.md"
  - "build.gradle"
  - ".github/workflows/main.yml"
  - "src/main/resources/application.yaml"
resource:
  - "build.gradle"
  - ".github/workflows/main.yml"
---

# Container Image Build

## Overview

Feature Valves ships as an OCI image built entirely with Spring Boot's own build tooling — the `bootBuildImage` Gradle task backed by Cloud Native Buildpacks (CNB). No hand-written `Dockerfile` is maintained. The image is produced from the executable `bootJar` artifact and is AOT-processed at build time so it starts faster on the JVM.

Build it locally with:

```shell
./gradlew bootBuildImage
```

The resulting image is tagged `ehpalumbo/feature-valves:0.1.0-SNAPSHOT` (or whatever `project.version` resolves to) and can be loaded and run with Docker:

```shell
docker run --rm -p 8080:8080 ehpalumbo/feature-valves:0.1.0-SNAPSHOT
```

## Why Spring Boot buildpacks (and not a Dockerfile)

Two viable strategies were evaluated when adding a container build:

- **Spring Boot `bootBuildImage` (CNB buildpacks)** — chosen.
  - Pros: zero hand-maintained `Dockerfile`; the builder picks correct Java version, non-root user, and hardening defaults; integrates cleanly with Spring AOT; repeatable and reproducible (fixed `Created` date).
  - Cons: the base image is chosen by the builder, so you cannot pick Alpine yourself.
- **Multi-stage `Dockerfile` (JDK builder → JRE / `jlink` runtime)** — rejected for now.
  - Pros: full control of the base image (allows true Alpine / musl), and `jlink` can produce a minimal modular runtime.
  - Cons: a `Dockerfile` must be written and maintained; layering and size tuning are manual; does not use Spring Boot build tooling.

The maintainability win of letting the build tooling own the image outweighed the marginal size win of a hand-tuned Alpine image.

## Base image and size rationale

- `bootBuildImage` defaults to the Paketo builder `paketobuildpacks/builder-noble-java-tiny:latest`, whose run image `paketobuildpacks/run-noble-tiny` is a minimal **Ubuntu Noble (glibc)** distroless-style image: no shell, non-root, minimal attack surface.
- The **tiny** builder is deliberately chosen for the smallest Paketo output; it satisfies the "slim images are acceptable" preference.
- **`jlink` is enabled** (`BP_JVM_JLINK_ENABLED=true`) so the Paketo BellSoft Liberica buildpack generates a custom minimal JRE at build time (default args `--no-man-pages --no-header-files --strip-debug --compress=1`). This roughly halves the runtime image size (~343 MB → ~177 MB) without any hand-written `Dockerfile`; a JRE is still installed at runtime and AOT is unaffected.
- **Alpine / musl** was the first choice in principle (smallest images), but it is only reachable via a hand-written `Dockerfile`, and musl can introduce JVM/native binary compatibility surprises. Temurin does publish `eclipse-temurin:25-jre-alpine`, so Alpine remains a viable future alternative if image size outranks maintainability — see the rationale above before switching.
- The tiny run image has no shell and a reduced library set; it is appropriate because this application is a plain JVM service with no start-script or system-dependency needs.

## Spring AOT

The `org.springframework.boot.aot` Gradle plugin is applied, so the executable jar embeds AOT-generated initialization code (via the `processAot` task) plus the AOT hint metadata under `META-INF/native-image`. At build time `BP_SPRING_AOT_ENABLED=true` tells the Paketo Spring Boot buildpack — which detects the AOT-instrumented jar — to set `BPL_SPRING_AOT_ENABLED=true` at launch, and the buildpack's launcher then contributes `-Dspring.aot.enabled=true` to `JAVA_TOOL_OPTIONS` on its own. No `JAVA_TOOL_OPTIONS` manipulation is needed in the build config:

```groovy
tasks.named('bootBuildImage') {
    imageName = "ehpalumbo/feature-valves:${project.version}"
    environment['BP_JVM_VERSION'] = '25'
    environment['BP_JVM_JLINK_ENABLED'] = 'true'
    environment['BP_SPRING_AOT_ENABLED'] = 'true'
    dependsOn tasks.named('processAot')
}
```

This is preferred over baking `-Dspring.aot.enabled=true` into `JAVA_TOOL_OPTIONS` with `BPE_APPEND_*`, because the value is supplied by the buildpack's launcher instead: `JAVA_TOOL_OPTIONS` stays clean for operators, and the buildpack keeps the flag consistent with related features (e.g. forcing the same profile if an AOT Cache training run is enabled later).

You can confirm AOT is active by checking the startup log line:

```
Starting AOT-processed Feature Valves ...
```

The launcher also logs `Spring AOT Enabled, contributing -Dspring.aot.enabled=true to JAVA_TOOL_OPTIONS`.

Keep the AOT restrictions in mind: the bean graph is fixed at build time — no runtime `@Profile` switching, and `@ConditionalOn*` / `enabled`-style conditions that would change which beans exist are not supported. Feature Valves uses plain `@Value` property injection with no such conditions, so it is AOT-compatible today; introducing profile- or condition-controlled beans later will require revisiting this.

> Note: This uses ahead-of-time processing on the **JVM**. It is distinct from the buildpack **AOT Cache** (`BP_JVM_AOTCACHE_ENABLED`), which performs a build-time training run and bakes a startup cache into the image — not enabled here. Unlike a hand-written multi-stage `Dockerfile` `jlink` step, the `jlink`-generated JRE here is produced by the Paketo buildpack (`BP_JVM_JLINK_ENABLED`), so no build tooling is bypassed.

## CI

The `.github/workflows/main.yml` build job runs `bootBuildImage` on every CI run (push and pull request) purely as build validation; the image is not saved or uploaded. Publishing is the Release workflow's job: `.github/workflows/release.yml` builds and pushes `ghcr.io/ehpalumbo/feature-valves:<version>` (and `:latest` for master releases), tags the repository, creates a GitHub Release, and prepares the next snapshot version. See the [Release Process](release.md) doc for details.

## Configuration

The container reads the same `features.*` settings from `application.yaml`. Defaults point at a public Git repository and local paths under `/var/tmp/feature-valves`; override them with environment variables or config maps as needed for a given deployment.
