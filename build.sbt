import sbt.Keys.version
import sbt.{ Def, Global, Tags }
import com.github.sbt.git.SbtGit.GitKeys.useConsoleForROGit
import com.typesafe.tools.mima.core.ProblemFilter
import org.typelevel.scalacoptions.ScalacOptions
import xerial.sbt.Sonatype.sonatypeCentralHost

import scala.collection.immutable.SortedSet
import MonixBuildUtils._

ThisBuild / useConsoleForROGit := true

val benchmarkProjects = List(
  "benchmarksPrev",
  "benchmarksNext"
).map(_ + "/compile").mkString(" ;")

val jvmTests = List(
  "reactiveTests",
  "tracingTests"
).map(_ + "/test").mkString(" ;")

addCommandAlias(
  "ci-all",
  ";ci-jvm ;ci-js ;ci-meta"
)
addCommandAlias(
  "ci-js",
  ";clean ;coreJS/Test/compile ;coreJS/test ;coreJS/package"
)
addCommandAlias(
  "ci-jvm",
  ";clean ;coreJVM/Test/compile ;coreJVM/test ;coreJVM/package ;tracingTests/test"
)
addCommandAlias(
  "ci-meta",
  ";mimaReportBinaryIssues ;unidoc"
)
addCommandAlias(
  "ci-release",
  ";+publishSigned ;sonatypeBundleRelease"
)

// ------------------------------------------------------------------------------------------------
// Dependencies - Versions

val cats_Version              = "2.13.0"
val catsEffect_Version        = "2.5.5"
val fs2_Version               = "2.5.11"
val jcTools_Version           = "4.0.6"
val reactiveStreams_Version   = "1.0.4"
val macrotaskExecutor_Version = "1.1.1"
val minitest_Version          = "2.9.6"
val implicitBox_Version       = "0.3.4"
val kindProjector_Version     = "0.13.4"
val betterMonadicFor_Version  = "0.3.1"
val scalaCompat_Version       = "2.14.0"

// The Monix version with which we must keep binary compatibility.
// https://github.com/lightbend/mima#sbt
val monixSeries = "3.4.0"

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
  Def.setting { "org.typelevel" %%% "cats-laws" % cats_Version }

/** [[https://typelevel.org/cats-effect/]] */
lazy val catsEffectLib =
  Def.setting { "org.typelevel" %%% "cats-effect" % catsEffect_Version }

/** [[https://typelevel.org/cats-effect/]] */
lazy val catsEffectLawsLib =
  Def.setting { "org.typelevel" %%% "cats-effect-laws" % catsEffect_Version }

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

/** [[https://github.com/scala-js/scala-js-macrotask-executor]] */
lazy val macrotaskExecutorLib =
  Def.setting { "org.scala-js" %%% "scala-js-macrotask-executor" % macrotaskExecutor_Version }

/** [[https://github.com/typelevel/kind-projector]] */
lazy val kindProjectorCompilerPlugin =
  "org.typelevel" % "kind-projector" % kindProjector_Version cross CrossVersion.full

/** [[https://github.com/monix/minitest/]] */
lazy val minitestLib =
  Def.setting { "io.monix" %%% "minitest-laws" % minitest_Version }

/** [[https://github.com/scala/scala-collection-compat]] */
lazy val scalaCollectionCompatLib =
  Def.setting { ("org.scala-lang.modules" %%% "scala-collection-compat" % scalaCompat_Version) }

/** [[https://github.com/oleg-py/better-monadic-for]] */
lazy val betterMonadicForCompilerPlugin =
  "com.olegpy" %% "better-monadic-for" % betterMonadicFor_Version

lazy val macroDependencies =
  Seq(
    libraryDependencies ++= (
      if (isDotty.value) Seq()
      else
        Seq(
          scalaReflectLib.value  % Provided,
          scalaCompilerLib.value % Provided
        )
    )
  )

