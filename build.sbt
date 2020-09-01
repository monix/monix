import sbt.Keys.version
import sbt.Def
import scala.collection.immutable.SortedSet
import MonixBuildUtils._


val benchmarkProjects = List(
  "benchmarksPrev",
  "benchmarksNext"
).map(_ + "/compile")

addCommandAlias("ci",          ";ci-jvm ;ci-js")
addCommandAlias("ci-all",      ";ci-jvm ;ci-js ;ci-meta")
addCommandAlias("ci-js",       ";clean ;coreJS/test:compile ;coreJS/test ;coreJS/package")
addCommandAlias("ci-jvm",      ";clean ;coreJVM/test:compile ;coreJVM/test ;coreJVM/package")
addCommandAlias("ci-meta",     ";mimaReportBinaryIssues ;unidoc")
addCommandAlias("ci-release",  ";+publishSigned ;sonatypeBundleRelease")

// ------------------------------------------------------------------------------------------------
// Dependencies - Versions

val cats_Version = "2.1.1"
val catsEffect_Version = "2.1.4"
val fs2_Version = "2.4.0"
val jcTools_Version = "3.0.1"
val reactiveStreams_Version = "1.0.3"
val minitest_Version = "2.8.2"
val scalaTest_Version = "3.0.8"
val implicitBox_Version = "0.2.0"
val kindProjector_Version = "0.11.0"
val betterMonadicFor_Version = "0.3.1"
val silencer_Version = "1.7.1"
val scalaCompat_Version = "2.1.6"
val customScalaJS_Version =
  Option(sys.env.getOrElse("SCALAJS_VERSION", null)).filter(_.nonEmpty)

// Scala 2.11 specific versions
val cats_ForScala211Version = "2.0.0"
val catsEffect_ForScala211Version = "2.0.0"
val fs2_ForScala211Version = "2.1.0"

// The Monix version with which we must keep binary compatibility.
// https://github.com/typesafehub/migration-manager/wiki/Sbt-plugin
val monixSeries = "3.2.2"

lazy val cats_CrossVersion = Def.setting {
  CrossVersion.partialVersion(scalaVersion.value) match {
    case Some((2, 11)) => cats_ForScala211Version
    case _ => cats_Version
  }
}

lazy val catsEffect_CrossVersion = Def.setting {
  CrossVersion.partialVersion(scalaVersion.value) match {
    case Some((2, 11)) => catsEffect_ForScala211Version
    case _ => catsEffect_Version
  }
}

lazy val fs2_CrossVersion = Def.setting {
  CrossVersion.partialVersion(scalaVersion.value) match {
    case Some((2, 11)) => fs2_ForScala211Version
    case _ => fs2_Version
  }
}

// ------------------------------------------------------------------------------------------------
// Dependencies - Libraries

lazy val scalaReflectLib = Def.setting {
  scalaOrganization.value % "scala-reflect" % scalaVersion.value
}

lazy val scalaCompilerLib = Def.setting {
  scalaOrganization.value % "scala-compiler" % scalaVersion.value
}

/** [[https://typelevel.org/cats/typeclasses/lawtesting.html]] */
lazy val catsLawsLib =
  Def.setting { "org.typelevel" %%% "cats-laws" % cats_CrossVersion.value }

/** [[https://typelevel.org/cats-effect/]] */
lazy val catsEffectLib =
  Def.setting { "org.typelevel" %%% "cats-effect" % catsEffect_CrossVersion.value }

/** [[https://typelevel.org/cats-effect/]] */
lazy val catsEffectLawsLib =
  Def.setting { "org.typelevel" %%% "cats-effect-laws" % catsEffect_CrossVersion.value }

/** [[https://github.com/monix/implicitbox]] */
lazy val implicitBoxLib =
  Def.setting { "io.monix" %%% "implicitbox" % implicitBox_Version }

/** [[https://github.com/JCTools/JCTools]] */
lazy val jcToolsLib =
  "org.jctools" % "jctools-core" % jcTools_Version

/** [[https://github.com/reactive-streams/reactive-streams-jvm]] */
lazy val reactiveStreamsLib =
  "org.reactivestreams" % "reactive-streams" % reactiveStreams_Version
lazy val reactiveStreamsTCKLib =
  "org.reactivestreams" % "reactive-streams-tck" % reactiveStreams_Version

/** [[https://github.com/typelevel/kind-projector]]  */
lazy val kindProjectorCompilerPlugin =
  "org.typelevel" % "kind-projector" % kindProjector_Version cross CrossVersion.full

/** [[https://github.com/monix/minitest/]] */
lazy val minitestLib =
  Def.setting { "io.monix" %%% "minitest-laws" % minitest_Version }

