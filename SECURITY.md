# Security policy

## Supported versions

Only the latest released version receives fixes.

## Reporting a vulnerability

Please report privately using GitHub's **"Report a vulnerability"** button on the repository's *Security* tab
(private vulnerability reporting must be enabled in the repository settings). Include the PiperZip version, Java
version, and, if possible, a minimal archive or command that reproduces the problem. Do not open a public issue for
vulnerabilities.

You can expect an acknowledgement within a few days. This is a volunteer-maintained project: there is no
guaranteed response time or bug bounty.

Scope: path traversal or writes outside the destination, limit bypasses, unsafe handling of corrupt input, and
workflow or supply-chain weaknesses. Known limitations are listed in [docs/security.md](docs/security.md).