lazy val testDependencies = Seq(
  testFrameworks := Seq(new TestFramework("minitest.runner.Framework")),
  libraryDependencies ++= Seq(
    minitestLib.value       % Test,
    catsLawsLib.value       % Test,
    catsEffectLawsLib.value % Test
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

lazy val publishStableMonixVersion =
  settingKey[Boolean]("If it should publish stable versions to Sonatype staging repository, instead of a snapshot")

lazy val pgpSettings = {
  val withHex = sys.env.get("PGP_KEY_HEX").filter(_.nonEmpty) match {
    case None => Seq.empty
    case Some(v) => Seq(usePgpKeyHex(v))
  }
  withHex ++ Seq(
    pgpPassphrase := sys.env.get("PGP_PASSPHRASE").filter(_.nonEmpty).map(_.toArray)
  )
}

lazy val isDotty =
  Def.setting {
    scalaPartV.value match {
      case Some((3, _)) => true
      case _ => false
    }
  }

lazy val isCI = {
  sys.env.getOrElse("SBT_PROFILE", "").contains("ci") ||
  sys.env.get("CI").exists(v => v == "true" || v == "1" || v == "yes")
}

lazy val sharedSettings = pgpSettings ++ Def.settings(
  organization := "io.monix",
  // Value extracted from .github/workflows/build.yml
  scalaVersion := crossScalaVersionsFromBuildYaml.value.flatMap(_.filterPrefix("2.13.")).head.value,
  // Value extracted from .github/workflows/build.yml
  crossScalaVersions := crossScalaVersionsFromBuildYaml.value.toIndexedSeq.map(_.value),
  gitHubTreeTagOrHash := {
    val ver = s"v${version.value}"
    if (isSnapshot.value)
      git.gitHeadCommit.value.getOrElse(ver)
    else
      ver
  },

  // Enable this to debug warnings...
  Compile / scalacOptions ++= {
    CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((2, 13)) =>
        Seq(
          "-Xfatal-warnings",
          "-Xsource:3-cross",
          // These break binary backwards compatibility, enabled by -Xsource:3, so disabling them
          "-Xsource-features:-case-apply-copy-access,-case-companion-function,-infer-override",
          // Silence various warnings that are false positives or intentional patterns
          // "-Wconf:cat=other-pure-statement:silent,cat=lint-constant:silent,cat=unused-privates:silent,cat=unused-locals:silent,cat=unused-params:silent,cat=unused-imports:silent,cat=w-flag-numeric-widen:silent,any:warning-verbose",
          // @nowarn statements for Scala 3 will generate unused-nowarn for Scala 2.13
          "-Wconf:cat=unused-nowarn:s",
          // Disabling via -Xsource-features will generate these warnings
          "-Wconf:cat=scala3-migration:s",
        )
      case Some((3, _)) =>
        Seq(
          "-Werror",
          "-Wconf:msg=Implicit parameters should be provided with a `using` clause:s"
        )
      case _ =>
        Seq.empty
    }
  },
  Test / scalacOptions ++= {
    CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((2, 13)) => Seq(
          // Silence various warnings in tests
          "-Wconf:cat=other-pure-statement:silent,cat=lint-constant:silent,cat=unused-privates:silent,cat=unused-locals:silent,cat=unused-params:silent,cat=unused-imports:silent,cat=w-flag-numeric-widen:silent"
        )
      case Some((3, _)) =>
        Seq(
          // Scala 3.8.x surfaces a very large warning volume in legacy tests and doctests.
          // Keep -Werror for main sources, but silence test warnings to preserve CI signal.
          "-Wconf:any:silent"
        )
      case _ =>
        Seq.empty
    }
  },

  // Turning off fatal warnings for doc generation
  Compile / doc / tpolecatExcludeOptions ++= ScalacOptions.defaultConsoleExclude,

  // Turn off annoyances in tests
  Test / tpolecatExcludeOptions ++= {
    Set(
      ScalacOptions.lintInferAny,
      ScalacOptions.warnUnusedImplicits,
      ScalacOptions.warnUnusedExplicits,
      ScalacOptions.warnUnusedParams,
      ScalacOptions.warnUnusedNoWarn,
    )
  },

  // Syntax improvements, linting, etc.
  libraryDependencies ++= {
    if (isDotty.value)
      Seq()
    else {
      Seq(
        compilerPlugin(kindProjectorCompilerPlugin),
        compilerPlugin(betterMonadicForCompilerPlugin)
      )
    }
  },
  libraryDependencies ++= Seq(
    scalaCollectionCompatLib.value % "provided;optional"
  ),
  // ScalaDoc settings
  autoAPIMappings := true,
  scalacOptions ++= Seq(
    // Note, this is used by the doc-source-url feature to determine the
    // relative path of a given source file. If it's not a prefix of a the
    // absolute path of the source file, the absolute path of that file
    // will be put into the FILE_SOURCE variable, which is
    // definitely not what we want.
    "-sourcepath",
    file(".").getAbsolutePath.replaceAll("[.]$", "")
  ),

  //
  // Tries disabling parallel execution in tests (in the same project / task)
  Test / logBuffered := isCI,
  Test / parallelExecution := false,
  Test / testForkedParallel := false,

  // https://github.com/sbt/sbt/issues/2654
  incOptions := incOptions.value.withLogRecompileOnMacro(false),

  // -- Settings meant for deployment on central.sonatype.com
  ThisBuild / sonatypeCredentialHost := sonatypeCentralHost,
  ThisBuild / publishTo := sonatypePublishToBundle.value,
  ThisBuild / isSnapshot := {
    !isVersionStable.value || !publishStableMonixVersion.value
  },
  ThisBuild / dynverSonatypeSnapshots := !(isVersionStable.value && publishStableMonixVersion.value),
  ThisBuild / sonatypeProfileName := organization.value,
  sonatypeSessionName := s"[sbt-sonatype] ${name.value}-${version.value}",
  publishMavenStyle := true,
  Test / publishArtifact := false,
  pomIncludeRepository := { _ => false }, // removes optional dependencies

  licenses := Seq("APL2" -> url("http://www.apache.org/licenses/LICENSE-2.0.txt")),
  homepage := Some(url("https://monix.io")),
  headerLicense := Some(HeaderLicense.Custom("""
    |Copyright (c) 2014-2022 Monix Contributors.
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
    |limitations under the License.""".trim.stripMargin)),
  scmInfo := Some(
    ScmInfo(
      url("https://github.com/monix/monix"),
      "scm:git@github.com:monix/monix.git"
    )
  ),
  developers := List(
    Developer(
      id    = "alexelcu",
      name  = "Alexandru Nedelcu",
      email = "noreply@alexn.org",
      url   = url("https://alexn.org")
    )
  )
)

