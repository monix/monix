# Issue tracker: GitHub

Issues and specs live in GitHub Issues for `monix/monix`.
Use the `gh` CLI from this repository.

## Conventions

- Create an issue: `gh issue create --title "..." --body-file <file>`.
- Read an issue: `gh issue view <number> --comments`.
- List issues: `gh issue list --state open --json number,title,body,labels,comments`.
  Apply label and state filters as needed.
- Comment: `gh issue comment <number> --body-file <file>`.
- Add or remove labels: `gh issue edit <number> --add-label "..."`
  or `gh issue edit <number> --remove-label "..."`.
- Close an issue: `gh issue close <number> --comment "..."`.

Use a temporary file for multiline issue bodies and comments.
Infer the repository from Git remotes.

When a skill says "publish to the issue tracker", create a GitHub issue.
When it says "fetch the relevant ticket", read the issue and its comments.

## Pull requests as a triage surface

**PRs as a request surface: no.**

GitHub shares one number space across issues and pull requests.
If a reference is ambiguous, resolve its type before acting.

## Wayfinding operations

- Map: one issue labelled `wayfinder:map`, containing Notes,
  Decisions-so-far, and Fog.
- Child ticket: link it to the map as a GitHub sub-issue. If sub-issues
  are unavailable, use a task list in the map and add `Part of #<map>`
  to the child body.
- Ticket labels: `wayfinder:research`, `wayfinder:prototype`,
  `wayfinder:grilling`, or `wayfinder:task`.
- Blocking: use native GitHub issue dependencies. If unavailable,
  record `Blocked by: #<number>` references in the child body.
  A ticket is unblocked when all blockers are closed.
- Frontier: select the first open child in map order with no open
  blockers and no assignee.
- Claim: assign the selected ticket to the driving developer.
- Resolve: comment with the result, close the ticket, and append
  a short summary and link to the map's Decisions-so-far.
