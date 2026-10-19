# Contributing

1. Fork, then create a branch: `git checkout -b fix/short-description`.
2. Make a focused change with tests (including failure cases; for archive code, hostile inputs).
3. Run `mvn verify` locally. It runs the tests, the coverage gate, Checkstyle and SpotBugs, the same as CI.
4. Open a pull request using the template. CI must be green.

Guidelines: keep business logic out of the `cli` package; no new dependency without a clear reason; never swallow
exceptions; document non-obvious security decisions. Use [Conventional-style](https://www.conventionalcommits.org)
commit messages if you like (`fix:`, `feat:`, `docs:`).

Reviewing Dependabot PRs: read the release notes, confirm CI is green, merge; for major versions, check the
changelog for breaking changes first.

Security issues: do **not** open a public issue; see [SECURITY.md](SECURITY.md).
