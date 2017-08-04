/*
 * Copyright (c) 2014-2017 by The Monix Project Developers.
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

package monix.eval

import cats.Eq
import cats.effect.IO
import cats.kernel.laws._
import cats.laws.IsEq
import minitest.SimpleTestSuite
import minitest.laws.Checkers
import monix.execution.Cancelable
import monix.execution.internal.Platform
import monix.execution.schedulers.TestScheduler
import monix.execution.schedulers.TrampolineExecutionContext.immediate
import org.scalacheck.Test.Parameters
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

  implicit def equalityTask[A](implicit A: Eq[A], ec: TestScheduler): Eq[Task[A]] =
    new Eq[Task[A]] {
      def eqv(lh: Task[A], rh: Task[A]): Boolean =
        equalityFuture(A, ec).eqv(lh.runAsync, rh.runAsync)
    }

  implicit def equalityIO[A](implicit A: Eq[A], ec: TestScheduler): Eq[IO[A]] =
    new Eq[IO[A]] {
      def eqv(x: IO[A], y: IO[A]): Boolean =
        equalityFuture[A].eqv(x.unsafeToFuture(), y.unsafeToFuture())
    }
}

trait ArbitraryInstancesBase extends cats.instances.AllInstances {
  /** Syntax for equivalence in tests. */
  implicit final class IsEqArrow[A](val lhs: A) {
    def <->(rhs: A): IsEq[A] = IsEq(lhs, rhs)
  }

  implicit def isEqToProp[A](isEq: IsEq[A])(implicit A: Eq[A]): Prop =
    isEq.lhs ?== isEq.rhs

  implicit def isEqListToProp[A](list: List[IsEq[A]])(implicit A: Eq[A]): Prop =
    Prop(list.forall(isEq => A.eqv(isEq.lhs, isEq.rhs)))

  implicit def arbitraryCoeval[A](implicit A: Arbitrary[A]): Arbitrary[Coeval[A]] =
    Arbitrary {
      for {
        a <- A.arbitrary
        coeval <- Gen.oneOf(Coeval.now(a), Coeval.evalOnce(a), Coeval.eval(a))
      } yield coeval
    }

  implicit def arbitraryTask[A](implicit A: Arbitrary[A]): Arbitrary[Task[A]] =
    Arbitrary {
      for {
        a <- A.arbitrary
        task <- Gen.oneOf(
          Task.now(a), Task.evalOnce(a), Task.eval(a),
          Task.create[A] { (_, cb) =>
            cb.onSuccess(a)
            Cancelable.empty
          })
      } yield task
    }

  implicit def arbitraryIO[A](implicit A: Arbitrary[A]): Arbitrary[IO[A]] =
    Arbitrary {
      for {
        a <- A.arbitrary
        io <- Gen.oneOf(
          IO.pure(a), IO(a),
          IO.async[A](f =>
            immediate.execute(new Runnable { def run() = f(Right(a)) })
          ))
      } yield io
    }

  implicit def arbitraryExToA[A](implicit A: Arbitrary[A]): Arbitrary[Throwable => A] =
    Arbitrary {
      val fun = implicitly[Arbitrary[Int => A]]
      for (f <- fun.arbitrary) yield (t: Throwable) => f(t.hashCode())
    }

  implicit def arbitraryPfExToA[A](implicit A: Arbitrary[A]): Arbitrary[PartialFunction[Throwable, A]] =
    Arbitrary {
      val fun = implicitly[Arbitrary[Int => A]]
      for (f <- fun.arbitrary) yield PartialFunction((t: Throwable) => f(t.hashCode()))
    }

  implicit def arbitraryCoevalToLong[A, B](implicit A: Arbitrary[A], B: Arbitrary[B]): Arbitrary[Coeval[A] => B] =
    Arbitrary {
      for (b <- B.arbitrary) yield (_: Coeval[A]) => b
    }

  implicit def arbitraryTaskToLong[A, B](implicit A: Arbitrary[A], B: Arbitrary[B]): Arbitrary[Task[A] => B] =
    Arbitrary {
      for (b <- B.arbitrary) yield (_: Task[A]) => b
    }

  implicit def arbitraryIOToLong[A, B](implicit A: Arbitrary[A], B: Arbitrary[B]): Arbitrary[IO[A] => B] =
    Arbitrary {
      for (b <- B.arbitrary) yield (_: IO[A]) => b
    }

  implicit def equalityCoeval[A](implicit A: Eq[A]): Eq[Coeval[A]] =
    new Eq[Coeval[A]] {
      def eqv(lh: Coeval[A], rh: Coeval[A]): Boolean = {
        val valueA = lh.runTry
        val valueB = rh.runTry

        (valueA.isFailure && valueB.isFailure) ||
          A.eqv(valueA.get, valueB.get)
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
  implicit def cogenForTask[A]: Cogen[Task[A]] =
    Cogen[Unit].contramap(_ => ())
  implicit def cogenForIO[A]: Cogen[IO[A]] =
    Cogen[Unit].contramap(_ => ())
  implicit def cogenForCoeval[A](implicit A: Numeric[A]): Cogen[Coeval[A]] =
    Cogen((x: Coeval[A]) => A.toLong(x.value))
}
