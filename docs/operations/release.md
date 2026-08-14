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

1. Checks out the triggering branch (using the `DEPLOY_KEY` deploy key) and sets up JDK 25 (Liberica) and Gradle.
2. Resolves and validates the release version, computes the next snapshot version (patch+1, e.g. `0.1.0` → `0.1.1-SNAPSHOT`), and pins the release version in `gradle.properties`.
3. Logs in to ghcr.io with the repository's `GITHUB_TOKEN`.
4. Runs `./gradlew build` (tests + jar) and `bootBuildImage`, producing `ghcr.io/<owner>/<repo>:<version>` (derived from `github.repository`).
5. Commits the pinned release version in `gradle.properties` and pushes it directly to the triggering branch (bypassing branch rulesets via the deploy key), then creates and pushes the Git tag `v<version>`. The tag therefore points at a commit that carries the released version.
6. Pushes the image to ghcr.io. When releasing from `master`, the image is additionally tagged and pushed as `:latest`.
7. Creates a GitHub Release for `v<version>` whose notes include the published image reference (`docker pull ghcr.io/<owner>/<repo>:<version>`) plus automatically generated release notes.
8. Prepares the next development iteration: commits `version=<next>-SNAPSHOT` (e.g. `0.1.1-SNAPSHOT`) in `gradle.properties` and pushes it directly to the triggering branch.

## Dependencies and prerequisites

- **`DEPLOY_KEY`** (repository secret): an SSH deploy key **with write access**. Git operations (version commits and tag push) authenticate with it, and the `master` ruleset is configured to let deploy keys bypass its rules. Without this, the direct pushes in steps 6 and 8 are rejected.
- **Ruleset bypass:** the branch ruleset covering the release branch (currently `master`) must list **Deploy keys** in its bypass list. Deploy keys are the recommended bypass actor here because they work for personal-account repositories and are scoped to a single repository.
- **`GITHUB_TOKEN`** covers the remaining authenticated operations; the workflow requests `contents: write` (GitHub Release) and `packages: write` (ghcr push).
- **ghcr.io** must accept packages for the repository owner's namespace, and package visibility should be set as desired (public for public consumption, private otherwise).
- **Docker** is available on the `ubuntu-latest` runner; the image is built via Cloud Native Buildpacks (see [Container Image Build](container-image.md)).
- The project version lives in **`gradle.properties`** (`version=...`); `build.gradle` no longer hardcodes it. The workflow pins the release version in the file before building, so the build uses the release version without a CLI override.

## Notes and caveats

- **Protected branches:** the version commits (steps 5 and 8) are pushed directly to the protected branch. This relies on the deploy key being in the ruleset's bypass list; if the bypass is removed, the push fails and no release artifacts are published.
- **Tag placement:** the `v<version>` tag is created after the release-version commit is pushed, so the tagged commit contains `version=<released>` in `gradle.properties` rather than a snapshot.
- **Idempotent reruns:** every step is safe to re-run for the same version — the version commit skips when `gradle.properties` is already at the release version, the `v<version>` tag creation skips when the tag already exists at the current commit, the image push re-pushes the same tag, and the GitHub Release step updates an existing release. Recovery from a partially failed run is therefore just re-triggering the workflow. The one hard failure is a tag that already exists at a **different** commit than `HEAD`: the workflow aborts rather than silently repointing the tag, so it must be inspected and removed manually.
- **CI vs Release:** the `main.yml` CI workflow still builds the image on every push and pull request as build validation, but no longer saves or uploads a master image artifact — publishing to a registry is exclusively the Release workflow's job. Pushing the version commits to `master` therefore triggers an ordinary CI run.

## References

- [Container Image Build](container-image.md) — how the OCI image is built.
- [Module Docs Index](../index.md) — all Feature Valves docs.
