/*
 * Copyright (c) 2014-2018 by The Monix Project Developers.
 * See the project homepage at: https://monix.io
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package monix.execution

import cats.Eq
import cats.laws._
//import cats.kernel.laws._

import minitest.SimpleTestSuite
import minitest.laws.Checkers
import monix.execution.exceptions.DummyException
import org.scalacheck.Test.Parameters
import monix.execution.internal.Platform
import monix.execution.schedulers.TestScheduler
import org.scalacheck.{Arbitrary, Cogen, Gen, Prop}
import org.typelevel.discipline.Laws

import scala.concurrent.duration._
import scala.concurrent.{ExecutionException, Future}
import scala.util.{Failure, Success, Try}

trait BaseLawsSuite extends SimpleTestSuite with Checkers with ArbitraryInstances {
  override lazy val checkConfig: Parameters =
    Parameters.default
      .withMinSuccessfulTests(if (Platform.isJVM) 100 else 10)
      .withMaxDiscardRatio(if (Platform.isJVM) 5.0f else 50.0f)

  lazy val slowCheckConfig: Parameters =
    Parameters.default
      .withMinSuccessfulTests(10)
      .withMaxDiscardRatio(50.0f)
      .withMaxSize(6)

  def checkAll(name: String, ruleSet: Laws#RuleSet, config: Parameters = checkConfig): Unit = {
    for ((id, prop: Prop) ← ruleSet.all.properties)
      test(name + "." + id) {
        check(prop)
      }
  }

  def checkAllAsync(name: String, config: Parameters = checkConfig)
    (f: TestScheduler => Laws#RuleSet): Unit = {

    val s = TestScheduler()
    val ruleSet = f(s)

    for ((id, prop: Prop) ← ruleSet.all.properties)
      test(name + "." + id) {
        s.tick(1.day)
        check(prop)
      }
  }
}

trait ArbitraryInstances extends ArbitraryInstancesBase {
  /** Syntax for equivalence in tests. */
  implicit def isEqListToProp[A](list: List[IsEq[A]])(implicit A: Eq[A]): Prop =
    Prop(list.forall(isEq => A.eqv(isEq.lhs, isEq.rhs)))

  implicit def equalityCancelableFuture[A](implicit A: Eq[A], ec: TestScheduler): Eq[CancelableFuture[A]] =
    new Eq[CancelableFuture[A]] {
      val inst = equalityFuture[A]
      def eqv(x: CancelableFuture[A], y: CancelableFuture[A]) =
        inst.eqv(x, y)
    }

  implicit def arbitraryCancelableFuture[A]
    (implicit A: Arbitrary[A], ec: Scheduler): Arbitrary[CancelableFuture[A]] =
    Arbitrary {
      for {
        a <- A.arbitrary
        future <- Gen.oneOf(
          CancelableFuture.pure(a),
          CancelableFuture.raiseError(DummyException(a.toString)),
          CancelableFuture.async[A](cb => { cb(Success(a)); Cancelable.empty }),
          CancelableFuture.async[A](cb => { cb(Failure(DummyException(a.toString))); Cancelable.empty }),
          CancelableFuture.pure(a).flatMap(CancelableFuture.pure))
      } yield future
    }

  implicit def arbitraryThrowable: Arbitrary[Throwable] =
    Arbitrary {
      val msg = implicitly[Arbitrary[Int]]
      for (a <- msg.arbitrary) yield DummyException(a.toString)
    }

  implicit def cogenForCancelableFuture[A]: Cogen[CancelableFuture[A]] =
    Cogen[Unit].contramap(_ => ())
}

trait ArbitraryInstancesBase extends cats.instances.AllInstances {
  implicit def equalityFuture[A](implicit A: Eq[A], ec: TestScheduler): Eq[Future[A]] =
    new Eq[Future[A]] {
      def eqv(x: Future[A], y: Future[A]): Boolean = {
        // Executes the whole pending queue of runnables
        ec.tick(1.day)

        x.value match {
          case None =>
            y.value.isEmpty
          case Some(Success(a)) =>
            y.value match {
              case Some(Success(b)) => A.eqv(a, b)
              case _ => false
            }
          case Some(Failure(ex1)) =>
            y.value match {
              case Some(Failure(ex2)) =>
                equalityThrowable.eqv(ex1, ex2)
              case _ =>
                false
            }
        }
      }
    }

  implicit lazy val equalityThrowable = new Eq[Throwable] {
    override def eqv(x: Throwable, y: Throwable): Boolean = {
      val ex1 = extractEx(x)
      val ex2 = extractEx(y)
      ex1.getClass == ex2.getClass && ex1.getMessage == ex2.getMessage
    }

    // Unwraps exceptions that got caught by Future's implementation
    // and that got wrapped in ExecutionException (`Future(throw ex)`)
    def extractEx(ex: Throwable): Throwable =
      ex match {
        case ref: ExecutionException =>
          Option(ref.getCause).getOrElse(ref)
        case _ =>
          ex
      }
  }

  implicit def equalityTry[A : Eq]: Eq[Try[A]] =
    new Eq[Try[A]] {
      val optA = implicitly[Eq[Option[A]]]
      val optT = implicitly[Eq[Option[Throwable]]]

      def eqv(x: Try[A], y: Try[A]): Boolean =
        if (x.isSuccess) optA.eqv(x.toOption, y.toOption)
        else optT.eqv(x.failed.toOption, y.failed.toOption)
    }

  implicit def cogenForThrowable: Cogen[Throwable] =
    Cogen[String].contramap(_.toString)
  implicit def cogenForFuture[A]: Cogen[Future[A]] =
    Cogen[Unit].contramap(_ => ())
}
