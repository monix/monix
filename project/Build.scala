/*
 * Copyright (c) 2014-2015 by its authors. Some rights reserved.
 * See the project homepage at: http://www.monifu.org
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

import com.typesafe.sbt.pgp.PgpKeys
import org.scalajs.sbtplugin.ScalaJSPlugin
import org.scalajs.sbtplugin.ScalaJSPlugin.autoImport._
import sbt.Keys._
import sbt.{Build => SbtBuild, _}
import sbtrelease.ReleasePlugin.autoImport._


object Build extends SbtBuild {
  val dontPublishArtifact = Seq(
    publishArtifact := false,
    publishArtifact in (Compile, packageDoc) := false,
    publishArtifact in (Compile, packageSrc) := false,
    publishArtifact in (Compile, packageBin) := false
  )

  val sharedSettings = Seq(
    organization := "org.monifu",
    scalaVersion := "2.11.7",
    scalacOptions ++= Seq(
      "-unchecked", "-deprecation", "-feature", "-Xlint", "-target:jvm-1.6", "-Yinline-warnings",
      "-optimise", "-Ywarn-adapted-args", "-Ywarn-dead-code", "-Ywarn-inaccessible",
      "-Ywarn-nullary-override", "-Ywarn-nullary-unit"
    ),

    // ScalaDoc settings
    autoAPIMappings := true,
    scalacOptions in (Compile, doc) ++=
      Opts.doc.sourceUrl(s"https://github.com/monifu/monifu/tree/v${version.value}/monifu€{FILE_PATH}.scala"),
    scalacOptions in (Compile, doc) ++=
      Seq("-doc-root-content", baseDirectory.value + "/rootdoc.txt"),
    scalacOptions in (Compile, doc) ++=
      Opts.doc.version(s"${version.value}"),
    scalacOptions in ThisBuild <++= baseDirectory.map { bd =>
      Seq("-sourcepath", bd.getAbsolutePath)
    },

    resolvers ++= Seq(
      "Typesafe Releases" at "http://repo.typesafe.com/typesafe/releases",
      Resolver.sonatypeRepo("releases")
    ),

    libraryDependencies <+= scalaVersion("org.scala-lang" % "scala-reflect" % _ % "compile"),

    unmanagedSourceDirectories in Compile <+= baseDirectory(_ / ".." / "shared" / "src" / "main" / "scala"),
    unmanagedSourceDirectories in Test <+= baseDirectory(_ / ".." / "shared" / "src" / "test" / "scala"),

    // -- Settings meant for deployment on oss.sonatype.org

    publishMavenStyle := true,
    releasePublishArtifactsAction := PgpKeys.publishSigned.value,

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

  lazy val monifu = project.in(file("."))
    .aggregate(monifuJVM, monifuJS, tckTests)
    .settings(sharedSettings: _*)
    .settings(dontPublishArtifact: _*)

  lazy val monifuJVM = project.in(file("jvm"))
    .settings(sharedSettings: _*)
    .settings(
      name := "monifu",
      scalacOptions in (Compile, doc) ++= Opts.doc.title(s"Monifu"),
      testFrameworks += new TestFramework("minitest.runner.Framework"),
      libraryDependencies ++= Seq(
        "org.reactivestreams" % "reactive-streams" % "1.0.0",
        "org.monifu" %% "minitest" % "0.13" % "test"
      ))

  lazy val monifuJS = project.in(file("js"))
    .settings(sharedSettings: _*)
    .enablePlugins(ScalaJSPlugin)
    .settings(
      name := "monifu",
      scalacOptions in (Compile, doc) ++= Opts.doc.title(s"Monifu (JS)"),
      scalaJSStage in Test := FastOptStage,
      testFrameworks += new TestFramework("minitest.runner.Framework"),
      libraryDependencies ++= Seq(
        "org.monifu" %%% "minitest" % "0.13" % "test"
      ))

  lazy val tckTests = project.in(file("tckTests"))
    .settings(sharedSettings: _*)
    .settings(dontPublishArtifact: _*)
    .dependsOn(monifuJVM)
    .settings(
      libraryDependencies ++= Seq(
        "org.reactivestreams" % "reactive-streams-tck" % "1.0.0" % "test",
        "org.scalatest" %% "scalatest" % "2.2.4" % "test"
      ))
}
