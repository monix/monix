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
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import org.scalajs.sbtplugin.ScalaJSPlugin
import org.scalajs.sbtplugin.ScalaJSPlugin.autoImport._
import sbt.Keys._
import sbt.{Build => SbtBuild, _}
import sbtrelease.ReleasePlugin._
import sbtrelease.ReleasePlugin.ReleaseKeys._
import sbtunidoc.Plugin.UnidocKeys._
import sbtunidoc.Plugin._


object Build extends SbtBuild {
  val compilerSettings = Seq(
    unmanagedSourceDirectories in Compile <+= baseDirectory(_ /  "shared" / "main" / "scala"),
    unmanagedSourceDirectories in Test <+= baseDirectory(_ / "shared" / "test" / "scala"),

    scalacOptions <<= baseDirectory.map { bd => Seq("-sourcepath", bd.getAbsolutePath) },
    scalacOptions in (ScalaUnidoc, unidoc) <<= baseDirectory.map { bd =>
      Seq(
        "-Ymacro-no-expand",
        "-sourcepath", bd.getAbsolutePath
      )
    },

    scalacOptions ++= Seq(
      "-unchecked", "-deprecation", "-feature", "-Xlint", "-target:jvm-1.6", "-Yinline-warnings",
      "-optimise", "-Ywarn-adapted-args", "-Ywarn-dead-code", "-Ywarn-inaccessible",
      "-Ywarn-nullary-override", "-Ywarn-nullary-unit"
    )
  )

  val sharedSettings = releaseSettings ++ Seq(
    organization := "org.monifu",
    scalaVersion := "2.11.5",

    resolvers ++= Seq(
      "Typesafe Releases" at "http://repo.typesafe.com/typesafe/releases",
      Resolver.sonatypeRepo("releases")
    ),

    libraryDependencies <+= scalaVersion("org.scala-lang" % "scala-reflect" % _ % "compile"),

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

  // -- Root aggregating everything

  lazy val root = project.in(file("."))
    .aggregate(coreJVM, coreJS, jvm, js)
    .settings(sharedSettings : _*)
    .settings(
      name := "root",
      publishArtifact := false,
      publishArtifact in (Compile, packageDoc) := false,
      publishArtifact in (Compile, packageSrc) := false,
      publishArtifact in (Compile, packageBin) := false
    )

  // -- Monifu Core

  lazy val coreJVM = project.in(file("monifu-core/jvm"))
    .settings(sharedSettings ++ compilerSettings : _*)
    .settings(
      name := "monifu-core",
      libraryDependencies ++= Seq(
        "org.scalatest" %% "scalatest" % "2.1.3" % "test"
      )
    )

  lazy val coreJS = project.in(file("monifu-core/js"))
    .settings(sharedSettings ++ compilerSettings : _*)
    .enablePlugins(ScalaJSPlugin)
    .settings(
      name := "monifu-core"
    )

  // -- Monifu

  lazy val jvm = project.in(file("monifu/jvm"))
    .dependsOn(coreJVM)
    .settings(sharedSettings ++ compilerSettings ++ unidocSettings : _*)
    .settings(
      name := "monifu",
      scalacOptions in (ScalaUnidoc, sbtunidoc.Plugin.UnidocKeys.unidoc) ++=
        Opts.doc.sourceUrl(s"https://github.com/monifu/monifu/tree/v${version.value}/monifu€{FILE_PATH}.scala"),
      scalacOptions in (ScalaUnidoc, sbtunidoc.Plugin.UnidocKeys.unidoc) ++=
        Opts.doc.title(s"Monifu"),
      scalacOptions in (ScalaUnidoc, sbtunidoc.Plugin.UnidocKeys.unidoc) ++=
        Opts.doc.version(s"${version.value}"),
      scalacOptions in (ScalaUnidoc, sbtunidoc.Plugin.UnidocKeys.unidoc) ++=
        Seq("-doc-root-content", "monifu/jvm/rootdoc.txt"),

      libraryDependencies ++= Seq(
        "org.reactivestreams" % "reactive-streams" % "0.4.0",
        "org.reactivestreams" % "reactive-streams-tck" % "0.4.0" % "test",
        "org.scalatest" %% "scalatest" % "2.1.3" % "test"
      )
    )

  lazy val js = project.in(file("monifu/js"))
    .dependsOn(coreJS)
    .enablePlugins(ScalaJSPlugin)
    .settings(sharedSettings ++ compilerSettings ++ unidocSettings : _*)
    .settings(
      name := "monifu",
      scalacOptions in (ScalaUnidoc, sbtunidoc.Plugin.UnidocKeys.unidoc) ++=
        Opts.doc.sourceUrl(s"https://github.com/monifu/monifu.js/tree/v${version.value}/monifu€{FILE_PATH}.scala"),
      scalacOptions in (ScalaUnidoc, sbtunidoc.Plugin.UnidocKeys.unidoc) ++=
        Opts.doc.title(s"Monifu.js"),
      scalacOptions in (ScalaUnidoc, sbtunidoc.Plugin.UnidocKeys.unidoc) ++=
        Opts.doc.version(s"${version.value}"),
      scalacOptions in (ScalaUnidoc, sbtunidoc.Plugin.UnidocKeys.unidoc) ++=
        Seq("-doc-root-content", "monifu/js/rootdoc.txt")
    )
}
