import sbt._
import sbt.{Build => SbtBuild}
import sbt.Keys._


object Build extends SbtBuild {
  def scalatestDependency(scalaVersion: String) = scalaVersion match {
    case "2.8.2" => "org.scalatest" %% "scalatest" % "1.8" % "test"
    case _ => "org.scalatest" %% "scalatest" % "1.9.1" % "test"
  }

  lazy val godzilla = Project(
    id = "monifu",
    base = file("."),
    settings = Project.defaultSettings ++ Seq(
      name := "monifu",
      organization := "org.monifu",
      version := "0.1",

      scalaVersion in ThisBuild := "2.10.3",

      scalacOptions in ThisBuild ++= Seq(
        "-unchecked", "-deprecation", "-feature", "-target:jvm-1.6",
        "-optimize"
      ),

      resolvers ++= Seq(
        "Typesafe Releases" at "http://repo.typesafe.com/typesafe/releases"
      ),

      libraryDependencies ++= Seq("junit" % "junit" % "4.10" % "test"),
      libraryDependencies <+= scalaVersion(scalatestDependency(_)),

      licenses in ThisBuild := Seq("ALv2" -> url("http://www.apache.org/licenses/LICENSE-2.0")),

      homepage in ThisBuild := Some(url("http://www.monifu.org/")),

      pomExtra in ThisBuild := (
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
        </developers>)
    )
  )
}
