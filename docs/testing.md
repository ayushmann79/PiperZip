# Testing

```bash
mvn test        # unit + in-process integration tests
mvn verify      # + coverage report and gate, Checkstyle, SpotBugs
```

Tests use JUnit 5, `@TempDir` and generated data only: no network, no personal files, no sleeps.

| Area | Where | Examples |
|---|---|---|
| Codec | `GzipCodecTest` | round trip of empty / binary / Unicode data, every level, invalid levels, non-GZIP input |
| Files | `FileCompressorTest` | 48 MiB streaming round trip, Unicode names, overwrite rules, truncated and bit-flipped input, no partial output |
| Archives | `ZipArchiverTest` | names, order, duplicates, output-inside-input, symlinks |
| Extraction safety | `SafeExtractorTest` | 11 hostile name patterns, symlink in destination, limit boundaries (N passes, N+1 fails), 64 MiB zero bomb, corrupt/truncated ZIP, all-or-nothing overwrite |
| CLI | `PiperZipCliTest` | help/version, usage errors, exit codes, JSON, full compress→decompress→verify and archive workflows |
| Utilities | `AtomicFileWriterTest`, `PathValidatorTest`, `CompressionStatsTest`, `Sha256HasherTest` | failure cleanup, known SHA-256 vectors, ratio edge cases |

Permission-failure tests skip themselves when run as root (root ignores file permissions), and Unicode filename
tests skip when the JVM's file-name encoding is not UTF-8.

## Coverage

JaCoCo enforces 80% line and 70% branch coverage on the bundle (`Main` excluded). The initial code base measured
about 89% line / 79% branch with the local test run. **Coverage proves code was executed, not that it is correct or
secure**: a test can run a line without asserting anything about it, and no percentage shows that an attack you did
not think of is handled. The security tests above are written from the threat list, not from coverage gaps.
Raise the gate only when it reflects real, meaningful tests.

## Debugging failures

- Run one class: `mvn -Dtest=SafeExtractorTest test`.
- Reports: `target/surefire-reports/`, coverage: `target/site/jacoco/index.html`.
- In CI, the failing job's log shows the same Maven output; download the `reports-java-21` artifact for more.
