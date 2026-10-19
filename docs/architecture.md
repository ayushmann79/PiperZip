# Architecture

PiperZip is a thin command-line layer over a small, dependency-free core. The core never prints and never
calls `System.exit`, so every behaviour can be tested in-process.

```
 args ──► cli (picocli) ──► core services ──► java.util.zip / java.nio.file / java.security
            │                   │
            │                   ├─ compression : CompressionCodec (interface), GzipCodec, FileCompressor
            │                   ├─ archive     : ZipArchiver, SafeExtractor
            │                   ├─ integrity   : Sha256Hasher
            │                   ├─ statistics  : CompressionStats (immutable record)
            │                   ├─ validation  : PathValidator, ProtectedPaths
            │                   ├─ io          : AtomicFileWriter, counting/close-shield streams
            │                   ├─ config      : ExtractionLimits (immutable record)
            │                   └─ exception   : InvalidInput / CorruptData / UnsafeArchive
            └─ ExitCodes + one error handler turn exceptions into messages and exit codes
```

## Components

| Package | Responsibility |
|---|---|
| `cli` | Parse arguments, call the core, format output (text or JSON), map failures to exit codes. No business logic. |
| `compression` | `CompressionCodec` is the extension point (compress/decompress streams). `GzipCodec` implements it. `FileCompressor` handles files: validation, atomic output, statistics, round-trip verification. |
| `archive` | `ZipArchiver` builds archives; `SafeExtractor` extracts them defensively (see `security.md`). |
| `integrity` | SHA-256 hashing. Deliberately separate from compression: a hash says whether bytes are identical, not how well they compressed. |
| `statistics` | `CompressionStats`: sizes, bytes saved, ratio, percent reduction, elapsed time. |
| `validation` | Early, readable path checks; protection against extracting into system directories. |
| `io` | `AtomicFileWriter` (write to a hidden temp file, move into place only on success), byte-counting streams. |
| `config` | `ExtractionLimits`: entry count, total bytes, compression ratio. |
| `exception` | Expected failures, each mapped to one exit code. |

## Data flow: `compress input.txt out.gz`

1. `CompressCommand` validates `--level`, builds `FileCompressor(new GzipCodec(level))`.
2. `PathValidator` checks the input is a readable regular file and the output is writable, not the input, and not
   an existing file (unless `--force`).
3. `AtomicFileWriter` opens a hidden temp file next to the output. The codec streams input to it through
   `CountingInputStream` (64 KiB at a time; memory use does not grow with file size).
4. On success the temp file is moved over the target; on any failure it is deleted. No partial output.
5. `CompressionStats` is built from the counted bytes and `Files.size(output)` and printed.

## Why these choices

- **Standard library for compression.** `java.util.zip` streams; no dependency needed.
- **picocli for the CLI.** One small Apache-2.0 library gives subcommands, help, version and validation.
- **One interface, `CompressionCodec`.** The only abstraction that earns its keep: adding LZ4 or Zstandard later
  means implementing it and wiring a command option, with no rewrite.
- **Unchecked domain exceptions + `IOException`.** Expected user errors are `PiperZipException` subclasses
  (exit codes 3, 4, 5); real I/O failures stay `IOException` (exit code 1).
- **No logging framework.** A CLI that prints results and errors does not need SLF4J; adding it would be a
  dependency without a use. Revisit if a long-running mode is added.

## GZIP vs ZIP

- **GZIP** is a compression *format* for a single byte stream: header, Deflate data, CRC-32 trailer. It stores no
  file names or directories. Use it for one file.
- **ZIP** is an *archive container*: many named entries, each compressed separately (usually with Deflate), plus
  a central directory. Use it for multiple files and directories.
- Both use the **Deflate** algorithm here; the difference is the container, not the compression.
