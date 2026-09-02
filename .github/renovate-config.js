module.exports = {
  platform: "github",
  repositories: ["monix/monix"],
  branchPrefix: "renovate/",
  onboarding: false,
  requireConfig: "optional",
  recreateWhen: "always",
  prHourlyLimit: 0,
  separateMajorMinor: false,

  extends: [":dependencyDashboard"],

  enabledManagers: ["github-actions", "sbt"],

  ignorePaths: ["**/.gradle/**"],

  packageRules: [
    {
      description: "Group all dependency updates into a single PR",
      matchManagers: ["github-actions", "sbt"],
      groupName: "dependencies",
      groupSlug: "all-dependencies",
      group: {
        commitMessageTopic: "dependencies",
        commitMessageExtra: "",
      },
    },
    {
      description: "Only use stable dotted numeric JVM dependency versions",
      matchManagers: ["sbt"],
      allowedVersions: "/^\\d+(?:\\.\\d+)+$/",
    },
    {
      description: "Keep sbt on the 1.x line (2.x is not yet supported)",
      matchManagers: ["sbt"],
      matchPackageNames: ["org.scala-sbt:sbt", "sbt/sbt"],
      allowedVersions: "/^1\\.\\d+\\.\\d+$/",
    },
    {
      description: "Keep Cats Effect on the 2.x line",
      matchManagers: ["sbt"],
      matchPackagePrefixes: ["org.typelevel:cats-effect"],
      allowedVersions: "/^2\\.\\d+\\.\\d+$/",
    },
    {
      description: "Keep Scala 2 on the 2.13.x line",
      matchManagers: ["sbt"],
      matchPackageNames: ["org.scala-lang:scala-library"],
      allowedVersions: "/^2\\.13\\.\\d+$/",
    },
    {
      description: "Ignore derived Scala binary versions",
      matchManagers: ["sbt"],
      matchPackageNames: ["org.scala-lang:scala-library"],
      matchCurrentValue: "/^\\d+\\.\\d+$/",
      enabled: false,
    },
    {
      description: "Keep Scala on the 3.8.x line",
      matchManagers: ["sbt"],
      matchPackageNames: ["org.scala-lang:scala3-library_3"],
      allowedVersions: "/^3\\.8\\.\\d+$/",
    },
    {
      description: "Disable updates for libraryDependencySchemes entries (not real versions)",
      matchManagers: ["sbt"],
      matchCurrentValue: "/^(early-semver|semver-spec|pvp|always|strict)$/",
      enabled: false,
    },
    {
      description: "Wait one week before proposing dependency updates",
      matchManagers: ["github-actions", "sbt"],
      minimumReleaseAge: "7 days",
      minimumReleaseAgeBehaviour: "timestamp-optional",
    },
  ],
};