/** [[https://github.com/scalatest/scalatest]] */
lazy val scalaTestLib =
  Def.setting { "org.scalatest" %%% "scalatest" % scalaTest_Version }

/** [[https://github.com/scala/scala-collection-compat]] */
lazy val scalaCollectionCompatLib =
  Def.setting { "org.scala-lang.modules" %%% "scala-collection-compat" % scalaCompat_Version }

/** [[https://github.com/oleg-py/better-monadic-for]] */
lazy val betterMonadicForCompilerPlugin =
  "com.olegpy" %% "better-monadic-for" % betterMonadicFor_Version

/** [[https://github.com/ghik/silencer]] */
lazy val silencerCompilerPlugin =
  "com.github.ghik" % "silencer-plugin" % silencer_Version cross CrossVersion.full

lazy val macroDependencies = Seq(
  libraryDependencies ++= Seq(
    scalaReflectLib.value % Provided,
    scalaCompilerLib.value % Provided
  )
)

lazy val testDependencies = Seq(
  testFrameworks := Seq(new TestFramework("minitest.runner.Framework")),
  libraryDependencies ++= Seq(
    minitestLib.value % Test,
    catsLawsLib.value % Test,
    catsEffectLawsLib.value % Test,
  )
)

// ------------------------------------------------------------------------------------------------
// Shared settings

/** For building correct links to source in documentation. */
lazy val gitHubTreeTagOrHash =
  settingKey[String]("Identifies GitHub's version tag or commit sha")

val crossScalaVersionsFromBuildYaml =
  settingKey[SortedSet[MonixScalaVersion]](
    "Scala versions set in .github/workflows/build.yml as scala_version_XXX"
  )

crossScalaVersionsFromBuildYaml in Global := {
  val manifest = (baseDirectory in ThisBuild).value / ".github" / "workflows" / "build.yml"
  scalaVersionsFromBuildYaml(manifest, customScalaJS_Version)
}

lazy val publishStableMonixVersion =
  settingKey[Boolean]("If it should publish stable versions to Sonatype staging repository, instead of a snapshot")

publishStableMonixVersion in Global := {
  sys.env.get("PUBLISH_STABLE_VERSION")
    .exists(v => v == "true" || v == "1" || v == "yes")
}

lazy val pgpSettings = {
  val withHex = sys.env.get("PGP_KEY_HEX").filter(_.nonEmpty) match {
    case None => Seq.empty
    case Some(v) => Seq(usePgpKeyHex(v))
  }
  withHex ++ Seq(
    pgpPassphrase := sys.env.get("PGP_PASSPHRASE").filter(_.nonEmpty).map(_.toArray)
  )
}

lazy val sharedSettings = pgpSettings ++ Seq(
  organization := "io.monix",
  // Value extracted from .github/workflows/build.yml
  scalaVersion := crossScalaVersionsFromBuildYaml.value.head.value,
  // Value extracted from .github/workflows/build.yml
  crossScalaVersions := crossScalaVersionsFromBuildYaml.value.toIndexedSeq.map(_.value),

  gitHubTreeTagOrHash := {
    val ver = s"v${version.value}"
    if (isSnapshot.value)
      git.gitHeadCommit.value.getOrElse(ver)
    else
      ver
  },

  /*
  // Enable this to debug warnings...
  scalacOptions in Compile ++= {
    CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((2, 13)) => Seq("-Wconf:any:warning-verbose")
      case _ => Seq.empty
    }
  },
  */

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
  // Silence everything in auto-generated files
  scalacOptions += "-P:silencer:pathFilters=.*[/]src_managed[/].*",
  // Syntax improvements, linting, etc.
  addCompilerPlugin(kindProjectorCompilerPlugin),
  addCompilerPlugin(betterMonadicForCompilerPlugin),
  addCompilerPlugin(silencerCompilerPlugin),

  libraryDependencies ++= Seq(
    scalaCollectionCompatLib.value % "provided;optional",
  ),
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

  // https://github.com/sbt/sbt/issues/2654
  incOptions := incOptions.value.withLogRecompileOnMacro(false),

  // -- Settings meant for deployment on oss.sonatype.org
  publishTo in ThisBuild := sonatypePublishToBundle.value,
  isSnapshot in ThisBuild := {
    !isVersionStable.value || !publishStableMonixVersion.value  
  },
  dynverSonatypeSnapshots in ThisBuild := !(isVersionStable.value && publishStableMonixVersion.value),
  sonatypeProfileName in ThisBuild := organization.value,
  sonatypeSessionName := s"[sbt-sonatype] ${name.value}${customScalaJS_Version.fold("-nojs")(v => s"-sjs$v")}-${version.value}",

  publishMavenStyle := true,
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

lazy val sharedSourcesSettings = Seq(
  unmanagedSourceDirectories in Compile += {
    baseDirectory.value.getParentFile / "shared" / "src" / "main" / "scala"
  },
  unmanagedSourceDirectories in Test += {
    baseDirectory.value.getParentFile / "shared" / "src" / "test" / "scala"
  }
)

def scalaPartV = Def setting (CrossVersion partialVersion scalaVersion.value)
lazy val crossVersionSourcesSettings: Seq[Setting[_]] =
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
          }
        )
      }
    }
  }

