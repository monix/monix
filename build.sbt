import com.typesafe.sbt.GitVersioning
import sbt.Keys.version

val allProjects = List(
  "execution",
  "catnap",
  "eval",
  "tail",
  "reactive",
  "java"
)

val benchmarkProjects = List(
  "benchmarksPrev",
  "benchmarksNext"
).map(_ + "/compile")

addCommandAlias("ci",          ";ci-jvm ;ci-js")
addCommandAlias("ci-all",      ";ci-jvm ;ci-js ;mimaReportBinaryIssues ;unidoc")
addCommandAlias("ci-js",       s";clean ;coreJS/test:compile ;${(allProjects.filter(_ != "java").map(_ + "JS/test") ++ benchmarkProjects).mkString(" ;")}")
addCommandAlias("ci-jvm",      s";clean ;coreJVM/test:compile ;${(allProjects.map(_ + "JVM/test") ++ benchmarkProjects).mkString(" ;")}")
addCommandAlias("ci-jvm-mima", s";ci-jvm ;mimaReportBinaryIssues")
addCommandAlias("ci-jvm-all",  s";ci-jvm-mima ;unidoc")
addCommandAlias("release",     ";project monix ;+clean ;+package ;+publishSigned")

lazy val catsVersion = settingKey[String]("cats version")
  ThisBuild/catsVersion := {
    CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((2, 11)) => "2.0.0"
      case _ => "2.1.1"
    }
  }

lazy val catsEffectVersion = settingKey[String]("cats-effect version")
  ThisBuild/catsEffectVersion := {
    CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((2, 11)) => "2.0.0"
      case _ => "2.1.3"
    }
  }

// For benchmarks
lazy val fs2Version = settingKey[String]("fs2 version")
  ThisBuild/fs2Version := {
    CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((2, 11)) => "2.1.0"
      case _ => "2.4.0"
    }
  }

val jcToolsVersion = "3.0.0"
val reactiveStreamsVersion = "1.0.3"
val minitestVersion = "2.8.2"
val scalaTestVersion = "3.0.8"
val implicitBoxVersion = "0.2.0"
val kindProjectorVersion = "0.11.0"
val betterMonadicForVersion = "0.3.1"
val silencerVersion = "1.7.0"
val customScalaJSVersion = Option(System.getenv("SCALAJS_VERSION"))

// The Monix version with which we must keep binary compatibility.
// https://github.com/typesafehub/migration-manager/wiki/Sbt-plugin
val monixSeries = "3.0.0"

lazy val doNotPublishArtifact = Seq(
  publishArtifact := false,
  publishArtifact in (Compile, packageDoc) := false,
  publishArtifact in (Compile, packageSrc) := false,
  publishArtifact in (Compile, packageBin) := false
)

