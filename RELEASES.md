## Release Process

This section is for maintainers.

For the release process you need to activate Git signing, see 
[the tutorial on GitHub](https://help.github.com/articles/signing-commits-using-gpg/).

Also add these to your `$HOME/.sbt/1.0/build.sbt`:

```scala
credentials += Credentials(
  "Sonatype Nexus Repository Manager",
  "oss.sonatype.org",
  "USERNAME",
  "PASSWORD"
)
```

You might also need to configure the [sbt-pgp](http://www.scala-sbt.org/sbt-pgp/)
plugin. Even if it's included in `plugins.sbt`, you might want to tune it according
to your local setup. So if it doesn't work out, you can try playing its settings.

For example you could also try adding these in `$HOME/.sbt/1.0/build.sbt`:

```scala
useGpg := true
useGpgAgent := true
```

Plus if you do that, you'd need to add the plugin globally as well, so 
according to the official docs you need to edit 
`$HOME/.sbt/1.0/plugins/gpg.sbt` and add something like:

```scala
addSbtPlugin("com.jsuereth" % "sbt-pgp" % "1.1.0")
```

You can then test that you can sign packages locally with:

```
sbt +publishLocalSigned
```

In order to release a new version, these commands need to be executed:

```
VERSION="v3.0.0"

git tag -s -m "Tagging $VERSION" $VERSION

git verify-tag $VERSION

git checkout $VERSION

sbt release

git push upstream $VERSION
```

Please execute one by one, at each step verify that there haven't been any errors.

## Cryptographically Verifying Releases

All release artifacts must be cryptographically signed by a GPG key from one of [the maintainers](https://github.com/monix/monix/graphs/contributors).  Every release corresponds to a tag of the form `/v(\d+)\.(\d+)(\.(\d+))?` which is pushed to [the upstream Git repository](https://github.com/monix/monix), and that tag is always signed by the *same* key.

To locally cryptographically verify the integrity of a release, you should start by verifying the tag itself:

```bash
$ git verify-tag v3.0.0
```

(replace `v3.0.0` with the version you're checking)

The output should be something like this:

```
gpg: Signature made Mon Jan 29 13:19:48 2018 EET
gpg:                using RSA key 971E5587E7EDA30AE2F0C230397C67E28DFD7BB4
gpg: Good signature from "Alexandru Nedelcu <noreply@alexn.org>" [ultimate]
gpg:                 aka "[jpeg image of size 4363]" [ultimate]
```

Note the "*using RSA key*" line, which is the signature of the signing key.  You can also look at this tag on Github and, if you trust their servers, verify that it is linked to a profile you trust.  An even better way of doing this is to visit [Keybase](https://keybase.io) and search for that 8 character signature, since this can be done without trusting any third parties (or rather, without trusting any single third party).

Once you've verified that the key signature matches someone you would expect to be releasing `monix` artifacts, you should import the key to pin it for subsequent verifications and note that only the 8 characters are needed:

```bash
$ gpg --recv-keys 8DFD7BB4
```

(replace those eight characters with the signature from above)

It's always a good exercise to take that primary key fingerprint (all 120 characters) and ensure that it matches the other key sources (e.g. Keybase).  It is relatively easy to generate keys which signature collide on the final eight bits.

Now that you've grabbed the signature of the tag and verified that it correspond to an individual you would *expect* should be pushing `monix` releases, you can move on to verifying the artifacts themselves.

```bash
sbt check-pgp-signatures
```

You will need the [sbt-gpg](http://www.scala-sbt.org/sbt-pgp/index.html) plugin to run this command.  It will grab all of the signatures for all of your dependencies and verify them.  Each one should indicate either `[OK]` or `[UNTRUSTED(...)]`.  Each `UNTRUSTED` artifact will list the signature of the signing key, just as with the tag verification.  Since we have already imported the key of the developer who signed the release tag, we should *definitely* see `[OK]` for the `monix-kafka` artifact:

```
[info]    io.monix :    monix_2.12 :    3.0.0 : jar   [OK]
```

If you do see `UNTRUSTED` (which will happen if you don't import the key), it should look like the following:

```
[info]    io.monix :    monix_2.12 :    3.0.0 : jar   [UNTRUSTED(0x2bae5960)]
```
