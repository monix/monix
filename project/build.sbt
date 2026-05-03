libraryDependencies += "org.yaml" % "snakeyaml" % "2.6"

// Resolve version conflicts in build plugins
ThisBuild / libraryDependencySchemes ++= Seq(
  "com.lihaoyi" %% "geny" % VersionScheme.Always
)