lazy val sharedSettings = Seq(
  organization := "io.monix",
  scalaVersion := "2.13.3",
  crossScalaVersions := Seq("2.11.12", "2.12.12", "2.13.3"),

  // Enable this to debug warnings
  scalacOptions in Compile += "-Wconf:any:warning-verbose",
  // Disabled from the sbt-tpolecat set
  scalacOptions in Compile ~= { options: Seq[String] =>
    options.filterNot(
      Set(
        "-Wunused:privates",
        "-Ywarn-unused:privates",
        "-Ywarn-unused:implicits",
        "-Wunused:implicits",
        "-Ywarn-unused:imports",
        "-Wunused:explicits",
        "-Ywarn-unused:params",
        "-Wunused:params",
      )
    )
  },

  // Turning off fatal warnings for doc generation
  scalacOptions.in(Compile, doc) ~= filterConsoleScalacOptions,

  // For working with partially-applied types
  addCompilerPlugin("org.typelevel" % "kind-projector" % kindProjectorVersion cross CrossVersion.full),
  addCompilerPlugin("com.olegpy" %% "better-monadic-for" % betterMonadicForVersion),
  addCompilerPlugin("com.github.ghik" % "silencer-plugin" % silencerVersion cross CrossVersion.full),

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

  logBuffered in Test := false,
  logBuffered in IntegrationTest := false,

  resolvers ++= Seq(
    "Typesafe Releases" at "https://repo.typesafe.com/typesafe/releases",
    Resolver.sonatypeRepo("releases")
  ),

  // https://github.com/sbt/sbt/issues/2654
  incOptions := incOptions.value.withLogRecompileOnMacro(false),

  // -- Settings meant for deployment on oss.sonatype.org
  sonatypeProfileName := organization.value,

  credentials += Credentials(
    "Sonatype Nexus Repository Manager",
    "oss.sonatype.org",
    sys.env.getOrElse("SONATYPE_USER", ""),
    sys.env.getOrElse("SONATYPE_PASS", "")
  ),

  publishMavenStyle := true,
  publishTo := Some(
    if (isSnapshot.value)
      Opts.resolver.sonatypeSnapshots
    else
      Opts.resolver.sonatypeStaging
  ),

  isSnapshot := version.value endsWith "SNAPSHOT",
  publishArtifact in Test := false,
  pomIncludeRepository := { _ => false }, // removes optional dependencies

  licenses := Seq("APL2" -> url("http://www.apache.org/licenses/LICENSE-2.0.txt")),
  homepage := Some(url("https://monix.io")),
  headerLicense := Some(HeaderLicense.Custom(
    """|Copyright (c) 2014-2020 by The Monix Project Developers.
       |See the project homepage at: https://monix.io
       |
       |Licensed under the Apache License, Version 2.0 (the "License");
       |you may not use this file except in compliance with the License.
       |You may obtain a copy of the License at
       |
       |    http://www.apache.org/licenses/LICENSE-2.0
       |
       |Unless required by applicable law or agreed to in writing, software
       |distributed under the License is distributed on an "AS IS" BASIS,
       |WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
       |See the License for the specific language governing permissions and
       |limitations under the License."""
    .stripMargin)),

  scmInfo := Some(
    ScmInfo(
      url("https://github.com/monix/monix"),
      "scm:git@github.com:monix/monix.git"
    )),

  developers := List(
    Developer(
      id="alexelcu",
      name="Alexandru Nedelcu",
      email="noreply@alexn.org",
      url=url("https://alexn.org")
    ))
)

lazy val crossSettings = sharedSettings ++ Seq(
  unmanagedSourceDirectories in Compile += {
    baseDirectory.value.getParentFile / "shared" / "src" / "main" / "scala"
  },
  unmanagedSourceDirectories in Test += {
    baseDirectory.value.getParentFile / "shared" / "src" / "test" / "scala"
  }
)

def scalaPartV = Def setting (CrossVersion partialVersion scalaVersion.value)
lazy val crossVersionSharedSources: Seq[Setting[_]] =
  Seq(Compile, Test).map { sc =>
    (unmanagedSourceDirectories in sc) ++= {
      (unmanagedSourceDirectories in sc).value.flatMap { dir =>
        Seq(
          scalaPartV.value match {
            case Some((2, y)) if y == 11 => new File(dir.getPath + "_2.11")
            case Some((2, y)) if y == 12 => new File(dir.getPath + "_2.12")
            case Some((2, y)) if y >= 13 => new File(dir.getPath + "_2.13")
          },

          scalaPartV.value match {
            case Some((2, n)) if n >= 12 => new File(dir.getPath + "_2.12+")
            case _                       => new File(dir.getPath + "_2.12-")
          },

          scalaPartV.value match {
            case Some((2, n)) if n >= 13 => new File(dir.getPath + "_2.13+")
            case _                       => new File(dir.getPath + "_2.13-")
          },
        )
      }
    }
  }

lazy val requiredMacroDeps = Seq(
  libraryDependencies ++= Seq(
    scalaOrganization.value % "scala-reflect" % scalaVersion.value % Provided,
    scalaOrganization.value % "scala-compiler" % scalaVersion.value % Provided
  ))