lazy val doNotPublishArtifactSettings = Seq(
  publishArtifact := false,
  publishArtifact in (Compile, packageDoc) := false,
  publishArtifact in (Compile, packageSrc) := false,
  publishArtifact in (Compile, packageBin) := false
)

lazy val assemblyShadeSettings = Seq(
  assemblyOption in assembly :=  (assemblyOption in assembly).value.copy(
    includeScala = false,
    includeBin = false
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
  packageBin in Compile := ReproducibleBuildsPlugin.postProcessJar((assembly in Compile).value),
)

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
    Opts.doc.sourceUrl(s"https://github.com/monix/monix/tree/${gitHubTreeTagOrHash.value}€{FILE_PATH}.scala"),
  scalacOptions in (ScalaUnidoc, unidoc) ++=
    Seq("-doc-root-content", file("rootdoc.txt").getAbsolutePath),
  scalacOptions in (ScalaUnidoc, unidoc) ++=
    Opts.doc.version(s"${version.value}")
)

lazy val sharedJSSettings = Seq(
  coverageExcludedFiles := ".*",
  // Use globally accessible (rather than local) source paths in JS source maps
  scalacOptions += {
    val l = (baseDirectory in LocalRootProject).value.toURI.toString
    val g = s"https://raw.githubusercontent.com/monix/monix/${gitHubTreeTagOrHash.value}/"
    s"-P:scalajs:mapSourceURI:$l->$g"
  },
  // Needed in order to publish for multiple Scala.js versions:
  // https://github.com/olafurpg/sbt-ci-release#how-do-i-publish-cross-built-scalajs-projects
  skip.in(publish) := customScalaJS_Version.isEmpty,
)

lazy val sharedJVMSettings = Seq(
  skip.in(publish) := customScalaJS_Version.isDefined
)

def mimaSettings(projectName: String) = Seq(
  mimaPreviousArtifacts := Set("io.monix" %% projectName % monixSeries),
  mimaBinaryIssueFilters ++= MimaFilters.changesFor_3_0_1,
  mimaBinaryIssueFilters ++= MimaFilters.changesFor_3_2_0,
  mimaBinaryIssueFilters ++= MimaFilters.changesFor_3_2_3,
  mimaBinaryIssueFilters ++= MimaFilters.changesTO_REMOVE
)

lazy val doctestTestSettings = Seq(
  doctestTestFramework := DoctestTestFramework.Minitest,
  doctestIgnoreRegex := Some(s".*TaskApp.scala|.*reactive.internal.(builders|operators|rstreams).*"),
  doctestOnlyCodeBlocksMode := true
)

// ------------------------------------------------------------------------------------------------
// Configuration profiles

def baseSettingsAndPlugins(publishArtifacts: Boolean): Project ⇒ Project =
  pr => {
    val withCoverage = sys.env.getOrElse("SBT_PROFILE", "") match {
      case "coverage" => pr
      case _ => pr.disablePlugins(scoverage.ScoverageSbtPlugin)
    }
    withCoverage
      .enablePlugins(AutomateHeaderPlugin)
      .settings(sharedSettings)
      .settings(if (publishArtifacts) Seq.empty else doNotPublishArtifactSettings)
      .settings(filterOutMultipleDependenciesFromGeneratedPomXml(
        "groupId" -> "org.scoverage".r :: Nil,
        "groupId" -> "org.typelevel".r :: "artifactId" -> "simulacrum".r :: Nil,
      ))
  }

def monixSubModule(
  projectName: String,
  publishArtifacts: Boolean,
): Project => Project = pr => {
  pr.configure(baseSettingsAndPlugins(publishArtifacts = publishArtifacts))
    .enablePlugins(ReproducibleBuildsPlugin)
    .settings(sharedSourcesSettings)
    .settings(crossVersionSourcesSettings)
    .settings(name := projectName)
}

