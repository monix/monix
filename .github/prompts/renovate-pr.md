Review and fix this Renovate dependency update PR.

Hard requirements:

- Read and follow `AGENTS.md` and any project-specific `AGENTS.md` files.
- Treat this PR as a dependency maintenance PR only. Do not make unrelated
  refactors or feature changes.
- Preserve public API and binary compatibility.
- Inspect the dependency/build-tool updates already made by Renovate.
- Run `make check-all`.
- If the build, formatting, compilation, or tests fail, fix the failures on
  this PR branch.
- If no code changes are needed, leave the branch unchanged.
- Do not merge or close the PR.

When finished, summarize what you checked, what you fixed, and whether
`make check-all` passes.
