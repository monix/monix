import java.text.SimpleDateFormat
import java.util.Date

import com.typesafe.sbt.SbtSite.SiteKeys._
import com.typesafe.sbt.pgp.PgpKeys
import com.typesafe.sbt.site.PreprocessSupport._
import sbtunidoc.Plugin.UnidocKeys._
import sbtunidoc.Plugin.{ScalaUnidoc, unidocSettings => baseUnidocSettings}

lazy val doNotPublishArtifact = Seq(
  publishArtifact := false,
  publishArtifact in (Compile, packageDoc) := false,
  publishArtifact in (Compile, packageSrc) := false,
  publishArtifact in (Compile, packageBin) := false
)

lazy val sharedSettings = Seq(
  organization := "org.monifu",
  scalaVersion := "2.11.7",
  crossScalaVersions := Seq("2.11.7", "2.10.6"),
  javacOptions ++= Seq("-source", "1.6", "-target", "1.6"),
  scalacOptions ++= Seq(
    "-target:jvm-1.6", // generates code with the Java 6 class format
    "-optimise", // enables optimisations
    // warnings
    "-unchecked", // able additional warnings where generated code depends on assumptions
    "-deprecation", // emit warning for usages of deprecated APIs
    "-feature", // emit warning usages of features that should be imported explicitly
    // possibly deprecated options
    "-Yinline-warnings",
    "-Ywarn-dead-code",
    "-Ywarn-inaccessible"
  ),

  // version specific compiler options
  scalacOptions ++= (CrossVersion.partialVersion(scalaVersion.value) match {
    case Some((2, majorVersion)) if majorVersion >= 11 =>
      Seq(
        // turns all warnings into errors ;-)
        "-Xfatal-warnings",
        // enables linter options
        "-Xlint:adapted-args", // warn if an argument list is modified to match the receiver
        "-Xlint:nullary-unit", // warn when nullary methods return Unit
        "-Xlint:inaccessible", // warn about inaccessible types in method signatures
        "-Xlint:nullary-override", // warn when non-nullary `def f()' overrides nullary `def f'
        "-Xlint:infer-any", // warn when a type argument is inferred to be `Any`
        "-Xlint:missing-interpolator", // a string literal appears to be missing an interpolator id
        "-Xlint:doc-detached", // a ScalaDoc comment appears to be detached from its element
        "-Xlint:private-shadow", // a private field (or class parameter) shadows a superclass field
        "-Xlint:type-parameter-shadow", // a local type parameter shadows a type already in scope
        "-Xlint:poly-implicit-overload", // parameterized overloaded implicit methods are not visible as view bounds
        "-Xlint:option-implicit", // Option.apply used implicit view
        "-Xlint:delayedinit-select", // Selecting member of DelayedInit
        "-Xlint:by-name-right-associative", // By-name parameter of right associative operator
        "-Xlint:package-object-classes", // Class or object defined in package object
        "-Xlint:unsound-match" // Pattern match may not be typesafe
      )
    case _ =>
      Seq.empty
  }),

  // Turning off fatal warnings for ScalaDoc, otherwise we can't release.
  scalacOptions in (Compile, doc) ~= (_ filterNot (_ == "-Xfatal-warnings")),

  // ScalaDoc settings
  autoAPIMappings := true,
  scalacOptions in ThisBuild ++= Seq(
    // Note, this is used by the doc-source-url feature to determine the
    // relative path of a given source file. If it's not a prefix of a the
    // absolute path of the source file, the absolute path of that file
    // will be put into the FILE_SOURCE variable, which is
    // definitely not what we want.
    "-sourcepath", file(".").getAbsolutePath.replaceAll("[.]$", "")
  ),

  parallelExecution in Test := false,
  parallelExecution in IntegrationTest := false,
  testForkedParallel in Test := false,
  testForkedParallel in IntegrationTest := false,
  concurrentRestrictions in Global += Tags.limit(Tags.Test, 1),

  resolvers ++= Seq(
    "Typesafe Releases" at "http://repo.typesafe.com/typesafe/releases",
    Resolver.sonatypeRepo("releases")
  ),

  // -- Settings meant for deployment on oss.sonatype.org

  publishMavenStyle := true,
  releasePublishArtifactsAction := PgpKeys.publishSigned.value,
  releaseCrossBuild := true,

  publishTo := {
    val nexus = "https://oss.sonatype.org/"
    if (isSnapshot.value)
      Some("snapshots" at nexus + "content/repositories/snapshots")
    else
      Some("releases"  at nexus + "service/local/staging/deploy/maven2")
  },

  publishArtifact in Test := false,
  pomIncludeRepository := { _ => false }, // removes optional dependencies

  pomExtra :=
    <url>https://monix.io/</url>
    <licenses>
      <license>
        <name>Apache License, Version 2.0</name>
        <url>https://www.apache.org/licenses/LICENSE-2.0</url>
        <distribution>repo</distribution>
      </license>
    </licenses>
    <scm>
      <url>git@github.com:monixio/monix.git</url>
      <connection>scm:git:git@github.com:monixio/monix.git</connection>
    </scm>
    <developers>
      <developer>
        <id>alex_ndc</id>
        <name>Alexandru Nedelcu</name>
        <url>https://bionicspirit.com/</url>
      </developer>
    </developers>
)