def jvmModule(
  projectName: String,
  withMimaChecks: Boolean,
  withDocTests: Boolean,
  publishArtifacts: Boolean,
): Project => Project =
  pr => {
    pr.configure(monixSubModule(projectName, publishArtifacts = publishArtifacts))
      .settings(sharedJVMSettings)
      .settings(testDependencies)
      .settings(if (withDocTests) doctestTestSettings else Seq.empty)
      .settings(if (withMimaChecks) mimaSettings(projectName) else Seq.empty)
  }

def jsProfile(projectName: String, publishArtifacts: Boolean): Project => Project =
  pr => {
    pr.configure(monixSubModule(projectName, publishArtifacts = publishArtifacts))
      .enablePlugins(ScalaJSPlugin)
      .settings(testDependencies)
      .settings(sharedJSSettings)
  }

def crossModule(
  projectName: String,
  withMimaChecks: Boolean = true,
  withDocTests: Boolean = true,
  publishArtifacts: Boolean = true,
  crossSettings: Seq[sbt.Def.SettingsDefinition] = Nil): MonixCrossModule = {

  MonixCrossModule(
    jvm = jvmModule(
      projectName = projectName,
      withMimaChecks = withMimaChecks,
      withDocTests = withDocTests,
      publishArtifacts = publishArtifacts
    ).andThen(_.settings(crossSettings:_*)),
    js = jsProfile(
      projectName = projectName,
      publishArtifacts = publishArtifacts
    ).andThen(_.settings(crossSettings:_*))
  )
}

// ------------------------------------------------------------------------------------------------
// Projects

lazy val monix = project.in(file("."))
  .configure(baseSettingsAndPlugins(publishArtifacts = false))
  .enablePlugins(ScalaUnidocPlugin)
  .aggregate(coreJVM, coreJS)
  .settings(unidocSettings)
  .settings(
    Global / onChangedBuildSource := ReloadOnSourceChanges,
    // https://github.com/lightbend/mima/pull/289
    mimaFailOnNoPrevious in ThisBuild := false
  )

// --------------------------------------------
// monix (root)

lazy val coreProfile =
  crossModule(
    projectName = "monix",
    withMimaChecks = false,
    withDocTests = false,
    crossSettings = Seq(
      description := "Root project for Monix, a library for asynchronous programming in Scala. See: https://monix.io"
    ))

lazy val coreJVM = project.in(file("monix/jvm"))
  .configure(coreProfile.jvm)
  .dependsOn(executionJVM, catnapJVM, evalJVM, tailJVM, reactiveJVM, javaJVM)
  .aggregate(executionShadedJCTools, executionJVM, catnapJVM, evalJVM, tailJVM, reactiveJVM, javaJVM)

lazy val coreJS = project.in(file("monix/js"))
  .configure(coreProfile.js)
  .dependsOn(executionJS, catnapJS, evalJS, tailJS, reactiveJS)
  .aggregate(executionJS, catnapJS, evalJS, tailJS, reactiveJS)

// --------------------------------------------
// monix-internal-jctools (shaded lib)

lazy val executionShadedJCTools = project.in(file("monix-execution/shaded/jctools"))
  .configure(jvmModule(
    projectName = "monix-internal-jctools",
    withMimaChecks = false,
    withDocTests = false,
    publishArtifacts = true
  ))
  .settings(assemblyShadeSettings)
  .settings(
    description := "Monix Execution Shaded JCTools is a shaded version of JCTools library. See: https://github.com/JCTools/JCTools",
    libraryDependencies += jcToolsLib % "optional;provided",
    // https://github.com/sbt/sbt-assembly#shading
    assemblyShadeRules in assembly := Seq(
      ShadeRule.rename("org.jctools.**" -> "monix.execution.internal.jctools.@1")
        .inLibrary("org.jctools" % "jctools-core" % jcTools_Version % "optional;provided")
        .inAll
    )
  )

// --------------------------------------------
// monix-execution

lazy val executionProfile =
  crossModule(
    projectName = "monix-execution",
    withDocTests = false,
    crossSettings = Seq(
      description := "Sub-module of Monix, exposing low-level primitives for dealing with async execution. See: https://monix.io",
      libraryDependencies += implicitBoxLib.value
    ))

lazy val executionJVM = project.in(file("monix-execution/jvm"))
  .configure(executionProfile.jvm)
  .settings(macroDependencies)
  .dependsOn(executionShadedJCTools)
  .settings(libraryDependencies += reactiveStreamsLib)

lazy val executionJS = project.in(file("monix-execution/js"))
  .configure(executionProfile.js)
  .settings(macroDependencies)

// --------------------------------------------
// monix-catnap

