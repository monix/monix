resolvers += Resolver.url("scala-js-releases",
  url("http://dl.bintray.com/content/scala-js/scala-js-releases"))(
    Resolver.ivyStylePatterns)

addSbtPlugin("org.scala-lang.modules.scalajs" % "scalajs-sbt-plugin" % "0.5.5")

addSbtPlugin("com.eed3si9n" % "sbt-unidoc" % "0.3.1")

