/*
 * Copyright (c) 2014 by its authors. Some rights reserved.
 * See the project homepage at
 *
 *     http://www.monifu.org/
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  	http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import sbt.Keys._
import sbt.{Build => SbtBuild, _}
import sbtunidoc.Plugin.UnidocKeys._
import sbtunidoc.Plugin._
import scala.scalajs.sbtplugin.ScalaJSPlugin._


object Build extends SbtBuild {
  val projectVersion = "0.14.0.RC1"

  val baseSettings = Defaults.defaultSettings ++ Seq(
    organization := "org.monifu",
    version := projectVersion,

    scalaVersion := "2.11.2",
    crossScalaVersions ++= Seq("2.10.4", "2.11.2"),

    initialize := {
       val _ = initialize.value // run the previous initialization
       val classVersion = sys.props("java.class.version")
       val specVersion = sys.props("java.specification.version")
       assert(
        classVersion.toDouble >= 50 && specVersion.toDouble >= 1.6,
        s"JDK version 6 or newer is required for building this project " + 
        s"(SBT instance running on top of JDK $specVersion with class version $classVersion)")
    },

    scalacOptions ++= Seq(
      "-unchecked", "-deprecation", "-feature", "-Xlint", "-target:jvm-1.6", "-Yinline-warnings",
      "-optimise", "-Ywarn-adapted-args", "-Ywarn-dead-code", "-Ywarn-inaccessible",
      "-Ywarn-nullary-override", "-Ywarn-nullary-unit"
    ),

    scalacOptions <<= baseDirectory.map { bd => Seq("-sourcepath", bd.getAbsolutePath) },
    scalacOptions in (ScalaUnidoc, unidoc) <<= baseDirectory.map { bd =>
      Seq(
        "-Ymacro-no-expand",
        "-sourcepath", bd.getAbsolutePath
      )
    },

    parallelExecution in Test := false,

    resolvers ++= Seq(
      "Typesafe Releases" at "http://repo.typesafe.com/typesafe/releases",
      Resolver.sonatypeRepo("releases")
    ),

    // -- Settings meant for deployment on oss.sonatype.org

    publishMavenStyle := true,

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
      <url>http://www.monifu.org/</url>
      <licenses>
        <license>
          <name>Apache License, Version 2.0</name>
          <url>https://www.apache.org/licenses/LICENSE-2.0</url>
          <distribution>repo</distribution>
        </license>
      </licenses>
      <scm>
        <url>git@github.com:monifu/monifu.git</url>
        <connection>scm:git:git@github.com:monifu/monifu.git</connection>
      </scm>
      <developers>
        <developer>
          <id>alex_ndc</id>
          <name>Alexandru Nedelcu</name>
          <url>https://www.bionicspirit.com/</url>
        </developer>
      </developers>
  )

  val nonPublishedSettings = baseSettings ++ Seq(
    publishArtifact := false,
    publishArtifact in (Compile, packageDoc) := false,
    publishArtifact in (Compile, packageSrc) := false,
    publishArtifact in (Compile, packageBin) := false
  )

  // -- Actual Projects

  lazy val root = Project(id="root", base=file("."))
    .settings(nonPublishedSettings : _*)
    .aggregate(monifuJVM, monifuJS, reactiveStreamsTCK)

  lazy val monifuCoreJVM = Project(
    id = "monifu-core",
    base = file("jvm/monifu-core"),
    settings = baseSettings ++ Seq(
      libraryDependencies <+= scalaVersion("org.scala-lang" % "scala-reflect" % _ % "compile"),
      libraryDependencies ++= Seq(
        "org.scalatest" %% "scalatest" % "2.1.3" % "test"
      )
    )
  )

  lazy val monifuRxJVM = Project(
    id = "monifu-rx",
    base = file("jvm/monifu-rx"),
    settings = baseSettings ++ Seq(
      unmanagedSourceDirectories in Compile += baseDirectory.value / "shared" / "main" / "scala",
      libraryDependencies <+= scalaVersion("org.scala-lang" % "scala-reflect" % _ % "compile"),
      libraryDependencies ++= Seq(
        "org.reactivestreams" % "reactive-streams" % "0.4.0",
        "org.scalatest" %% "scalatest" % "2.1.3" % "test"
      )
    )
  ).dependsOn(monifuCoreJVM)

  lazy val reactiveStreamsTCK = Project(id = "streams-tck", base = file("jvm/streams-tck"))
    .settings(nonPublishedSettings ++ Seq(
      libraryDependencies ++= Seq(
        "org.reactivestreams" % "reactive-streams-tck" % "0.4.0",
        "org.scalatest" %% "scalatest" % "2.1.3" % "test"
      )
    ) : _*)
    .dependsOn(monifuRxJVM)

  lazy val monifuJVM = Project(id="monifu", base = file("jvm"),
    settings=baseSettings ++ unidocSettings ++ Seq(
      unidocProjectFilter in (ScalaUnidoc, unidoc) := inProjects(monifuCoreJVM, monifuRxJVM),
      scalacOptions in (ScalaUnidoc, sbtunidoc.Plugin.UnidocKeys.unidoc) ++=
        Opts.doc.sourceUrl(s"https://github.com/monifu/monifu/tree/v$projectVersion/monifu€{FILE_PATH}.scala"),
      scalacOptions in (ScalaUnidoc, sbtunidoc.Plugin.UnidocKeys.unidoc) ++=
        Opts.doc.title(s"Monifu"),
      scalacOptions in (ScalaUnidoc, sbtunidoc.Plugin.UnidocKeys.unidoc) ++=
        Opts.doc.version(s"$projectVersion"),
      scalacOptions in (ScalaUnidoc, sbtunidoc.Plugin.UnidocKeys.unidoc) ++= 
        Seq("-doc-root-content", "rootdoc.txt")
    ))
    .dependsOn(monifuCoreJVM, monifuRxJVM)
    .aggregate(monifuCoreJVM, monifuRxJVM)

  lazy val monifuJS = Project(id="monifu-js", base = file("js"), 
    settings=baseSettings ++ scalaJSSettings ++ unidocSettings ++ Seq(
      scalacOptions in (ScalaUnidoc, sbtunidoc.Plugin.UnidocKeys.unidoc) ++=
        Opts.doc.sourceUrl(s"https://github.com/monifu/monifu.js/tree/v$projectVersion/monifu€{FILE_PATH}.scala"),
      scalacOptions in (ScalaUnidoc, sbtunidoc.Plugin.UnidocKeys.unidoc) ++=
        Opts.doc.title(s"Monifu.js"),
      scalacOptions in (ScalaUnidoc, sbtunidoc.Plugin.UnidocKeys.unidoc) ++=
        Opts.doc.version(s"$projectVersion"),
      scalacOptions in (ScalaUnidoc, sbtunidoc.Plugin.UnidocKeys.unidoc) ++= 
        Seq("-doc-root-content", "rootdoc.txt")
    ))
    .dependsOn(monifuCoreJS, monifuRxJS)
    .aggregate(monifuCoreJS, monifuRxJS)

  lazy val monifuCoreJS = Project(
    id = "monifu-core-js",
    base = file("js/monifu-core"),
    settings = baseSettings ++ scalaJSSettings ++ Seq(
      libraryDependencies <+= scalaVersion("org.scala-lang" % "scala-reflect" % _ % "compile"),
      libraryDependencies ++= Seq(
        "org.scala-lang.modules.scalajs" %% "scalajs-jasmine-test-framework" % scalaJSVersion % "test"
      )
    )
  )

  lazy val monifuRxJS = Project(
    id = "monifu-rx-js",
    base = file("js/monifu-rx"),
    settings = baseSettings ++ scalaJSSettings ++ Seq(
      unmanagedSourceDirectories in Compile += baseDirectory.value / "shared" / "main" / "scala",
      libraryDependencies <+= scalaVersion("org.scala-lang" % "scala-reflect" % _ % "compile"),
      libraryDependencies ++= Seq(
        "org.scala-lang.modules.scalajs" %% "scalajs-jasmine-test-framework" % scalaJSVersion % "test"
      )
    )
  ).dependsOn(monifuCoreJS) 
}