lazy val catnapProfile =
  crossModule(
    projectName = "monix-catnap",
    crossSettings = Seq(
      description := "Sub-module of Monix, exposing pure abstractions built on top of the Cats-Effect type classes. See: https://monix.io",
      libraryDependencies += catsEffectLib.value
    ))

lazy val catnapJVM = project.in(file("monix-catnap/jvm"))
  .configure(catnapProfile.jvm)
  .dependsOn(executionJVM % "compile->compile; test->test")

lazy val catnapJS = project.in(file("monix-catnap/js"))
  .configure(catnapProfile.js)
  .dependsOn(executionJS % "compile->compile; test->test")

// --------------------------------------------
// monix-catnap

lazy val evalProfile =
  crossModule(
    projectName = "monix-eval",
    crossSettings = Seq(
      description := "Sub-module of Monix, exposing Task and Coeval, for suspending side-effects. See: https://monix.io"
    ))

lazy val evalJVM = project.in(file("monix-eval/jvm"))
  .configure(evalProfile.jvm)
  .dependsOn(executionJVM % "compile->compile; test->test")
  .dependsOn(catnapJVM)

lazy val evalJS = project.in(file("monix-eval/js"))
  .configure(evalProfile.js)
  .dependsOn(executionJS % "compile->compile; test->test")
  .dependsOn(catnapJS)

// --------------------------------------------
// monix-tail

lazy val tailProfile =
  crossModule(
    projectName = "monix-tail",
    crossSettings = Seq(
      description := "Sub-module of Monix, exposing Iterant for purely functional pull based streaming. See: https://monix.io"
    ))

lazy val tailJVM = project.in(file("monix-tail/jvm"))
  .configure(tailProfile.jvm)
  .dependsOn(evalJVM % "test->test")
  .dependsOn(catnapJVM)

lazy val tailJS = project.in(file("monix-tail/js"))
  .configure(tailProfile.js)
  .dependsOn(evalJS % "test->test")
  .dependsOn(catnapJS)

// --------------------------------------------
// monix-reactive

lazy val reactiveProfile =
  crossModule(
    projectName = "monix-reactive",
    crossSettings = Seq(
      description := "Sub-module of Monix, exposing the Observable pattern for modeling of reactive streams. See: https://monix.io"
    ))

lazy val reactiveJVM = project.in(file("monix-reactive/jvm"))
  .configure(reactiveProfile.jvm)
  .dependsOn(executionJVM, evalJVM % "compile->compile; test->test")

lazy val reactiveJS = project.in(file("monix-reactive/js"))
  .configure(reactiveProfile.js)
  .dependsOn(executionJS, evalJS % "compile->compile; test->test")

// --------------------------------------------
// monix-java

lazy val javaJVM = project.in(file("monix-java"))
  .configure(jvmModule(
    projectName = "monix-java",
    withMimaChecks = true,
    withDocTests = true,
    publishArtifacts = true
  ))
  .dependsOn(executionJVM % "provided->compile; test->test")
  .dependsOn(evalJVM % "provided->compile; test->test")

// --------------------------------------------
// monix-reactive-tests (not published)

lazy val reactiveTests = project.in(file("reactiveTests"))
  .configure(monixSubModule(
    "monix-reactive-tests",
    publishArtifacts = false
  ))
  .dependsOn(reactiveJVM, tailJVM)
  .settings(
    libraryDependencies ++= Seq(
      reactiveStreamsTCKLib % Test,
      scalaTestLib.value % Test,
    ))

// --------------------------------------------
// monix-benchmarks-{prev,next} (not published)

lazy val benchmarksPrev = project.in(file("benchmarks/vprev"))
  .enablePlugins(JmhPlugin)
  .configure(monixSubModule(
    "monix-benchmarks-prev",
    publishArtifacts = false
  ))
  .settings(
    libraryDependencies ++= Seq(
      "io.monix" %% "monix" % "3.2.2",
      "dev.zio" %% "zio-streams" % "1.0.0-RC21-2",
      "co.fs2" %% "fs2-core" % fs2_CrossVersion.value
  ))

lazy val benchmarksNext = project.in(file("benchmarks/vnext"))
  .enablePlugins(JmhPlugin)
  .configure(monixSubModule(
    projectName = "monix-benchmarks-next",
    publishArtifacts = false
  ))
  .dependsOn(reactiveJVM, tailJVM)
  .settings(
    libraryDependencies ++= Seq(
      "dev.zio" %% "zio-streams" % "1.0.0-RC21-2",
      "co.fs2" %% "fs2-core" % fs2_CrossVersion.value
    ))
