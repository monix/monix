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

object Build extends SbtBuild {
  val sharedSettings = releaseSettings ++ Seq(
    organization := "org.monifu",
    scalaVersion := "2.11.5",

    scalacOptions <<= baseDirectory.map { bd => Seq("-sourcepath", bd.getAbsolutePath) },

    scalacOptions ++= Seq(
      "-unchecked", "-deprecation", "-feature", "-Xlint", "-target:jvm-1.6", "-Yinline-warnings",
      "-optimise", "-Ywarn-adapted-args", "-Ywarn-dead-code", "-Ywarn-inaccessible",
      "-Ywarn-nullary-override", "-Ywarn-nullary-unit"
    ),

    resolvers ++= Seq(
      "Typesafe Releases" at "http://repo.typesafe.com/typesafe/releases",
      Resolver.sonatypeRepo("releases")
    ),

    libraryDependencies <+= scalaVersion("org.scala-lang" % "scala-reflect" % _ % "compile"),

    // for testing
    testFrameworks += new TestFramework("minitest.runner.Framework"),
    libraryDependencies += "org.monifu" %%% "minitest" % "0.11" % "test",

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
    .aggregate(coreJVM, coreJS, monifuJVM, monifuJS)
    .settings(
      publish := {},
      publishLocal := {}
    )

  // -- Monifu Core

  lazy val core = crossProject.in(file("monifu-core"))
    .settings(sharedSettings : _*)
    .settings(name := "monifu-core")

  lazy val coreJVM = core.jvm
  lazy val coreJS = core.js

  // -- Monifu

  lazy val monifu = crossProject.in(file("monifu"))
    .dependsOn(core)
    .settings(sharedSettings : _*)
    .settings(name := "monifu")
    .jvmSettings(
      libraryDependencies += "org.reactivestreams" % "reactive-streams" % "0.4.0"
    )

  lazy val monifuJVM = monifu.jvm
  lazy val monifuJS = monifu.js
}
