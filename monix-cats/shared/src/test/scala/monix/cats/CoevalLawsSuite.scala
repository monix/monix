package monix.cats

import algebra.Eq
import algebra.laws.GroupLaws
import monix.eval.Coeval
import monix.cats.tests.EvaluableTests
import org.scalacheck.Arbitrary

object CoevalLawsSuite extends BaseLawsSuite with GroupLaws[Coeval[Int]] {
  // for GroupLaws
  override def Equ: Eq[Coeval[Int]] = equalityCoeval[Int]
  override def Arb: Arbitrary[Coeval[Int]] = arbitraryCoeval[Int]

  checkAll("Group[Coeval[Int]]", GroupLaws[Coeval[Int]].group)
  checkAll("Monoid[Coeval[Int]]", GroupLaws[Coeval[Int]].monoid)
  checkAll("Semigroup[Coeval[Int]]", GroupLaws[Coeval[Int]].semigroup)

  checkAll("Evaluable[Coeval]", EvaluableTests[Coeval].evaluable[Int,Int,Int])
}
