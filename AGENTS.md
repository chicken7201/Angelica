### Release workflow ownership

- Treat the local '.github/workflows/release-tags.yml' files as authoritative.
- Do not overwrite these workflows with upstream versions during synchronization or merges.
- If upstream changes conflict with local release automation, preserve the local versioning, tagging, build, and publishing behavior.
- Review upstream workflow changes separately and apply only compatible improvements.
- Do not introduce Discord notifications or CurseForge/Modrinth publishing without explicit approval.