def scalaPartV = Def.setting(CrossVersion.partialVersion(scalaVersion.value))
lazy val extraSourceSettings = {
  val shared = Seq(
    Compile / unmanagedSourceDirectories += {
      baseDirectory.value.getParentFile / "shared" / "src" / "main" / "scala"
    },
    Test / unmanagedSourceDirectories += {
      baseDirectory.value.getParentFile / "shared" / "src" / "test" / "scala"
    }
  )

  val perVersion = Seq(Compile, Test).map { sc =>
    (sc / unmanagedSourceDirectories) ++= {
      (sc / unmanagedSourceDirectories).value.flatMap { dir =>
        if (dir.getPath().endsWith("scala"))
          scalaPartV.value.toList.flatMap {
            case (major, minor) =>
              Seq(
                new File(s"${dir.getPath}-$major"),
                new File(s"${dir.getPath}-$major.$minor"),
              )
          }
        else
          Seq.empty
      }
    }
  }

  shared ++ perVersion
}

lazy val doNotPublishArtifactSettings = Seq(
  publishArtifact := false,
  Compile / packageDoc / publishArtifact := false,
  Compile / packageDoc / publishArtifact := false,
  Compile / packageDoc / publishArtifact := false
)

lazy val assemblyShadeSettings = Seq(
  assembly / assemblyOption := (assembly / assemblyOption).value
    .withIncludeScala(false)
    .withIncludeBin(false),
  // for some weird reason the "assembly" task runs tests by default
  assembly / test := {},
  // prevent cyclic task dependencies, see https://github.com/sbt/sbt-assembly/issues/365
  // otherwise, there's a cyclic dependency between packageBin and assembly
  assembly / fullClasspath := (Runtime / managedClasspath).value,
  // in dependent projects, use assembled and shaded jar
  // Note: exportJars must be false during assembly, but true for dependent projects
  // With sbt-assembly 2.x we need to ensure exportJars is false during the assembly task
  assembly / exportJars := false,
  exportJars := true,
  // do not include scala dependency in pom
  autoScalaLibrary := false,
  // prevent original dependency to be added to pom as runtime dep
  makePomConfiguration := makePomConfiguration.value.withConfigurations(Vector.empty),
  // package by running assembly
  Compile / packageBin := ReproducibleBuildsPlugin.postProcessJar((Compile / assembly).value)
)