lazy val unidocSettings = Seq(
  unidocProjectFilter in (ScalaUnidoc, unidoc) :=
    inProjects(executionJVM, catnapJVM, evalJVM, tailJVM, reactiveJVM),

  // Exclude monix.*.internal from ScalaDoc
  sources in (ScalaUnidoc, unidoc) ~= (_ filterNot { file =>
    // Exclude all internal Java files from documentation
    file.getCanonicalPath matches "^.*monix.+?internal.*?\\.java$"
  }),

  scalacOptions in (ScalaUnidoc, unidoc) +=
    "-Xfatal-warnings",
  scalacOptions in (ScalaUnidoc, unidoc) --=
    Seq("-Ywarn-unused-import", "-Ywarn-unused:imports"),
  scalacOptions in (ScalaUnidoc, unidoc) ++=
    Opts.doc.title(s"Monix"),
  scalacOptions in (ScalaUnidoc, unidoc) ++=
    Opts.doc.sourceUrl(s"https://github.com/monix/monix/tree/v${version.value}€{FILE_PATH}.scala"),
  scalacOptions in (ScalaUnidoc, unidoc) ++=
    Seq("-doc-root-content", file("rootdoc.txt").getAbsolutePath),
  scalacOptions in (ScalaUnidoc, unidoc) ++=
    Opts.doc.version(s"${version.value}")
)

lazy val testSettings = Seq(
  testFrameworks := Seq(new TestFramework("minitest.runner.Framework")),
  libraryDependencies ++= Seq(
    "io.monix" %%% "minitest-laws" % minitestVersion % Test,
    "org.typelevel" %%% "cats-laws" % catsVersion.value % Test,
    "org.typelevel" %%% "cats-effect-laws" % catsEffectVersion.value % Test
  )
)

lazy val javaExtensionsSettings = sharedSettings ++ testSettings ++ Seq(
  name := "monix-java"
)

lazy val scalaJSSettings = Seq(
  coverageExcludedFiles := ".*",
  // Use globally accessible (rather than local) source paths in JS source maps
  scalacOptions += {
    val tagOrHash =
      if (isSnapshot.value) git.gitHeadCommit.value.get
      else s"v${git.baseVersion.value}"
    val l = (baseDirectory in LocalRootProject).value.toURI.toString
    val g = s"https://raw.githubusercontent.com/monix/monix/$tagOrHash/"
    s"-P:scalajs:mapSourceURI:$l->$g"
  }
)

lazy val cmdlineProfile =
  sys.env.getOrElse("SBT_PROFILE", "")

def mimaSettings(projectName: String) = Seq(
  mimaPreviousArtifacts := Set("io.monix" %% projectName % monixSeries),
  mimaBinaryIssueFilters ++= MimaFilters.changesFor_3_0_1,
  mimaBinaryIssueFilters ++= MimaFilters.changesFor_3_2_0,
  mimaBinaryIssueFilters ++= MimaFilters.changesFor_3_2_3,
)

def profile: Project ⇒ Project = pr => {
  val withCoverage = cmdlineProfile match {
    case "coverage" => pr
    case _ => pr.disablePlugins(scoverage.ScoverageSbtPlugin)
  }
  withCoverage
    .enablePlugins(AutomateHeaderPlugin)
    .enablePlugins(ReproducibleBuildsPlugin)
}

lazy val doctestTestSettings = Seq(
  doctestTestFramework := DoctestTestFramework.Minitest,
  doctestIgnoreRegex := Some(s".*TaskApp.scala|.*reactive.internal.(builders|operators|rstreams).*"),
  doctestOnlyCodeBlocksMode := true
)

lazy val assemblyShadeSettings = Seq(
  assemblyOption in assembly :=  (assemblyOption in assembly).value.copy(
    includeScala = false,
    includeBin = false,
  ),
  // for some weird reason the "assembly" task runs tests by default
  test in assembly := {},
  // prevent cyclic task dependencies, see https://github.com/sbt/sbt-assembly/issues/365
  // otherwise, there's a cyclic dependency between packageBin and assembly
  fullClasspath in assembly := (managedClasspath in Runtime).value,
  // in dependent projects, use assembled and shaded jar
  exportJars := true,
  // do not include scala dependency in pom
  autoScalaLibrary := false,
  // prevent original dependency to be added to pom as runtime dep
  makePomConfiguration := makePomConfiguration.value.withConfigurations(Vector.empty),
  // package by running assembly
  packageBin in Compile := ReproducibleBuildsPlugin
    .postProcessJar((assembly in Compile).value),
  // disable publishing the main API jar
  Compile / packageDoc / publishArtifact := false,
  // disable publishing the main sources jar
  Compile / packageSrc / publishArtifact := false,
)