lazy val crossSettings = sharedSettings ++ Seq(
  libraryDependencies <+= scalaVersion("org.scala-lang" % "scala-reflect" % _ % "compile"),
  unmanagedSourceDirectories in Compile <+= baseDirectory(_.getParentFile / "shared" / "src" / "main" / "scala"),
  unmanagedSourceDirectories in Test <+= baseDirectory(_.getParentFile / "shared" / "src" / "test" / "scala")
)

lazy val scalaMacroDependencies = Seq(
  libraryDependencies ++= Seq(
    "org.scala-lang" % "scala-reflect" % scalaVersion.value % "provided",
    "org.scala-lang" % "scala-compiler" % scalaVersion.value % "provided",
    "org.typelevel" %% "macro-compat" % "1.1.1" % "provided",
    compilerPlugin("org.scalamacros" % "paradise" % "2.1.0" cross CrossVersion.full)
  ))

lazy val unidocSettings = baseUnidocSettings ++ Seq(
  autoAPIMappings := true,
  unidocProjectFilter in (ScalaUnidoc, unidoc) :=
    inProjects(executionJVM, tasksJVM, streamsJVM),

  scalacOptions in (ScalaUnidoc, unidoc) +=
    "-Xfatal-warnings",
  scalacOptions in (ScalaUnidoc, unidoc) +=
    "-Ymacro-expand:none",
  scalacOptions in (ScalaUnidoc, unidoc) ++=
    Opts.doc.title(s"Sincron"),
  scalacOptions in (ScalaUnidoc, unidoc) ++=
    Opts.doc.sourceUrl(s"https://github.com/monixio/monix/tree/v${version.value}€{FILE_PATH}.scala"),
  scalacOptions in (ScalaUnidoc, unidoc) ++=
    Seq("-doc-root-content", file("docs/rootdoc.txt").getAbsolutePath),
  scalacOptions in (ScalaUnidoc, unidoc) ++=
    Opts.doc.version(s"${version.value}")
)

lazy val docsSettings =
  unidocSettings ++
  site.addMappingsToSiteDir(mappings in (ScalaUnidoc, packageDoc), "api") ++
  site.addMappingsToSiteDir(tut, "_tut") ++
  Seq(
    (test in Test) <<= (test in Test).dependsOn(tut),
    coverageExcludedFiles := ".*",
    siteMappings += file("CONTRIBUTING.md") -> "contributing.md",
    includeFilter in makeSite :=
      "*.html" | "*.css" | "*.scss" | "*.png" | "*.jpg" | "*.jpeg" |
        "*.gif" | "*.svg" | "*.js" | "*.swf" | "*.yml" | "*.md" | "*.xml",

    preprocessVars := {
      val now = new Date()
      val dayFormat = new SimpleDateFormat("yyyy-MM-dd")
      val timeFormat = new SimpleDateFormat("HH:mm:ss")

      Map(
        "VERSION" -> version.value,
        "DATE" -> dayFormat.format(now),
        "TIME" -> timeFormat.format(now)
      )
    }
  )

