# Feature Valves

Feature flag HTTP server, backing feature state on Git repositories. Well suited for incremental rollouts, hence the term "valve".

### What is a "feature valve"?
A feature valve is a convenient way to call a feature flag that is able to be computed on each request. For any given request data in the form of a set of key-value pairs, the valve can compute if the feature flag is ON or OFF based on a list of predefined rules. These rules specify when they can be applied for the incoming request data, what level of exposition is allowed for the matching requests, and what value data must be considered in order to compare the incoming request against the exposition level.

For example, a feature valve state could be defined in a YAML file as follows:

```yaml
active: true
eval:
  - name
valves:
  - name: all.large.cats
    tags:
      size: large
      animal: cat
    value: 10
  - name: some.small.dogs
    tags:
      size: small
      animal: dog
    value: 25
```
Incoming requests for the feature flag to be computed can include data for evaluation as follows:
```json
{
  "name": "little.rose",
  "size": "large",
  "animal": "cat",
  "age": 3
}
```
In this case, the valve named "all.large.cats" is applied and the hash of the tag value "little.rose" is compared against the current exposition level (here, 10%) to determine if the flag is on.

### How to use it?
TODO - This service can be easily containerized (e.g. using Docker), and it can be deployed side-by-side with its client application server to be locally called, or as an ordinary remote service if latency is not an issue.

### Container / Docker

The service is shipped as an OCI image built with Spring Boot's Cloud Native Buildpacks support (the `bootBuildImage` Gradle task). No `Dockerfile` is maintained; the build produces a distroless-style, non-root image based on the Paketo **tiny** (Ubuntu Noble) builder with a `jlink`-generated minimal JRE, and Spring AOT processing is enabled so the image starts faster on the JVM. See the [Container Image Build](docs/operations/container-image.md) doc for the full rationale and alternatives (including Alpine as a future option).

Build the image locally:

```shell
./gradlew bootBuildImage
```

Run it (defaults to port 8080):

```shell
docker run --rm -p 8080:8080 ehpalumbo/feature-valves:0.1.0-SNAPSHOT
```

The service reads its `features.*` configuration from `application.yaml`; override with environment variables or a config map as needed. The `features.git.remote.url` setting is **mandatory** in every environment except local development — startup aborts with a clear error if it is missing — so provide it at runtime, e.g. `docker run -e FEATURES_GIT_REMOTE_URL=...`. The feature repo is cloned shallowly by default (`features.git.clone.depth`, default `1`) for a faster startup; set it to `0` for a full-history clone. For local development only, run with the `dev` profile (`SPRING_PROFILES_ACTIVE=dev`) to use the development repository. On startup you should see `Starting AOT-processed ...`, confirming AOT is active.

### Module Docs

Please refer to the [Module Docs Index](docs/index.md) for further details.