lazy val unidocSettings = Seq(
  ScalaUnidoc / unidoc / unidocProjectFilter :=
    inProjects(
      executionAtomicJVM,
      executionJVM,
      catnapJVM,
      evalJVM,
      tailJVM,
      reactiveJVM,
    ),

  // Exclude monix.*.internal from ScalaDoc
  ScalaUnidoc / unidoc / sources ~=
    (_.filterNot { file =>
      // Exclude all internal Java files from documentation
      file.getCanonicalPath.matches("^.*monix.+?internal.*?\\.java$")
    }),
  ScalaUnidoc / unidoc / scalacOptions +=
    "-Xfatal-warnings",
  ScalaUnidoc / unidoc / scalacOptions --=
    Seq("-Ywarn-unused-import", "-Ywarn-unused:imports"),
  ScalaUnidoc / unidoc / scalacOptions ++=
    Opts.doc.title(s"Monix"),
  ScalaUnidoc / unidoc / scalacOptions ++=
    Opts.doc.sourceUrl(s"https://github.com/monix/monix/tree/${gitHubTreeTagOrHash.value}€{FILE_PATH}.scala"),
  ScalaUnidoc / unidoc / scalacOptions ++=
    Seq("-doc-root-content", file("rootdoc.txt").getAbsolutePath),
  ScalaUnidoc / unidoc / scalacOptions ++=
    Opts.doc.version(s"${version.value}")
)

lazy val sharedJSSettings = Seq(
  coverageExcludedFiles := ".*",
  scalacOptions ++= {
    if (isDotty.value)
      Seq()
    else {
      val l = (LocalRootProject / baseDirectory).value.toURI.toString
      val g = s"https://raw.githubusercontent.com/monix/monix/${gitHubTreeTagOrHash.value}/"
      Seq(
        // Use globally accessible (rather than local) source paths in JS source maps
        s"-P:scalajs:mapSourceURI:$l->$g",
        // Silence ExecutionContext.global warning
        "-P:scalajs:nowarnGlobalExecutionContext"
      )
    }
  }
)

def mimaSettings(projectName: String, exclusions: Seq[ProblemFilter]) = Seq(
  ThisBuild / mimaFailOnNoPrevious := false,
  mimaPreviousArtifacts := Set("io.monix" %% projectName % monixSeries),
  mimaBinaryIssueFilters ++= exclusions
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
      .settings(scalafmtOnCompile := !isCI)
      .settings(
        filterOutMultipleDependenciesFromGeneratedPomXml(
          "groupId" -> "org.scoverage".r :: Nil,
          "groupId" -> "org.typelevel".r :: "artifactId" -> "simulacrum".r :: Nil
        )
      )
  }

def monixSubModule(
  projectName: String,
  publishArtifacts: Boolean
): Project => Project = pr => {
  pr.configure(baseSettingsAndPlugins(publishArtifacts = publishArtifacts))
    .enablePlugins(ReproducibleBuildsPlugin)
    .settings(extraSourceSettings)
    .settings(name := projectName)
}

def jvmModule(
  projectName: String,
  publishArtifacts: Boolean,
  withMimaChecks: Option[Seq[ProblemFilter]]
): Project => Project =
  pr => {
    pr.configure(monixSubModule(projectName, publishArtifacts = publishArtifacts))
      .settings(testDependencies)
      .settings(withMimaChecks.toSeq.flatMap(mimaSettings(projectName, _)))
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
  withMimaChecks: Option[Seq[ProblemFilter]],
  publishArtifacts: Boolean,
  crossSettings: Seq[sbt.Def.SettingsDefinition]
): MonixCrossModule = {

  MonixCrossModule(
    jvm = jvmModule(
      projectName      = projectName,
      publishArtifacts = publishArtifacts,
      withMimaChecks   = withMimaChecks
    ).andThen(_.settings(crossSettings: _*)),
    js = jsProfile(
      projectName      = projectName,
      publishArtifacts = publishArtifacts
    ).andThen(_.settings(crossSettings: _*))
  )
}