lazy val testSettings = Seq(
  testFrameworks += new TestFramework("minitest.runner.Framework"),
  libraryDependencies += "io.monix" %%% "minitest" % "0.16" % "test"
)

lazy val scalaJSSettings = Seq(
  scalaJSUseRhino in Global := false,
  coverageExcludedFiles := ".*"
)

lazy val scalaStyleSettings = {
  // Create a default Scala style task to run with tests
  lazy val testScalastyle = taskKey[Unit]("testScalastyle")

  Seq(
    testScalastyle := org.scalastyle.sbt.ScalastylePlugin.scalastyle.in(Test).toTask("").value,
    (test in Test) <<= (test in Test) dependsOn testScalastyle
  )
}

lazy val monix = project.in(file("."))
  .aggregate(
    executionJVM, executionJS,
    tasksJVM, tasksJS,
    streamsJVM, streamsJS,
    docs, tckTests)
  .settings(sharedSettings)
  .settings(doNotPublishArtifact)
  .settings(scalaStyleSettings)

lazy val executionJVM = project.in(file("monix-execution/jvm"))
  .settings(crossSettings)
  .settings(testSettings)
  .settings(scalaMacroDependencies)
  .settings(
    name := "monix-execution",
    libraryDependencies += "org.sincron" %%% "sincron" % "0.10"
  )

lazy val executionJS = project.in(file("monix-execution/js"))
  .enablePlugins(ScalaJSPlugin)
  .settings(crossSettings)
  .settings(scalaJSSettings)
  .settings(testSettings)
  .settings(scalaMacroDependencies)
  .settings(
    name := "monix-execution",
    libraryDependencies += "org.sincron" %%% "sincron" % "0.10"
  )

lazy val tasksCommon =
  crossSettings ++ testSettings ++
  Seq(name := "monix-tasks")

lazy val tasksJVM = project.in(file("monix-tasks/jvm"))
  .dependsOn(executionJVM)
  .settings(tasksCommon)

lazy val tasksJS = project.in(file("monix-tasks/js"))
  .enablePlugins(ScalaJSPlugin)
  .dependsOn(executionJS)
  .settings(scalaJSSettings)
  .settings(tasksCommon)

lazy val streamsCommon =
  crossSettings ++ testSettings ++ scalaMacroDependencies ++ Seq(
    name := "monix-streams",
    libraryDependencies += "com.github.mpilquist" %%% "simulacrum" % "0.7.0"
  )

lazy val streamsJVM = project.in(file("monix-streams/jvm"))
  .dependsOn(executionJVM, tasksJVM)
  .settings(streamsCommon)
  .settings(libraryDependencies += "org.reactivestreams" % "reactive-streams" % "1.0.0")

lazy val streamsJS = project.in(file("monix-streams/js"))
  .enablePlugins(ScalaJSPlugin)
  .dependsOn(executionJS, tasksJS)
  .settings(streamsCommon)
  .settings(scalaJSSettings)

lazy val docs = project.in(file("docs"))
  .dependsOn(executionJVM, tasksJVM, streamsJVM)
  .settings(sharedSettings)
  .settings(doNotPublishArtifact)
  .settings(site.settings)
  .settings(tutSettings)
  .settings(docsSettings)

lazy val tckTests = project.in(file("tckTests"))
  .dependsOn(streamsJVM)
  .settings(sharedSettings)
  .settings(doNotPublishArtifact)
  .settings(
    libraryDependencies ++= Seq(
      "org.reactivestreams" % "reactive-streams-tck" % "1.0.0" % "test",
      "org.scalatest" %% "scalatest" % "2.2.4" % "test"
    ))

lazy val benchmarks = project.in(file("benchmarks"))
  .dependsOn(streamsJVM)
  .enablePlugins(JmhPlugin)
  .settings(sharedSettings)
  .settings(doNotPublishArtifact)
  .settings(
    libraryDependencies ++= Seq(
      "org.monifu" %% "monifu" % "1.0",
      "org.scalaz" %% "scalaz-concurrent" % "7.2.0",
      "io.reactivex" %% "rxscala" % "0.26.0"
    ))
