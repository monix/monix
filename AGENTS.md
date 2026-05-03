# Agent Instructions for Monix

This file contains mandatory rules for AI agents (Copilot, Claude, Cursor, etc.) working on this repository.
**Any violation is a CI-breaking mistake!**

---

## Code rules

- Never workaround the compiler, make an effort to solve errors in an idiomatic way:
  - Avoid `asInstanceOf` downcasting, unless there's no other way (e.g., untagged union types in Scala 3).
  - `@nowarn` annotations, or other ways for supressing warnings/errors, are not permitted without the user's consent.
    - We fix warnings, we don't ignore them.
- Use package imports, instead of fully qualified names.
- Make an effort to write idiomatic, yet performant Scala code.

## HOW-TOs

### Upgrade Dependencies

Run this task:
```
make dependency-updates
```
This will generate a set of text files named "dependency-updates.txt" in every sub-project, with the dependencies that can be upgraded (in `build.sbt` or `project/plugins.sbt`)

RULES: 
- Never upgrade major versions (semver), instead ask the user or warn them!!!
- Never upgrade to SNAPSHOT, RC, or milestone versions.
- Fix breakage, but apply good judgement.
- Scala versions get updated in CI workflow definitions (`build.yml`)
