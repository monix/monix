import sbt._
import sbt.{Build => SbtBuild}
import sbt.Keys._


object Build extends SbtBuild {
  def scalatestDependency(scalaVersion: String) = scalaVersion match {
    case "2.8.2" => "org.scalatest" %% "scalatest" % "1.8" % "test"
    case _ => "org.scalatest" %% "scalatest" % "1.9.1" % "test"
  }

  lazy val godzilla = Project(
    id = "scala-atomic",
    base = file("."),
    settings = Project.defaultSettings ++ Seq(
      name := "scala-atomic",
      organization := "com.bionicspirit",
      version := "0.1",

      scalaVersion in ThisBuild := "2.10.2",

      crossScalaVersions in ThisBuild := Seq("2.8.2", "2.9.2", "2.9.3", "2.10.2"),

      scalacOptions in ThisBuild ++= Seq(
        "-unchecked", "-deprecation", "-optimize"
      ),

      resolvers ++= Seq(
        "Typesafe Releases" at "http://repo.typesafe.com/typesafe/releases"
      ),

      libraryDependencies ++= Seq("junit" % "junit" % "4.10" % "test"),
      libraryDependencies <+= scalaVersion(scalatestDependency(_)),

      licenses in ThisBuild := Seq("MIT" -> url("http://opensource.org/licenses/MIT")),

      homepage in ThisBuild := Some(url("http://github.com/alexandru/scala-atomic")),

      pomExtra in ThisBuild := (
        <scm>
          <url>git@github.com:alexandru/scala-atomic.git</url>
          <connection>scm:git:git@github.com:alexandru/scala-atomic.git</connection>
        </scm>
        <developers>
          <developer>
            <id>alex_ndc</id>
            <name>Alexandru Nedelcu</name>
            <url>http://bionicspirit.com</url>
          </developer>
        </developers>)
    )
  )
}
