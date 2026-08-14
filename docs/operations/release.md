---
type: workflow
title: "Release Process"
description: "How to release a new version of Feature Valves with the Release workflow: trigger it, what it does, its dependencies, and how the next development version is prepared."
tags:
  - "operations"
  - "release"
  - "ci-cd"
  - "ghcr"
  - "github-actions"
timestamp: "2026-08-14T00:00:00Z"
related:
  - "container-image.md"
  - "../index.md"
  - "build.gradle"
  - "gradle.properties"
  - ".github/workflows/release.yml"
  - ".github/workflows/main.yml"
resource:
  - ".github/workflows/release.yml"
  - "gradle.properties"
---

# Release Process

## Overview

Releases are driven by the **Release** GitHub Actions workflow (`.github/workflows/release.yml`). It builds, tests, and packages the application as an OCI image, publishes it to the GitHub Container Registry (ghcr.io), tags the repository, creates a GitHub Release, and prepares the codebase for the next development iteration.

The workflow is intentionally reusable: it runs on any branch (`master` today, `release/*` later) and can be invoked manually or called from other workflows.

## How to trigger a release

1. Open the **Actions** tab, select the **Release** workflow.
2. Click **Run workflow**.
3. Enter the version to release, or leave it **blank**:
   - **Blank (recommended):** the workflow uses the current project version from `gradle.properties`, stripping the `-SNAPSHOT` suffix. For example, `0.1.0-SNAPSHOT` releases `0.1.0`.
   - **Explicit:** a semantic version such as `0.2.0`. A leading `v` and a trailing `-SNAPSHOT` are tolerated and stripped. The version must match `MAJOR.MINOR.PATCH`.
4. Select the branch to release from (`master` for a production release).
5. Run the workflow.

## What the workflow does

1. Checks out the triggering branch and sets up JDK 25 (Liberica) and Gradle.
2. Resolves and validates the release version, and computes the next snapshot version (patch+1, e.g. `0.1.0` → `0.1.1-SNAPSHOT`).
3. Logs in to ghcr.io with the repository's `GITHUB_TOKEN`.
4. Runs `./gradlew build` (tests + jar) and `bootBuildImage`, producing `ghcr.io/ehpalumbo/feature-valves:<version>`.
5. Pushes the image to ghcr.io. When releasing from `master`, the image is additionally tagged and pushed as `:latest`.
6. Creates and pushes a Git tag `v<version>`.
7. Creates a GitHub Release for `v<version>` whose notes include the published image reference (`docker pull ghcr.io/ehpalumbo/feature-valves:<version>`).
8. Prepares the next development iteration: it opens a pull request on the triggering branch that bumps `version` in `gradle.properties` to `<next>-SNAPSHOT` (e.g. `0.1.1-SNAPSHOT`). A human merges this PR; protected branches require review.

## Dependencies and prerequisites

- **`GITHUB_TOKEN`** is used for all authenticated operations; the workflow requests `contents: write` (tag + branch push), `packages: write` (ghcr push), and `pull-requests: write` (bump PR).
- **ghcr.io** must accept packages for the `ehpalumbo` namespace, and package visibility should be set as desired (public for public consumption, private otherwise).
- **Docker** is available on the `ubuntu-latest` runner; the image is built via Cloud Native Buildpacks (see [Container Image Build](container-image.md)).
- The project version lives in **`gradle.properties`** (`version=...`); `build.gradle` no longer hardcodes it. The workflow overrides it on the command line (`-Pversion=...`) so the release build does not modify the working tree.

## Notes and caveats

- **Protected tags:** `master` is protected by branch rulesets, which do not govern tag pushes. If tag protection is added later, pushing `v<version>` with `GITHUB_TOKEN` will fail; a dedicated `RELEASE_PAT` secret would be needed as the push credential.
- **Bump PR merge:** the next-version PR must be merged by a human. Branch rulesets on `master` (or a `release/*` branch) require review.
- **CI vs Release:** the `main.yml` CI workflow still builds the image on every push and pull request as build validation, but no longer saves or uploads a master image artifact — publishing to a registry is exclusively the Release workflow's job.

## References

- [Container Image Build](container-image.md) — how the OCI image is built.
- [Module Docs Index](../index.md) — all Feature Valves docs.