// ------------------------------------------------------------------------------------------------
// Projects

lazy val monix = project
  .in(file("."))
  .configure(baseSettingsAndPlugins(publishArtifacts = false))
  .enablePlugins(ScalaUnidocPlugin)
  .aggregate(coreJVM, coreJS)
  .settings(unidocSettings)
  .settings(
    //
    // Reads Scala versions from build.yml
    Global / crossScalaVersionsFromBuildYaml := {
      val manifest = (ThisBuild / baseDirectory).value / ".github" / "workflows" / "build.yml"
      scalaVersionsFromBuildYaml(manifest)
    },
    //
    // Tries restricting concurrency when running tests
    // https://www.scala-sbt.org/1.x/docs/Parallel-Execution.html
    Global / concurrentRestrictions += Tags.limit(Tags.Test, 1),
    //
    // Used in CI when publishing artifacts to Sonatype
    Global / publishStableMonixVersion := {
      sys.env
        .get("PUBLISH_STABLE_VERSION")
        .exists(v => v == "true" || v == "1" || v == "yes")
    },
    //
    // Settings for build.sbt management
    Global / onChangedBuildSource := ReloadOnSourceChanges,
    Global / excludeLintKeys ++= Set(
      Compile / gitHubTreeTagOrHash,
      Compile / coverageExcludedFiles
    ),
    // https://github.com/lightbend/mima/pull/289
    ThisBuild / mimaFailOnNoPrevious := false
  )

// --------------------------------------------
// monix (root)

lazy val coreProfile =
  crossModule(
    projectName      = "monix",
    withMimaChecks   = None,
    publishArtifacts = true,
    crossSettings    = Seq(
      description := "Root project for Monix, a library for asynchronous programming in Scala. See: https://monix.io"
    )
  )

lazy val coreJVM = project
  .in(file("monix/jvm"))
  .configure(coreProfile.jvm)
  .dependsOn(executionJVM, catnapJVM, evalJVM, tailJVM, reactiveJVM, javaJVM)
  .aggregate(executionShadedJCTools, executionJVM, catnapJVM, evalJVM, tailJVM, reactiveJVM, javaJVM)

lazy val coreJS = project
  .in(file("monix/js"))
  .configure(coreProfile.js)
  .dependsOn(executionJS, catnapJS, evalJS, tailJS, reactiveJS)
  .aggregate(executionJS, catnapJS, evalJS, tailJS, reactiveJS)

// --------------------------------------------
// monix-internal-jctools (shaded lib)

lazy val executionShadedJCTools = project
  .in(file("monix-execution/shaded/jctools"))
  .configure(
    jvmModule(
      projectName      = "monix-internal-jctools",
      publishArtifacts = true,
      withMimaChecks   = None
    )
  )
  .settings(assemblyShadeSettings)
  .settings(
    description :=
      "Monix Execution Shaded JCTools is a shaded version of JCTools library. See: https://github.com/JCTools/JCTools",
    libraryDependencies := Seq(jcToolsLib % "optional;provided"),
    // https://github.com/sbt/sbt-assembly#shading
    assembly / assemblyShadeRules := Seq(
      ShadeRule
        .rename("org.jctools.**" -> "monix.execution.internal.jctools.@1")
        .inLibrary("org.jctools" % "jctools-core" % jcTools_Version % "optional;provided")
        .inAll
    )
  )

// --------------------------------------------
// monix-execution-atomic

lazy val executionAtomicProfile =
  crossModule(
    projectName      = "monix-execution-atomic",
    withMimaChecks   = None,
    publishArtifacts = true,
    crossSettings    = Seq(
      description := "Sub-module of Monix, exposing low-level atomic references. See: https://monix.io",
    )
  )

lazy val executionAtomicJVM = project.in(file("monix-execution/atomic/jvm"))
  .configure(executionAtomicProfile.jvm)
  .settings(macroDependencies)

