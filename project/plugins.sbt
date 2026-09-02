addSbtPlugin("org.scala-js"       % "sbt-scalajs"             % "1.22.0")
addSbtPlugin("com.github.sbt"     % "sbt-unidoc"              % "0.6.1")
addSbtPlugin("com.eed3si9n"       % "sbt-salad-days"          % "0.2.0")
addSbtPlugin("pl.project13.scala" % "sbt-jmh"                 % "0.4.8")
addSbtPlugin("com.typesafe"       % "sbt-mima-plugin"         % "1.1.6")
addSbtPlugin("com.github.sbt"     % "sbt-header"              % "5.11.0")
addSbtPlugin("org.scalameta"      % "sbt-scalafmt"            % "2.6.2")
addSbtPlugin("org.scoverage"      % "sbt-scoverage"           % "2.4.4")
addSbtPlugin("com.eed3si9n"       % "sbt-assembly"            % "2.5.0")
addSbtPlugin("net.bzzt"           % "sbt-reproducible-builds" % "0.35")
addSbtPlugin("org.typelevel"      % "sbt-tpolecat"            % "0.5.7")
addSbtPlugin("com.github.sbt"     % "sbt-pgp"                 % "2.3.2")

libraryDependencies += "org.typelevel" %% "scalac-options" % "0.1.11"
