import sbt._
import sbt.{Build => SbtBuild}
import sbt.Keys._


object Build extends SbtBuild {
  val buildSettings = Defaults.defaultSettings ++ Seq(
    organization := "org.monifu",
    version := "0.2-SNAPSHOT",
    scalaVersion := "2.10.3",

    scalacOptions ++= Seq(
      "-unchecked", "-deprecation", "-feature", "-Xlint", "-target:jvm-1.6",
      "-optimise", "-Yinline-warnings"
    ),

    resolvers ++= Seq(
      "Typesafe Releases" at "http://repo.typesafe.com/typesafe/releases",
      Resolver.sonatypeRepo("releases")
    ),

    libraryDependencies ++= Seq(
      "org.scalatest" %% "scalatest" % "1.9.1" % "test"
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
        <url>git@github.com:alexandru/monifu.git</url>
        <connection>scm:git:git@github.com:alexandru/monifu.git</connection>
      </scm>
      <developers>
        <developer>
          <id>alex_ndc</id>
          <name>Alexandru Nedelcu</name>
          <url>https://www.bionicspirit.com/</url>
        </developer>
      </developers>
  )

  // -- Actual Projects
  lazy val monifu: Project = Project(
    id = "monifu",
    base = file("."),
    settings = buildSettings
  )
}
