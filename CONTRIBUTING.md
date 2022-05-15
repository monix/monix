# Contributing to Monix

The Monix project welcomes contributions from anybody wishing to participate.
All code or documentation that is provided must be licensed with Apache 2.0
(see `COPYING`).

## Code of Conduct

You should follow the [Scala Code of Conduct](https://www.scala-lang.org/conduct/)
when discussing Monix on the Github page, Gitter channel, or other venues.

We hope that our community will be respectful, helpful, and kind. If you find 
yourself embroiled in a situation that becomes heated, or that fails to live up 
to our expectations, you should disengage and contact one of the project maintainers 
in private. We hope to avoid letting minor aggressions and misunderstandings 
escalate into larger problems.

## General Workflow

1. Make sure you can license your work under Apache 2.0 

2. Before starting to work, make sure there is a ticket in the issue
   or create one first. It can help accelerate the acceptance process
   if the change is agreed upon

3. If you don't have write access to the repository, you should do
   your work in a local branch of your own fork and then submit a pull
   request. If you do have write access to the repository, never work
   directly on main `series/*` branches.
   
4. When the work is completed, verify it with following command:

```
sbt ci-all
```

If `mimaReportBinaryIssues` fails, it means there are binary incompatibilities.
- If you're working on the stable version (e.g. last released version is `3.x.x` or similar) then we will have to implement the change
in a way that passes this test. There are few useful guidelines [here](https://github.com/jatcwang/binary-compatibility-guide) 
but do not hesitate to submit a Pull Request anyway and ask Maintainers for help.
- If you're not working on the stable version (e.g. last released version is `3.0.0-RC3` or similar), just add proper filter
[here](project/MimaFilters.scala). You should be able to find it in the failure output.

5. Submit a Pull Request.

6. Anyone can comment on a pull request, and you are expected to
   answer questions or to incorporate feedback.

7. It is not allowed to force push to the branch on which the pull
   request is based.

## General Guidelines

1. We recommend for the work be accompanied by unit tests.

2. The commit messages should be clear and short one lines, if more
   details are needed, specify a body.

3. New source files should be accompanied by the copyright header.

4. Follow the structure of the code in this repository, and the
   indentation rules used.

5. Your first commit request should be accompanied with a change to
   the AUTHORS file, adding yourself to the authors list.
   
## Finding Starting Point

If you want to contribute but you don't know where to start - have a look at [low-hanging fruit](https://github.com/monix/monix/issues?q=is%3Aopen+is%3Aissue+label%3A%22low-hanging+fruit%22) or [help wanted](https://github.com/monix/monix/issues?q=is%3Aopen+is%3Aissue+label%3A%22help+wanted%22) issues.
If there aren't any, have unclear description or seem too complicated - visit [monix/monix](https://gitter.im/monix/monix) Gitter channel.
Gitter is a go-to place in case you have any questions or need guidance since we're more than happy to help new contributors regardless of their experience.
   
## License

All code must be licensed under the Apache 2.0 license and all files 
must include the following copyright header:

```
Copyright (c) 2014-$today.year by The Monix Project Developers.
See the project homepage at: https://monix.io

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```