lazy val monix = project.in(file("."))
  .enablePlugins(ScalaUnidocPlugin)
  .configure(profile)
  .aggregate(coreJVM, coreJS)
  .settings(sharedSettings)
  .settings(doNotPublishArtifact)
  .settings(unidocSettings)
  .settings(
    Global / onChangedBuildSource := ReloadOnSourceChanges,
    // https://github.com/lightbend/mima/pull/289
    mimaFailOnNoPrevious in ThisBuild := false
  )

lazy val coreJVM = project.in(file("monix/jvm"))
  .configure(profile)
  .dependsOn(executionJVM, catnapJVM, evalJVM, tailJVM, reactiveJVM, javaJVM)
  .aggregate(executionJVM, catnapJVM, evalJVM, tailJVM, reactiveJVM, javaJVM)
  .settings(crossSettings)
  .settings(name := "monix")
  .settings(
    // do not publish second time during +publish run with ScalaJS 1.0
    skip.in(publish) := customScalaJSVersion.forall(_.startsWith("1.0"))
  )

lazy val coreJS = project.in(file("monix/js"))
  .configure(profile)
  .enablePlugins(ScalaJSPlugin)
  .dependsOn(executionJS, catnapJS, evalJS, tailJS, reactiveJS)
  .aggregate(executionJS, catnapJS, evalJS, tailJS, reactiveJS)
  .settings(crossSettings)
  .settings(scalaJSSettings)
  .settings(name := "monix")

lazy val executionCommon = crossVersionSharedSources ++ Seq(
  name := "monix-execution",
  libraryDependencies += "io.monix" %%% "implicitbox" % implicitBoxVersion
)

lazy val executionShadedJCTools = project.in(file("monix-execution/shaded/jctools"))
  .configure(profile)
  .settings(crossSettings)
  .settings(assemblyShadeSettings)
  .settings(
    name := "monix-internal-jctools",
    description := "Monix Execution Shaded JCTools is a shaded version of JCTools library. See: https://github.com/JCTools/JCTools",
    libraryDependencies += "org.jctools" % "jctools-core" % jcToolsVersion % "optional;provided",
    // https://github.com/sbt/sbt-assembly#shading
    assemblyShadeRules in assembly := Seq(
      ShadeRule.rename("org.jctools.**" -> "monix.execution.internal.jctools.@1")
        .inLibrary("org.jctools" % "jctools-core" % jcToolsVersion % "optional;provided")
        .inAll
    )
  )

lazy val executionJVM = project.in(file("monix-execution/jvm"))
  .configure(profile)
  .settings(crossSettings)
  .settings(testSettings)
  .settings(requiredMacroDeps)
  .settings(executionCommon)
  .dependsOn(executionShadedJCTools)
  .settings(libraryDependencies += "org.reactivestreams" % "reactive-streams" % reactiveStreamsVersion)
  .settings(mimaSettings("monix-execution"))

lazy val executionJS = project.in(file("monix-execution/js"))
  .enablePlugins(ScalaJSPlugin)
  .configure(profile)
  .settings(crossSettings)
  .settings(scalaJSSettings)
  .settings(testSettings)
  .settings(requiredMacroDeps)
  .settings(executionCommon)

lazy val catnapCommon =
  crossSettings ++ crossVersionSharedSources ++ testSettings ++ Seq(
    name := "monix-catnap",
    libraryDependencies += "org.typelevel" %%% "cats-effect" % catsEffectVersion.value
)

lazy val catnapJVM = project.in(file("monix-catnap/jvm"))
  .configure(profile)
  .dependsOn(executionJVM % "compile->compile; test->test")
  .settings(catnapCommon)
  .settings(mimaSettings("monix-catnap"))
  .settings(doctestTestSettings)

lazy val catnapJS = project.in(file("monix-catnap/js"))
  .enablePlugins(ScalaJSPlugin)
  .configure(profile)
  .dependsOn(executionJS % "compile->compile; test->test")
  .settings(scalaJSSettings)
  .settings(catnapCommon)

lazy val evalCommon =
  crossSettings ++ crossVersionSharedSources ++ testSettings ++
    Seq(
      name := "monix-eval"
    )

lazy val evalJVM = project.in(file("monix-eval/jvm"))
  .configure(profile)
  .dependsOn(executionJVM % "compile->compile; test->test")
  .dependsOn(catnapJVM)
  .settings(evalCommon)
  .settings(mimaSettings("monix-eval"))
  .settings(doctestTestSettings)

