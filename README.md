# PiperZip

[![CI](https://github.com/ayushmann79/piperzip/actions/workflows/ci.yml/badge.svg)](https://github.com/ayushmann79/piperzip/actions/workflows/ci.yml)
[![Security](https://github.com/ayushmann79/piperzip/actions/workflows/security.yml/badge.svg)](https://github.com/ayushmann79/piperzip/actions/workflows/security.yml)

A reliable, secure, lossless file-compression command-line tool written in Java, inspired by the fictional
compression startup in *Silicon Valley*. PiperZip does **not** invent a new algorithm: it uses established
formats (GZIP and ZIP, both based on Deflate) with careful engineering: streaming I/O, integrity verification,
safe archive extraction, and a complete GitHub Actions pipeline. Its architecture leaves room for experimenting
with other algorithms later.

> **Status:** version 0.1.0-SNAPSHOT. The badges above resolve once the repository exists at
> `github.com/ayushmann79/piperzip` (edit the URLs if you use a different owner or name) and the workflows have run.
> See [What has been verified](#what-has-been-verified) for what is and is not proven.

## Features

- **GZIP** compress / decompress of single files (`--level 1-9`).
- **ZIP** archive create (files and directories, recursive) and **safe** extract.
- **Statistics**: original size, compressed size, bytes saved, ratio, percent reduction, elapsed time; `--json`
  for scripts.
- **Integrity**: SHA-256 `verify` of two files; `compress --verify` proves the result restores to the original.
- **Safety**: ZIP Slip / path traversal rejection, symlink protection, decompression-bomb limits, all-or-nothing
  extraction, no partial output files, no overwrite without `--force`.
- **Streaming**: files are processed in 64 KiB chunks; memory does not grow with file size.
- Meaningful exit codes and error messages.

## Formats and limitations

| | GZIP (`.gz`) | ZIP (`.zip`) |
|---|---|---|
| What it is | A compression format for **one** byte stream | An archive **container** of many entries |
| Stores names/directories | No | Yes |
| Algorithm used here | Deflate | Deflate (per entry) |

- Compression never guarantees a smaller file. Already-compressed or random data (JPEG, MP4, encrypted files)
  usually grows slightly; `stats` and the output tell you when that happens. Ratios depend entirely on the data.
- For ZIP, "compressed size" is the whole archive and includes container overhead.
- ZIP: no encryption, no symbolic links (rejected on create, ignored on extract), no multi-volume archives.
- GZIP decompression has no output size limit (see [docs/security.md](docs/security.md)).
- Not supported: `.tar.gz`, `.7z`, `.rar`, password protection.

## Prerequisites

- Java **21 or newer** (JDK for building, JRE for running). CI tests on Java 21 and, as an experimental probe, 25.
- Maven **3.9+** and Git, to build from source.

Check what you have:

```bash
java -version && javac -version && mvn -version && git --version
```

## Build and run

```bash
git clone https://github.com/ayushmann79/piperzip.git
cd piperzip
mvn verify                      # compile, test, coverage gate, Checkstyle, SpotBugs, package
java -jar target/piperzip-*.jar --help
```

Convenience alias (Linux/macOS): `alias piperzip='java -jar /full/path/to/piperzip-0.1.0.jar'`.
On Windows PowerShell: `function piperzip { java -jar C:\path\to\piperzip-0.1.0.jar @args }`.

Downloaded a release? Verify it first (details in [docs/release-process.md](docs/release-process.md)):

```bash
sha256sum --check SHA256SUMS
```

## Usage

```text
piperzip compress   <input> <output.gz> [-l 1-9] [-f] [--verify] [--json]
piperzip decompress <input.gz> <output> [-f] [--json]
piperzip archive create  <output.zip> <input>... [-l 1-9] [-f] [--json]
piperzip archive extract <input.zip> <directory> [-f] [--max-entries N] [--max-total-bytes N] [--max-ratio N] [--json]
piperzip stats  <input> [-l 1-9] [--json]       # what GZIP would achieve; writes nothing
piperzip verify <original> <restored> [--json]  # SHA-256 comparison
piperzip --help | --version
```

Existing outputs are never overwritten unless you pass `-f/--force`.

### Exit codes

| Code | Meaning |
|---|---|
| 0 | Success |
| 1 | I/O error (missing file, permission denied, disk full) |
| 2 | Usage error (unknown option, missing argument, `--level` out of range) |
| 3 | Invalid input (bad path, output exists, unusable destination) |
| 4 | Corrupt or truncated data |
| 5 | Unsafe archive rejected (path traversal, limits exceeded) |
| 6 | `verify` found the files differ |
| 70 | Internal error (set `PIPERZIP_DEBUG=1` for a stack trace) |

### Sample output

Produced by running the CLI on a generated 2 MB log-style file (timings vary by machine). They come from a local
build compiled with `javac` on JDK 21, not from the Maven/CI build.

```text
$ piperzip compress server.log server.log.gz --verify
Compressed 'server.log' -> 'server.log.gz' (gzip, level 6)
  Original size     : 2,044,840 bytes
  Compressed size   : 185,127 bytes
  Bytes saved       : 1,859,713 bytes
  Compression ratio : 11.05:1
  Size reduction    : 90.95%
  Elapsed           : 262 ms
  Verified          : restored data matches the original (SHA-256)

$ piperzip decompress server.log.gz restored.log
Decompressed 'server.log.gz' -> 'restored.log'
  Decompressed size : 2,044,840 bytes
  Compressed size   : 185,127 bytes
  Bytes saved       : 1,859,713 bytes
  Compression ratio : 11.05:1
  Size reduction    : 90.95%
  Elapsed           : 51 ms

$ piperzip verify server.log restored.log
SHA-256 978e9b1c838aec67cc009b9401cb5783a985e7b61a84e8549639654bdc752a6e  server.log
SHA-256 978e9b1c838aec67cc009b9401cb5783a985e7b61a84e8549639654bdc752a6e  restored.log
OK: the files are identical.

$ piperzip compress server.log server.log.gz
Error: Output already exists: server.log.gz (use --force to overwrite)
(exit code 3)

$ piperzip archive extract evil.zip out
Error: Archive entry escapes the destination (path traversal): ../../escape.txt
(exit code 5)
files extracted: 0
```

### Understanding the statistics

- **Compression ratio** = original / compressed (11.05:1 means the output is about 1/11 of the input; below 1:1
  means it grew).
- **Size reduction** = bytes saved / original × 100.
- **Bytes saved** is negative when the output is larger than the input.
- **Elapsed** is wall-clock time for the operation, including I/O.

### JSON for scripts

`piperzip compress --json in out.gz` prints one line, for example:
`{"operation":"compress","input":"in","output":"out.gz","algorithm":"gzip","level":6,"verified":false,"stats":{"originalBytes":...,"compressedBytes":...,"bytesSaved":...,"ratio":...,"percentReduction":...,"elapsedMillis":...}}`

## Testing

```bash
mvn test       # 128 JUnit tests
mvn verify     # + coverage gate (80% line / 70% branch), Checkstyle, SpotBugs
```

See [docs/testing.md](docs/testing.md).

## Architecture

CLI (picocli) → core services (`compression`, `archive`, `integrity`, `statistics`, `validation`, `io`) with a
`CompressionCodec` interface as the extension point. Details: [docs/architecture.md](docs/architecture.md).

## GitHub Actions pipeline

| Workflow | When | What |
|---|---|---|
| `ci.yml` | push/PR to main, manual, reusable | Checkstyle → SpotBugs → tests on Java 21 (+25 experimental) with coverage gate → package and smoke-test the JAR |
| `security.yml` | push/PR, weekly | CodeQL, Dependency Review (PRs), gitleaks secret scan |
| `release.yml` | tag `vX.Y.Z` | Validate tag, re-run CI, build, `SHA256SUMS`, GitHub Release |
| Dependabot | weekly | Maven and Actions updates |

Learn how each part works in [docs/ci-cd-guide.md](docs/ci-cd-guide.md).

## Security considerations

Archive extraction is treated as hostile-input handling; see [docs/security.md](docs/security.md) for defences and
**honest limitations**. Report vulnerabilities as described in [SECURITY.md](SECURITY.md).

## What has been verified

| Claim | Evidence |
|---|---|
| Core and CLI compile with `-Xlint:all -Werror` on JDK 21 | Run locally with `javac` |
| 128 JUnit tests pass (including permission tests, run as a non-root user) | Run locally with the JUnit console launcher |
| Coverage about 89% line / 79% branch | Measured locally with the JaCoCo agent |
| Checkstyle config passes on `src/main` | Run locally (Checkstyle 8.36 CLI) |
| `pom.xml`, workflows, SpotBugs | **Not yet executed.** The authoring environment could not reach Maven Central, so the first real `mvn verify` and the first GitHub Actions run happen on your machine. Plugin versions were chosen from knowledge, not resolved against Central; GitHub Action commit SHAs were resolved from the upstream repositories' tags. |

## Roadmap

- First green CI run and published v0.1.0 release (see [docs/first-push.md](docs/first-push.md)).
- Reproducible benchmark results ([docs/benchmarking.md](docs/benchmarking.md)); optional LZ4 / Zstandard codecs.
- Build provenance attestations for releases; fuzzing job; GraalVM native image.
- Optional `.tar.gz` support; GZIP output-size limit.

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md).

## License

Apache License 2.0, see [LICENSE](LICENSE). The release JAR bundles [picocli](https://picocli.info) (Apache-2.0).
