import sbt._
import sbt.{Build => SbtBuild}
import sbt.Keys._
import scala.scalajs.sbtplugin.ScalaJSPlugin._
import scala.scalajs.sbtplugin.ScalaJSPlugin.ScalaJSKeys._


object Build extends SbtBuild {

  val sharedSettings = Defaults.defaultSettings ++ Seq(
    organization := "org.monifu",
    version := "0.8-SNAPSHOT",
    scalaVersion := "2.10.4",
    crossScalaVersions := Seq("2.10.4", "2.11.0"),

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

  lazy val root = Project(id = "monifu", base = file("."), settings = sharedSettings)
    .aggregate(monifuCore, monifuCoreJS)
    .dependsOn(monifuCore)

  lazy val monifuCore = Project(
    id = "monifu-core",
    base = file("monifu-core"),
    settings = sharedSettings ++ Seq(
      unmanagedSourceDirectories in Compile <+= sourceDirectory(_ / "shared" / "scala"),
      libraryDependencies <+= scalaVersion("org.scala-lang" % "scala-reflect" % _ % "compile"),
      libraryDependencies ++= Seq(
        "org.scalatest" %% "scalatest" % "2.1.3" % "test"
      )
    )
  )

  lazy val monifuCoreJS = Project(
    id = "monifu-core-js",
    base = file("monifu-core-js"),
    settings = sharedSettings ++ scalaJSSettings ++ Seq(
      unmanagedSourceDirectories in Compile <+= sourceDirectory(_ / "shared" / "scala"),
      libraryDependencies <+= scalaVersion("org.scala-lang" % "scala-reflect" % _ % "compile"),
      libraryDependencies ++= Seq(
        "org.scala-lang.modules.scalajs" %% "scalajs-jasmine-test-framework" % scalaJSVersion % "test"
      )
    )
  )

  lazy val monifuBenchmarks =
    Project(id="benchmarks", base=file("benchmarks")).dependsOn(monifuCore)
}
