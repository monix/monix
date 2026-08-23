# Releasing Monix

Monix uses `sbt-dynver`. A stable version comes from an annotated `vX.Y.Z` Git tag. Other refs produce snapshot versions.

## One-time setup before the next release

Do not publish until the following work is complete:

- Sign in to the [Central Portal](https://central.sonatype.com/) with the existing OSSRH account. Confirm that the account can publish the `io.monix` namespace and migrate the namespace if Sonatype still lists it under OSSRH.
- Generate a [Central Portal user token](https://central.sonatype.com/usertoken). Store its generated username and password in the `SONATYPE_USERNAME` and `SONATYPE_PASSWORD` GitHub Actions secrets. Do not use the Portal login password.
- Replace the release signing key. The private key with fingerprint `2837 FA5C 4BA6 692A B71F 6009 F376 6EF5 3E70 15B9`, used to sign Monix 3.4.1, was committed to this repository. Deleting the current copy does not remove it from Git history. Treat the key as compromised, revoke it after preparing a replacement, and publish the replacement public key to a [key server supported by Central](https://central.sonatype.org/publish/requirements/gpg/#distributing-your-public-key).
- Update `PGP_KEY_HEX`, `PGP_SECRET`, and `PGP_PASSPHRASE` in GitHub Actions for the replacement key. `PGP_SECRET` must contain the base64-encoded private key.
- If snapshots will be published, enable snapshot publishing for `io.monix` in the Central Portal.

The old OSSRH credentials and account password do not work with the Central Portal publishing API.

## Prepare a stable release

1. Finish `CHANGES.md` and confirm the intended version.
2. Run the full build:

   ```bash
   sbt ci-all
   ```

3. Package one cross-built Scaladoc artifact as a publishing check:

   ```bash
   sbt "+coreJVM/Compile/packageDoc"
   ```

4. Create and push an annotated tag on the release commit:

   ```bash
   git tag -a vX.Y.Z -m "Monix X.Y.Z"
   git push origin vX.Y.Z
   ```

5. Wait for the tag's `build` workflow to pass.
6. Run the `manual-publish` workflow with `ref_to_publish` set to `refs/tags/vX.Y.Z` and `stable_version` enabled.
7. Check the deployment in the [Central Portal](https://central.sonatype.com/publishing/deployments). The workflow uses automatic publication, so a successful run should reach the `PUBLISHED` state without a manual Portal action.
8. Confirm the artifacts under [`io.monix` on Maven Central](https://repo1.maven.org/maven2/io/monix/).

The release task runs `clean`, cross-publishes signed artifacts to sbt's local staging directory, uploads one bundle, and waits for Central to publish it. Central releases are immutable. Do not reuse a version.

## Publish a snapshot

Run the `manual-publish` workflow with a branch or commit ref and disable `stable_version`. Snapshots publish directly to Central's snapshot repository and do not create a Portal deployment.

Central removes snapshots after 90 days. Snapshot publishing must be enabled for the namespace before the workflow runs.
