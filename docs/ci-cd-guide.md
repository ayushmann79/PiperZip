# CI/CD guide: how the GitHub Actions in this repo work

Read this next to `.github/workflows/`. Each section says what a concept is, where to see it here, and what to
check when it breaks.

## 1. YAML and workflow anatomy

YAML is indentation-based: spaces only (never tabs), `key: value` maps, `- item` lists. Wrong indentation is the
most common reason a workflow "does nothing". A workflow file has:

```yaml
name: CI                 # label in the Actions tab
on: ...                  # WHEN it runs (triggers)
permissions: ...         # WHAT the GITHUB_TOKEN may do
jobs:                    # WHAT runs
  lint:                  # a job: runs on one fresh virtual machine (runner)
    runs-on: ubuntu-latest
    steps:               # run in order on that machine
      - uses: owner/action@sha   # reuse a packaged action
      - run: mvn validate        # or run a shell command
```

- **Runner**: the VM. `ubuntu-latest` is a GitHub-hosted runner: fresh, disposable, maintained by GitHub.
  *Self-hosted* runners are your own machines: more control and no minute limits, but they persist between jobs,
  so a malicious PR can leave something behind, and you must patch them. Never use self-hosted runners for public
  repositories that accept PRs from strangers.
- **Action**: reusable code, such as `actions/checkout`. Marketplace presence is not a security review, so we pin
  each action to a full commit SHA (tags can be moved; SHAs cannot) and keep the human-readable version in a
  comment. Dependabot proposes SHA updates.
- **Step output / env**: `$GITHUB_OUTPUT` passes values between steps and jobs (see `validate` in `release.yml`).
  Untrusted text (branch names, PR titles) must go through `env:` and be quoted in shell, never interpolated
  directly with `${{ }}` inside `run:`; that is how script injection happens.

## 2. Triggers (`ci.yml`)

- `push` to `main`, `pull_request` into `main`: normal flow.
- `workflow_dispatch`: a "Run workflow" button.
- `workflow_call`: lets another workflow reuse this one (a **reusable workflow**). `release.yml` calls it with
  `uses: ./.github/workflows/ci.yml`, so a release always re-runs every check.
- `concurrency`: a newer run for the same PR cancels the older one; main and tags are never cancelled.

## 3. Jobs, dependencies and failure behaviour

```
lint ─┐
      ├──► test (Java 21, Java 25) ──► package
static-analysis ─┘
```

`needs:` makes the graph. `lint` and `static-analysis` run in parallel (cheap, fast feedback); `test` starts only if
both pass; `package` only if `test` passes. A failed job marks every dependent job as skipped.

**Matrix** (`test` job): one job definition expanded into several parallel jobs, one per value of `matrix.java`.
`fail-fast: false` lets every leg finish so you see all failures at once (the default cancels the rest on the first
failure). Java 25 is `experimental`: `continue-on-error` reports its failures without blocking. Expensive work is
**not** duplicated: Checkstyle and SpotBugs run once in their own jobs and are skipped inside the matrix with
`-Dcheckstyle.skip=true -Dspotbugs.skip=true`.

## 4. Maven in CI

Lifecycle: `validate → compile → test → package → verify`. Running a phase runs all earlier ones. In this POM,
JaCoCo's report and gate, Checkstyle and SpotBugs are bound to `verify`, so a local `mvn verify` runs everything
CI does. `actions/setup-java` with `cache: maven` caches `~/.m2` keyed on the hash of `pom.xml`: unchanged
dependencies are not downloaded again. If the cache is stale or corrupt, bump a key or delete it under
*Actions → Caches*.

## 5. Artifacts

`actions/upload-artifact` stores files from a run (test reports, coverage HTML, the JAR) for 14 days. Artifacts are
for humans and later jobs; **releases** (section 7) are the permanent, versioned distribution.

## 6. Permissions and secrets

Every workflow starts with `permissions: contents: read`. A job gets more only when it needs it: `security-events:
write` for CodeQL uploads, `contents: write` only in the release `publish` job. This project uses **no secrets at
all** (the release uses the short-lived automatic `GITHUB_TOKEN`), so a leaked workflow cannot leak credentials.
For PRs from forks GitHub already withholds secrets and gives a read-only token; we also never use
`pull_request_target` (which runs with privileges and must never check out untrusted code).

## 7. Releases (`release.yml`)

Trigger: pushing a tag `v*`. Pipeline: **validate** (semver tag, equals `pom.xml` version, commit on `main`) →
**checks** (reuse `ci.yml`) → **publish** (build, `SHA256SUMS`, `gh release create`). We use the `gh` CLI that is
preinstalled on runners instead of a third-party release action: one fewer thing to trust. Pull requests cannot
trigger it because only tag pushes do.

## 8. Security scanning (`security.yml`)

See `security.md` for what each technique finds. Schedules (`cron`) matter: a vulnerability published tomorrow
should still fail next Monday's run even if nobody pushed code.

## 9. Dependabot

`.github/dependabot.yml` opens weekly PRs for Maven dependencies and for action versions (minor/patch grouped).
Reviewing one: read the release notes, check the CI result, look for breaking changes in the changelog for major
versions, merge if green. A red CI on a Dependabot PR is the system working: fix forward or pin and note why.

## 10. Pull requests and branch protection

Set this up once the first CI run has succeeded (Settings → Branches → add rule for `main`):
- Require a pull request before merging.
- Require status checks: `Code style (Checkstyle)`, `Static analysis (SpotBugs)`, `Test (Java 21)`,
  `Package and smoke-test JAR`. (Check names appear after the first run.)
- Require branches to be up to date; block force pushes.

## 11. Debugging a red pipeline

1. Open the failing run, find the first red step, expand its log: the first error is usually the cause.
2. Reproduce locally with the exact command from the step (for example `mvn verify`).
3. "Works locally, fails in CI": compare Java version (CI uses Temurin 21), locale, and whether you ran as root.
4. YAML errors show as a failed run with no jobs: check indentation and quotes.
5. Permission errors (`Resource not accessible by integration`) mean a job is missing a `permissions:` entry.
