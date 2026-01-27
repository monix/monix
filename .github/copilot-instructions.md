# Copilot Instructions for Monix

## Project Overview

Monix is a high-performance Scala/Scala.js library for composing asynchronous, event-based programs. It's a Typelevel project that provides reactive programming abstractions with strong functional programming influences, designed for back-pressure and compatibility with Reactive Streams.

## Project Structure

This is a modular multi-project build with the following sub-projects:

- **monix-execution**: Low-level execution environment (Scheduler, Cancelable, Atomic, Local, etc.)
- **monix-catnap**: Pure abstractions built on Cats-Effect type classes
- **monix-eval**: Task and Coeval data types
- **monix-reactive**: Observable for reactive, push-based streams with back-pressure
- **monix-tail**: Iterant for purely functional pull-based streaming
- **monix**: Aggregates all of the above

Each module has JVM and JS (Scala.js) variants.

## Build System

This project uses **sbt** (Scala Build Tool). Key commands:

- `sbt compile` - Compile the project
- `sbt test` - Run tests
- `sbt ci-all` - Run all CI checks (JVM, JS, and meta checks)
- `sbt ci-jvm` - Run JVM-specific checks and tests
- `sbt ci-js` - Run Scala.js-specific checks and tests
- `sbt ci-meta` - Run MiMa binary compatibility checks and generate documentation

### Important Build Notes

- The project supports multiple Scala versions (2.13.x and 3.x)
- Always run `sbt ci-all` before submitting changes
- Binary compatibility is checked using MiMa (`mimaReportBinaryIssues`)

## Code Style and Formatting

- **Formatter**: The project uses Scalafmt version 3.5.2
- **Configuration**: See `.scalafmt.conf` for formatting rules
- **Max line length**: 120 characters
- **Formatting check**: `sbt scalafmtCheckAll scalafmtSbtCheck`
- **Auto-format**: `sbt scalafmtAll scalafmtSbt`

### Key Style Points

- Follow the existing code structure and indentation
- Use existing libraries whenever possible
- Add minimal comments unless they match the style of existing comments
- Preserve trailing commas as per existing code

## Testing

- Unit tests are required for new functionality
- Tests are located in test directories within each sub-project
- Run specific project tests: `sbt <projectName>/test`
- Additional test projects: `reactiveTests` and `tracingTests`

## Binary Compatibility

Monix maintains binary compatibility using MiMa (Migration Manager for Scala):

- **For stable versions**: Changes must pass `mimaReportBinaryIssues`
- **For RC versions**: Add appropriate filters in `project/MimaFilters.scala`
- See [Binary Compatibility Guide](https://github.com/jatcwang/binary-compatibility-guide) for help

## Contributing Guidelines

1. All code must be licensed under Apache 2.0
2. Create or reference an issue before starting work
3. Work in a feature branch, never directly on `series/*` branches
4. Include copyright header in new source files:
   ```scala
   /*
    * Copyright (c) 2014-<year> by The Monix Project Developers.
    * See the project homepage at: https://monix.io
    *
    * Licensed under the Apache License, Version 2.0 (the "License");
    * you may not use this file except in compliance with the License.
    * You may obtain a copy of the License at
    *
    *     http://www.apache.org/licenses/LICENSE-2.0
    *
    * Unless required by applicable law or agreed to in writing, software
    * distributed under the License is distributed on an "AS IS" BASIS,
    * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    * See the License for the specific language governing permissions and
    * limitations under the License.
    */
   ```
5. Update the AUTHORS file with your first contribution
6. Follow the Scala Code of Conduct

## Dependencies

Major dependencies include:

- Cats 2.7.0
- Cats-Effect 2.5.5
- FS2 2.5.11
- Reactive Streams 1.0.3

Only add new dependencies or update versions if absolutely necessary.

## Documentation

- **Website**: https://monix.io/
- **API Docs**: Generated using `sbt unidoc`
- Update documentation if changes affect public APIs

## Common Pitfalls

- Don't break binary compatibility on stable releases
- Always run formatters before committing
- Test both JVM and JS platforms when making cross-platform changes
- Consider the impact on all Scala versions (2.13.x and 3.x)

## CI/CD

The project uses GitHub Actions for CI:

- Tests run on multiple Java versions (8, 17) and Scala versions (2.13.18, 3.3.7)
- All checks must pass before merging
- Publishing to Sonatype happens automatically on tagged releases

## Getting Help

- Gitter: https://gitter.im/monix/monix
- Discord: https://discord.gg/wsVZSEx4Nw
- GitHub Issues: For bugs and feature requests
