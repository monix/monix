package monix.cats.tests

import cats._
import cats.data.{Xor, XorT}
import cats.laws.discipline.CartesianTests.Isomorphisms
import cats.laws.discipline.{CoflatMapTests, MonadErrorTests}
import cats.laws.{CoflatMapLaws, MonadErrorLaws}
import monix.cats.Deferrable
import org.scalacheck.Arbitrary

import scala.language.higherKinds

trait DeferrableTests[F[_]] extends MonadErrorTests[F, Throwable] with CoflatMapTests[F]  {
  def laws: MonadErrorLaws[F, Throwable] with CoflatMapLaws[F]

  def deferrable[A: Arbitrary: Eq, B: Arbitrary: Eq, C: Arbitrary: Eq](implicit
    ArbFA: Arbitrary[F[A]],
    ArbFB: Arbitrary[F[B]],
    ArbFC: Arbitrary[F[C]],
    ArbFAtoB: Arbitrary[F[A => B]],
    ArbFBtoC: Arbitrary[F[B => C]],
    ArbE: Arbitrary[Throwable],
    EqFA: Eq[F[A]],
    EqFB: Eq[F[B]],
    EqFC: Eq[F[C]],
    EqE: Eq[Throwable],
    EqFXorEU: Eq[F[Throwable Xor Unit]],
    EqFXorEA: Eq[F[Throwable Xor A]],
    EqXorTFEA: Eq[XorT[F, Throwable, A]],
    EqFABC: Eq[F[(A, B, C)]],
    iso: Isomorphisms[F]
  ): RuleSet = {
    new RuleSet {
      val name = "deferrable"
      val bases = Nil
      val parents = Seq(monadError[A,B,C], coflatMap[A,B,C])
      val props = Seq.empty
    }
  }
}

object DeferrableTests {
  type Laws[F[_]] = MonadErrorLaws[F, Throwable] with CoflatMapLaws[F]

  def apply[F[_] : Deferrable]: DeferrableTests[F] = {
    val ev = implicitly[Deferrable[F]]

    new DeferrableTests[F] {
      def laws: Laws[F] =
        new MonadErrorLaws[F, Throwable] with CoflatMapLaws[F] {
          implicit override def F: Deferrable[F] = ev
        }
    }
  }
}