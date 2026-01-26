libraryDependencies += "org.yaml" % "snakeyaml" % "2.5"

// Resolve version conflicts in build plugins
ThisBuild / libraryDependencySchemes ++= Seq(
  "com.lihaoyi" %% "geny" % VersionScheme.Always
)
