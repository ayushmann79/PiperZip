# Changelog

All notable changes are documented here. Format based on [Keep a Changelog](https://keepachangelog.com);
versions follow [Semantic Versioning](https://semver.org).

## [Unreleased]

### Added
- GZIP `compress` / `decompress` with levels 1-9, atomic output, optional `--verify`.
- ZIP `archive create` and hardened `archive extract` (ZIP Slip, symlink, entry-count / size / ratio limits).
- `stats` and `verify` (SHA-256) commands; `--json` output; documented exit codes.
- GitHub Actions: CI (lint, static analysis, Java matrix, coverage gate, package), Security (CodeQL, dependency
  review, gitleaks), Release (validated tags, checksums), Dependabot.
- Documentation under `docs/` and a benchmark script (`scripts/benchmark.sh`).