lazy val executionAtomicJS = project.in(file("monix-execution/atomic/js"))
  .configure(executionAtomicProfile.js)
  .settings(macroDependencies)

// --------------------------------------------
// monix-execution

lazy val executionProfile =
  crossModule(
    projectName      = "monix-execution",
    withMimaChecks   = Some(MimaFilters.MonixExecution.all),
    publishArtifacts = true,
    crossSettings    = Seq(
      description :=
        "Sub-module of Monix, exposing low-level primitives for dealing with async execution. See: https://monix.io",
      libraryDependencies += implicitBoxLib.value
    )
  )

lazy val executionJVM = project
  .in(file("monix-execution/jvm"))
  .configure(executionProfile.jvm)
  .dependsOn(executionShadedJCTools)
  .aggregate(executionAtomicJVM)
  .dependsOn(executionAtomicJVM)
  .settings(libraryDependencies += reactiveStreamsLib)

lazy val executionJS = project
  .in(file("monix-execution/js"))
  .configure(executionProfile.js)
  .settings(libraryDependencies += macrotaskExecutorLib.value)
  .aggregate(executionAtomicJS)
  .dependsOn(executionAtomicJS)

// --------------------------------------------
// monix-catnap

lazy val catnapProfile =
  crossModule(
    projectName      = "monix-catnap",
    withMimaChecks   = Some(MimaFilters.MonixCatnap.all),
    publishArtifacts = true,
    crossSettings    = Seq(
      description :=
        "Sub-module of Monix, exposing pure abstractions built on top of the Cats-Effect type classes. See: https://monix.io",
      libraryDependencies += catsEffectLib.value
    )
  )

lazy val catnapJVM = project
  .in(file("monix-catnap/jvm"))
  .configure(catnapProfile.jvm)
  .dependsOn(executionJVM % "compile->compile; test->test")

lazy val catnapJS = project
  .in(file("monix-catnap/js"))
  .configure(catnapProfile.js)
  .dependsOn(executionJS % "compile->compile; test->test")

// --------------------------------------------
// monix-catnap

lazy val evalProfile =
  crossModule(
    projectName      = "monix-eval",
    withMimaChecks   = Some(MimaFilters.MonixEval.all),
    publishArtifacts = true,
    crossSettings    = Seq(
      description := "Sub-module of Monix, exposing Task and Coeval, for suspending side-effects. See: https://monix.io"
    )
  )

lazy val evalJVM = project
  .in(file("monix-eval/jvm"))
  .configure(evalProfile.jvm)
  .dependsOn(executionJVM % "compile->compile; test->test")
  .dependsOn(catnapJVM)

lazy val evalJS = project
  .in(file("monix-eval/js"))
  .configure(evalProfile.js)
  .dependsOn(executionJS % "compile->compile; test->test")
  .dependsOn(catnapJS)

// --------------------------------------------
// monix-tail

lazy val tailProfile =
  crossModule(
    projectName      = "monix-tail",
    withMimaChecks   = Some(MimaFilters.MonixTail.all),
    publishArtifacts = true,
    crossSettings    = Seq(
      description :=
        "Sub-module of Monix, exposing Iterant for purely functional pull based streaming. See: https://monix.io"
    )
  )

lazy val tailJVM = project
  .in(file("monix-tail/jvm"))
  .configure(tailProfile.jvm)
  .dependsOn(evalJVM % "test->test")
  .dependsOn(catnapJVM)

lazy val tailJS = project
  .in(file("monix-tail/js"))
  .configure(tailProfile.js)
  .dependsOn(evalJS % "test->test")
  .dependsOn(catnapJS)

// --------------------------------------------
// monix-reactive

lazy val reactiveProfile =
  crossModule(
    projectName      = "monix-reactive",
    withMimaChecks   = Some(MimaFilters.MonixReactive.all),
    publishArtifacts = true,
    crossSettings    = Seq(
      description :=
        "Sub-module of Monix, exposing the Observable pattern for modeling of reactive streams. See: https://monix.io"
    )
  )

lazy val reactiveJVM = project
  .in(file("monix-reactive/jvm"))
  .configure(reactiveProfile.jvm)
  .dependsOn(executionJVM, evalJVM % "compile->compile; test->test")

