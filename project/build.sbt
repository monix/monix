libraryDependencies += "org.yaml" % "snakeyaml" % "2.3"

// Resolve version conflicts in build plugins
ThisBuild / libraryDependencySchemes ++= Seq(
  "com.lihaoyi" %% "geny" % VersionScheme.Always
)
