# Security

## Threat model

PiperZip may be pointed at archives from untrusted sources. The main risks are an archive that writes outside the
destination, one that exhausts disk/memory/inodes, and tooling mistakes (partial files, accidental overwrites).

## What `archive extract` does

| Threat | Defence |
|---|---|
| **ZIP Slip / path traversal** (`../../x`) | Every entry name is rejected if it contains a `..` segment, starts with `/`, has a drive letter, contains a backslash or NUL, or resolves outside the destination after normalisation. All names are validated **before** anything is written. |
| **Symbolic links** | Links stored in an archive are never created (entries are written as regular files only). An existing symlink inside the destination is never written through; this is checked at planning time and again at commit time. |
| **Decompression bombs** | Limits are enforced on bytes *actually produced*, not on sizes the archive claims: max entries (10,000), max total bytes (1 GiB), max overall ratio (200:1, only checked after 16 MiB). All configurable (`--max-entries`, `--max-total-bytes`, `--max-ratio`). |
| **Duplicate / conflicting entries** | Duplicates (including `a/b` vs `a//b`) and file/directory conflicts are rejected. |
| **Partial output** | Files are decompressed into a private staging directory inside the destination and moved into place only after the whole archive validated. Any failure deletes the staging directory. |
| **Corrupt data** | CRC-32 and size are verified per entry; truncated archives fail when the central directory is read. |
| **Accidental overwrite** | Existing files are never replaced without `--force`; the check happens before extraction. |
| **System directories** | Extraction into a filesystem root or common system directories (`/etc`, `/usr`, `/bin`, Windows `%SystemRoot%`, ...) is refused. |
| **Dangerous permissions** | Extracted files get default permissions; stored permission bits (setuid, executable) are ignored. |

## Limitations (be honest about them)

- The limits are heuristics. An attacker can stay just under them; extract untrusted archives into a disposable,
  quota-limited location.
- A local attacker who can modify the destination *while* extraction runs can race the symlink checks (TOCTOU).
- The commit phase is a sequence of renames, not one atomic transaction. If it fails halfway (very unlikely on
  one filesystem) some files will already be in place.
- If the JVM is killed mid-extraction, a `.piperzip-staging-*` directory may remain and can be deleted by hand.
- `ProtectedPaths` is a safety net against typos, not a security boundary.
- GZIP decompression has no size limit; decompressing hostile `.gz` files can fill a disk.
- File names that the JVM cannot represent (for example non-ASCII names under the POSIX "C" locale) are rejected
  as invalid paths.
- Hashing proves two files are identical; it does **not** prove they are trustworthy or authentic.

## Pipeline security

| Technique | What it finds | In this repo |
|---|---|---|
| **Static analysis (SAST)** | Bug and vulnerability patterns in *our* source without running it | SpotBugs (`ci.yml`), CodeQL (`security.yml`) |
| **Dependency scanning (SCA)** | Known vulnerabilities in *libraries we use* | Dependency Review on PRs, Dependabot alerts and updates |
| **Secret scanning** | Credentials committed to the repo or its history | gitleaks in `security.yml` (checksum-pinned binary); also enable GitHub secret scanning and push protection |
| **Runtime / dynamic testing (DAST, fuzzing)** | Behaviour of the *running* program under hostile input | Not automated yet. The hostile-archive unit tests are the closest thing; a fuzzing job is on the roadmap |

Workflow hardening: least-privilege `permissions:` everywhere (only the release job gets `contents: write`, and
only CodeQL gets `security-events: write`); every third-party action pinned to a full commit SHA with the release
in a comment; `persist-credentials: false` on checkout; no `pull_request_target`; no secrets used at all, so fork
PRs cannot leak any; the release workflow runs only on version tags that are on `main`.

Reporting vulnerabilities: see `SECURITY.md`.
