import sbt.Keys._
import sbt._
import sbt.io.Using

import scala.annotation.tailrec
import scala.collection.JavaConverters._
import scala.collection.mutable
import scala.collection.immutable.SortedSet
import scala.util.matching.Regex
import scala.xml.Elem
import scala.xml.transform.{RewriteRule, RuleTransformer}

final case class MonixScalaVersion(value: String) {
  lazy val parts = value.split("[.]").filter(_.nonEmpty).toList
}

object MonixScalaVersion {
  implicit object Ord extends Ordering[MonixScalaVersion] {
    override def compare(x: MonixScalaVersion, y: MonixScalaVersion): Int =
      -1 * compare(x.parts, y.parts)

    @tailrec
    private def compare(l1: List[String], l2: List[String]): Int =
      l1 match {
        case h1 :: r1 =>
          l2 match {
            case h2 :: r2 =>
              h1.compare(h2) match {
                case 0 => compare(r1, r2)
                case r => r
              }
            case Nil =>
              1
          }
        case Nil =>
          -1
      }
  }
}

final case class MonixCrossModule(
  jvm: Project => Project,
  js: Project => Project
)

object MonixBuildUtils {
  /**
    * Applies [[filterOutDependencyFromGeneratedPomXml]] to a list of multiple dependencies.
    */
  def filterOutMultipleDependenciesFromGeneratedPomXml(list: List[(String, Regex)]*) =
    list.foldLeft(List.empty[Def.Setting[_]]) { (acc, elem) =>
      acc ++ filterOutDependencyFromGeneratedPomXml(elem: _*)
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
        conditions.forall {
          case (key, regex) =>
            e.child.exists(child => child.label == key && regex.findFirstIn(child.text).isDefined)
        }
      }

    if (conditions.isEmpty) Nil
    else {
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

  /**
    * Reads the Scala versions from `.github/workflows/build.yml`.
    */
  def scalaVersionsFromBuildYaml(manifest: File): SortedSet[MonixScalaVersion] = {
    Using.fileInputStream(manifest) { fis =>
      val yaml = new org.yaml.snakeyaml.Yaml()
        .loadAs(fis, classOf[java.util.Map[Any, Any]])
        .asScala

      val scalaVersions = yaml
        .get("jobs")
        .collect { case map: java.util.Map[Any, Any] @unchecked => map.asScala }
        .getOrElse(mutable.Map.empty[Any, Any])
        .get("jvm-tests")
        .collect { case map: java.util.Map[Any, Any] @unchecked => map.asScala }
        .getOrElse(mutable.Map.empty[Any, Any])
        .get("strategy")
        .collect { case map: java.util.Map[Any, Any] @unchecked => map.asScala }
        .getOrElse(mutable.Map.empty[Any, Any])
        .get("matrix")
        .collect { case map: java.util.Map[Any, Any] @unchecked => map.asScala }
        .getOrElse(mutable.Map.empty[Any, Any])
        .get("scala")
        .collect { case list: java.util.List[Any] @unchecked => list.asScala }
        .getOrElse(Iterable.empty[Any])
        .collect { case v: String => MonixScalaVersion(v) }
        .toSeq

      assert(scalaVersions.nonEmpty, "build.yml is corrupt, suitable scala_version_* keys missing")
      SortedSet(scalaVersions: _*)
    }
  }
}
