# How-To

## Releasing / Publishing to Sonatype

### Snapshots

Set the version in `version.sbt`. Development versions must end in `-SNAPSHOT`.

Run the `manual-publish` workflow with the ref to publish and disable `stable_version`. The workflow runs `ci-snapshot`, which cross-publishes signed artifacts directly to Central's snapshot repository under the version from `version.sbt`.

Snapshot publishing must be enabled for the `io.monix` namespace in the [Central Portal](https://central.sonatype.com/).

### Stable releases

Set the release version in `version.sbt` without the `-SNAPSHOT` suffix. Run the `manual-publish` workflow with `ref_to_publish` set to the full tag ref, such as `refs/tags/v3.5.0`, and enable `stable_version`.

The workflow runs `ci-release`, which cross-publishes signed artifacts, uploads the bundle to the Central Portal, and requests automatic publication.

## Repository setup

### GitHub Actions secrets

Publishing requires these repository secrets:

- `SONATYPE_USERNAME` and `SONATYPE_PASSWORD` contain the generated username and password from a [Central Portal user token](https://central.sonatype.com/usertoken). They are not the Central Portal login credentials.
- `PGP_KEY_HEX` identifies the signing key.
- `PGP_SECRET` contains the base64-encoded private key.
- `PGP_PASSPHRASE` contains the private key's passphrase.

The signing key's public key must be available from a [key server supported by Central](https://central.sonatype.org/publish/requirements/gpg/#distributing-your-public-key).

### Create GitHub App

Renovate and OpenCode authenticate as the `Monix Bot` GitHub App so
their pull requests, comments, commits, and pushes are not attributed to a
personal account.

Create an organization-owned GitHub App at:

<https://github.com/organizations/monix/settings/apps/new>

Use these settings:

- Disable webhooks.
- Allow installation only on the owning account.
- Install the app only on `monix/newtypes`.
- Grant `Members: read-only` as an organization permission.
- Grant these repository permissions:
  - `Administration: read-only`
  - `Checks: read and write`
  - `Commit statuses: read and write`
  - `Contents: read and write`
  - `Dependabot alerts: read-only`
  - `Issues: read and write`
  - `Pull requests: read and write`
  - `Workflows: read and write`
  - `Metadata: read-only`, which GitHub grants automatically

`Contents: read and write` permits Git pushes. `Workflows: read and write` is
also required when a commit changes files under `.github/workflows`.

After creating the app, generate a private key and configure these repository
Actions values under **Settings > Secrets and variables > Actions**:

- Secret `AUTOMATION_APP_ID`: the GitHub App ID
- Secret `AUTOMATION_APP_PRIVATE_KEY`: the complete downloaded PEM private key,
  including its `BEGIN` and `END` lines

The workflows exchange these values for a repository-scoped installation token
using `actions/create-github-app-token`. The token expires after one hour and
the action revokes it when the job finishes.
