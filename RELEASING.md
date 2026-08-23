# Releasing Monix

## GitHub Actions secrets

Publishing requires these repository secrets:

- `SONATYPE_USERNAME` and `SONATYPE_PASSWORD` contain the generated username and password from a [Central Portal user token](https://central.sonatype.com/usertoken). They are not the Central Portal login credentials.
- `PGP_KEY_HEX` identifies the signing key.
- `PGP_SECRET` contains the base64-encoded private key.
- `PGP_PASSPHRASE` contains the private key's passphrase.

The signing key's public key must be available from a [key server supported by Central](https://central.sonatype.org/publish/requirements/gpg/#distributing-your-public-key).

## Snapshots

Set the version in `version.sbt`. Development versions must end in `-SNAPSHOT`; the current version is `3.5.0-SNAPSHOT`.

Every successful push to `main` runs `ci-snapshot`. The task cross-publishes signed artifacts directly to Central's snapshot repository under the version from `version.sbt`.

Snapshot publishing must be enabled for the `io.monix` namespace in the [Central Portal](https://central.sonatype.com/).

## Stable releases

Set the release version in `version.sbt` without the `-SNAPSHOT` suffix. Run the `manual-publish` workflow with `ref_to_publish` set to the full tag ref, such as `refs/tags/v3.5.0`, and enable `stable_version`.

The workflow runs `ci-release`, which cross-publishes signed artifacts, uploads the bundle to the Central Portal, and requests automatic publication.
