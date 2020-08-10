import net.bzzt.reproduciblebuilds.ReproducibleBuildsPlugin
import sbt.Keys._
import sbt._
import sbtassembly.AssemblyPlugin.autoImport.{assembly, assemblyOption}

import scala.util.matching.Regex
import scala.xml.Elem
import scala.xml.transform.{RewriteRule, RuleTransformer}

object MonixBuildUtils {

  final case class CrossModule(
    jvm: Project => Project,
    js: Project => Project
  )

  /**
    * Applies [[filterOutDependencyFromGeneratedPomXml]] to a list of multiple dependencies.
    */
  def filterOutMultipleDependenciesFromGeneratedPomXml(list: List[(String, Regex)]*) =
    list.foldLeft(List.empty[Def.Setting[_]]) { (acc, elem) =>
      acc ++ filterOutDependencyFromGeneratedPomXml(elem:_*)
    }

  /**
    * Filter out dependencies from the generated `pom.xml`.
    *
    * E.g. to exclude Scoverage:
    * {{{
    *   filterOutDependencyFromGeneratedPomXml("groupId" -> "org.scoverage")
    * }}}
    *
    * Or to exclude based on both `groupId` and `artifactId`:
    * {{{
    *   filterOutDependencyFromGeneratedPomXml("groupId" -> "io\\.estatico".r, "artifactId" -> "newtype".r)
    * }}}
    */
  def filterOutDependencyFromGeneratedPomXml(conditions: (String, Regex)*) = {
    def shouldExclude(e: Elem) =
      e.label == "dependency" && {
        conditions.forall { case (key, regex) =>
          e.child.exists(child => child.label == key && regex.findFirstIn(child.text).isDefined)
        }
      }

    if (conditions.isEmpty) Nil else {
      Seq(
        // For evicting Scoverage out of the generated POM
        // See: https://github.com/scoverage/sbt-scoverage/issues/153
        pomPostProcess := { (node: xml.Node) =>
          new RuleTransformer(new RewriteRule {
            override def transform(node: xml.Node): Seq[xml.Node] = node match {
              case e: Elem if shouldExclude(e) => Nil
              case _ => Seq(node)
            }
          }).transform(node).head
        }
      )
    }
  }
}
