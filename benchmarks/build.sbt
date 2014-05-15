libraryDependencies ++= Seq(
  "com.google.code.java-allocation-instrumenter" % "java-allocation-instrumenter" % "2.0",
  "com.google.code.gson" % "gson" % "1.7.1"
)
 
resolvers += "sonatypeSnapshots" at "http://oss.sonatype.org/content/repositories/snapshots"

fork in run := true

publishArtifact := false

// we need to add the runtime classpath as a "-cp" argument to the `javaOptions in run`, otherwise caliper
// will not see the right classpath and die with a ConfigurationException
javaOptions in run ++= Seq("-cp",
  Attributed.data((fullClasspath in Runtime).value).mkString(":"))