lazy val reactiveJS = project
  .in(file("monix-reactive/js"))
  .configure(reactiveProfile.js)
  .dependsOn(executionJS, evalJS % "compile->compile; test->test")

// --------------------------------------------
// monix-java

lazy val javaJVM = project
  .in(file("monix-java"))
  .configure(
    monixSubModule(
      projectName      = "monix-java",
      publishArtifacts = true
    )
  )
  .settings(testDependencies)
  .settings(mimaSettings("monix-java", MimaFilters.MonixJava.all))
  .dependsOn(executionJVM % "provided->compile; test->test")
  .dependsOn(evalJVM % "provided->compile; test->test")

// --------------------------------------------
// monix-reactive-tests (not published)

lazy val reactiveTests = project
  .in(file("reactiveTests"))
  .configure(
    monixSubModule(
      "monix-reactive-tests",
      publishArtifacts = false
    )
  )
  .dependsOn(reactiveJVM, tailJVM)
  .settings(
    libraryDependencies ++= Seq(
      reactiveStreamsTCKLib % Test,
      "org.scalatestplus"  %% "testng-7-5" % "3.2.12.0" % Test,
    )
  )

// --------------------------------------------
// monix-tracing-tests (not published)

lazy val FullTracingTest = config("fulltracing").extend(Test)

lazy val tracingTests = project
  .in(file("tracingTests"))
  .configure(
    monixSubModule(
      "monix-tracing-tests",
      publishArtifacts = false
    )
  )
  .dependsOn(evalJVM % "compile->compile; test->test")
  .configs(FullTracingTest)
  .settings(testFrameworks := Seq(new TestFramework("minitest.runner.Framework")))
  .settings(inConfig(FullTracingTest)(Defaults.testSettings): _*)
  .settings(
    FullTracingTest / unmanagedSourceDirectories += {
      baseDirectory.value.getParentFile / "src" / "fulltracing" / "scala"
    },
    Test / test := (Test / test).dependsOn(FullTracingTest / test).value,
    Test / fork := true,
    FullTracingTest / fork := true,
    Test / javaOptions ++= Seq(
      "-Dmonix.eval.tracing=true",
      "-Dmonix.eval.stackTracingMode=cached"
    ),
    FullTracingTest / javaOptions ++= Seq(
      "-Dmonix.eval.tracing=true",
      "-Dmonix.eval.stackTracingMode=full"
    )
  )

// --------------------------------------------
// monix-benchmarks-{prev,next} (not published)

lazy val benchmarksScalaVersions =
  Def.setting {
    crossScalaVersionsFromBuildYaml.value.toIndexedSeq
      .filter(v => !v.value.startsWith("3."))
      .map(_.value)
  }

lazy val benchmarksPrev = project
  .in(file("benchmarks/vprev"))
  .enablePlugins(JmhPlugin)
  .configure(
    monixSubModule(
      "monix-benchmarks-prev",
      publishArtifacts = false
    )
  )
  .settings(
    // Disable Scala 3 (Dotty)
    scalaVersion := benchmarksScalaVersions.value.head,
    crossScalaVersions := benchmarksScalaVersions.value,
    libraryDependencies ++= Seq(
      "io.monix"          %% "monix"       % "3.3.0",
      "dev.zio"           %% "zio-streams" % "1.0.0",
      "co.fs2"            %% "fs2-core"    % fs2_Version,
      "com.typesafe.akka" %% "akka-stream" % "2.6.9"
    )
  )

lazy val benchmarksNext = project
  .in(file("benchmarks/vnext"))
  .enablePlugins(JmhPlugin)
  .configure(
    monixSubModule(
      projectName      = "monix-benchmarks-next",
      publishArtifacts = false
    )
  )
  .dependsOn(reactiveJVM, tailJVM)
  .settings(
    // Disable Scala 3 (Dotty)
    scalaVersion := benchmarksScalaVersions.value.head,
    crossScalaVersions := benchmarksScalaVersions.value,
    libraryDependencies ++= Seq(
      "dev.zio"           %% "zio-streams" % "1.0.0",
      "co.fs2"            %% "fs2-core"    % fs2_Version,
      "com.typesafe.akka" %% "akka-stream" % "2.6.9"
    )
  )
