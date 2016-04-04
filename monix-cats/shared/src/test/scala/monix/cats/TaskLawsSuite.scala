package monix.cats

import algebra.Eq
import algebra.laws.GroupLaws
import monix.cats.tests.DeferrableTests
import monix.eval.Task
import org.scalacheck.Arbitrary

object TaskLawsSuite extends BaseLawsSuite with GroupLaws[Task[Int]] {
  // for GroupLaws
  override def Equ: Eq[Task[Int]] = equalityTask[Int]
  override def Arb: Arbitrary[Task[Int]] = arbitraryTask[Int]

  checkAll("Group[Task[Int]]", GroupLaws[Task[Int]].group)
  checkAll("Monoid[Task[Int]]", GroupLaws[Task[Int]].monoid)
  checkAll("Semigroup[Task[Int]]", GroupLaws[Task[Int]].semigroup)

  checkAll("Deferrable[Task]", DeferrableTests[Task].deferrable[Int,Int,Int])
}