lazy val evalJS = project.in(file("monix-eval/js"))
  .enablePlugins(ScalaJSPlugin)
  .configure(profile)
  .dependsOn(executionJS % "compile->compile; test->test")
  .dependsOn(catnapJS)
  .settings(scalaJSSettings)
  .settings(evalCommon)

lazy val tailCommon =
  crossSettings ++ testSettings ++ Seq(
    name := "monix-tail"
  )

lazy val tailJVM = project.in(file("monix-tail/jvm"))
  .configure(profile)
  .dependsOn(evalJVM % "test->test")
  .dependsOn(catnapJVM)
  .settings(tailCommon)
  .settings(doctestTestSettings)
  .settings(mimaSettings("monix-tail"))

lazy val tailJS = project.in(file("monix-tail/js"))
  .enablePlugins(ScalaJSPlugin)
  .configure(profile)
  .dependsOn(evalJS % "test->test")
  .dependsOn(catnapJS)
  .settings(scalaJSSettings)
  .settings(tailCommon)

lazy val reactiveCommon =
  crossSettings ++ testSettings ++ Seq(
    name := "monix-reactive"
  )

lazy val reactiveJVM = project.in(file("monix-reactive/jvm"))
  .configure(profile)
  .dependsOn(executionJVM, evalJVM % "compile->compile; test->test")
  .settings(reactiveCommon)
  .settings(mimaSettings("monix-reactive"))
  .settings(doctestTestSettings)

lazy val reactiveJS = project.in(file("monix-reactive/js"))
  .enablePlugins(ScalaJSPlugin)
  .configure(profile)
  .dependsOn(executionJS, evalJS % "compile->compile; test->test")
  .settings(reactiveCommon)
  .settings(scalaJSSettings)

lazy val javaJVM = project.in(file("monix-java"))
  .configure(profile)
  .dependsOn(executionJVM % "provided->compile; test->test")
  .dependsOn(evalJVM % "provided->compile; test->test")
  .settings(javaExtensionsSettings)

lazy val reactiveTests = project.in(file("reactiveTests"))
  .configure(profile)
  .dependsOn(coreJVM)
  .settings(sharedSettings)
  .settings(doNotPublishArtifact)
  .settings(
    libraryDependencies ++= Seq(
      "org.reactivestreams" % "reactive-streams-tck" % reactiveStreamsVersion % Test,
      "org.scalatest" %% "scalatest" % scalaTestVersion % Test
    ))

lazy val benchmarksPrev = project.in(file("benchmarks/vprev"))
  .configure(profile)
  .enablePlugins(JmhPlugin)
  .settings(crossSettings)
  .settings(sharedSettings)
  .settings(doNotPublishArtifact)
  .settings(
    libraryDependencies ++= Seq(
      "io.monix" %% "monix" % "3.2.2",
      "dev.zio" %% "zio-streams" % "1.0.0-RC21-2",
      "co.fs2" %% "fs2-core" % fs2Version.value
  ))

lazy val benchmarksNext = project.in(file("benchmarks/vnext"))
  .configure(profile)
  .dependsOn(coreJVM)
  .enablePlugins(JmhPlugin)
  .settings(crossSettings)
  .settings(sharedSettings)
  .settings(doNotPublishArtifact)
  .settings(
    libraryDependencies ++= Seq(
      "dev.zio" %% "zio-streams" % "1.0.0-RC21-2",
      "co.fs2" %% "fs2-core" % fs2Version.value
    ))

//------------- For Release

enablePlugins(GitVersioning)

/* The BaseVersion setting represents the in-development (upcoming) version,
 * as an alternative to SNAPSHOTS.
 */
git.baseVersion := "3.3.0"

val ReleaseTag = """^v(\d+\.\d+(?:\.\d+(?:[-.]\w+)?)?)$""".r
git.gitTagToVersionNumber := {
  case ReleaseTag(v) => Some(v)
  case _ => None
}

git.formattedShaVersion := {
  val suffix = git.makeUncommittedSignifierSuffix(git.gitUncommittedChanges.value, git.uncommittedSignifier.value)

  git.gitHeadCommit.value map { _.substring(0, 7) } map { sha =>
    git.baseVersion.value + "-" + sha + suffix
  }
}

