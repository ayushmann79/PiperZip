# Release process

Versions follow [semantic versioning](https://semver.org): `MAJOR.MINOR.PATCH`. Day-to-day work happens on
`X.Y.Z-SNAPSHOT`.

1. Make sure `main` is green (CI and Security workflows).
2. Prepare the release commit on a branch / PR:
   - set the version in `pom.xml` to the release version (for example `0.1.0`),
   - move the `CHANGELOG.md` entries from *Unreleased* to the new version.
3. Merge the PR into `main`.
4. Tag that commit and push the tag:
   ```bash
   git checkout main && git pull
   git tag -a v0.1.0 -m "PiperZip 0.1.0"
   git push origin v0.1.0
   ```
5. `release.yml` runs: validates the tag (semver, matches `pom.xml`, commit is on `main`), runs the full CI
   workflow again, builds the JAR with the commit date as build timestamp, writes `SHA256SUMS`, and creates the
   GitHub Release with generated notes. Tags containing `-` (for example `v0.2.0-rc.1`) become pre-releases.
6. Open a PR bumping `pom.xml` to the next `-SNAPSHOT`.

If validation fails, fix the cause, delete the tag (`git push --delete origin v0.1.0`, `git tag -d v0.1.0`) and tag
again. Never move a tag of an already published release; publish a new patch version instead.

## Verifying a download

```bash
sha256sum --check SHA256SUMS        # Linux
shasum -a 256 -c SHA256SUMS         # macOS
```
```powershell
Get-FileHash .\piperzip-0.1.0.jar -Algorithm SHA256   # compare with SHA256SUMS
```
The checksum file is published next to the JAR by the same workflow, so it protects against corrupted downloads,
not against a compromised release. Build provenance attestations are a roadmap item.

## Testing the workflow safely

Use a pre-release tag on a throwaway commit (for example `v0.0.1-test.1`) on a fork or scratch repository, with the
POM version set to match. Delete the release and tag afterwards.
