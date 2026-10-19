# First push checklist (Milestone 4)

1. Verify the build locally: `mvn verify`. Fix anything it reports first; the plugin versions in `pom.xml` were
   not resolved against Maven Central when this project was generated.
2. Create an **empty public** repository named `piperzip` on GitHub (no README/licence; they already exist).
3. Push:
   ```bash
   git init -b main
   git add .
   git commit -m "feat: initial PiperZip implementation"
   git remote add origin https://github.com/<your-user>/piperzip.git
   git push -u origin main
   ```
4. Open the **Actions** tab and read the runs of `CI` and `Security`. Expect to fix small things on the first run;
   read the first red step's log (see `ci-cd-guide.md`, section 11).
5. Enable: Dependabot alerts, secret scanning and push protection, private vulnerability reporting
   (Settings → Code security).
6. Add branch protection for `main` once the check names appear (see `ci-cd-guide.md`, section 10).
7. Replace `ayushmann79` in `README.md` and `pom.xml` if your GitHub username differs.
8. Release when ready: follow `release-process.md`.
