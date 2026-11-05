# Coding standards

This document supplements `AGENTS.md`. You MUST follow both when changing this repository.

The key words "MUST", "MUST NOT", "REQUIRED", "SHALL", "SHALL NOT", "SHOULD",
"SHOULD NOT", "RECOMMENDED", "MAY", and "OPTIONAL" in this document are to be
interpreted as described in [RFC 2119](https://datatracker.ietf.org/doc/html/rfc2119).

## Editing this document

- You MUST add only rules explicitly requested by a maintainer.
- You MUST keep each section about one rule.
- Every example MUST be minimal. Each line MUST help demonstrate that rule.
- You MUST use a `diff` when showing how to correct a code pattern.
- You MUST use RFC 2119 keywords for requirements.
- You MUST ask before editing if the intended rule or example is unclear.

## Compiler errors

You MUST NOT work around the compiler. You SHOULD solve errors in an idiomatic way.

## Downcasting

You SHOULD NOT use `asInstanceOf` downcasting unless there is no other way (e.g., untagged union types in Scala 3).

## Warning suppression

You MUST NOT use `@nowarn` annotations or other ways of suppressing warnings or errors without the user's consent. You MUST fix warnings instead of ignoring them.

## Imports

You MUST use package imports instead of fully qualified names.
