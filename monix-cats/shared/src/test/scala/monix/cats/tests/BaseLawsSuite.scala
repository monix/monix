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

package monix.cats.tests

import cats.{Eq, Eval}
import minitest.SimpleTestSuite
import minitest.laws.Checkers
import monix.cats.MonixToCatsConversions
import monix.eval._
import monix.execution.internal.Platform
import monix.execution.schedulers.TestScheduler
import monix.reactive.Observable
import org.scalacheck.{Arbitrary, Cogen, Gen, Prop}
import org.scalacheck.Test.Parameters
import org.typelevel.discipline.Laws

import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}

trait BaseLawsSuite extends SimpleTestSuite with Checkers with BaseLawsSuiteInstances2 {
  override lazy val checkConfig: Parameters =
    Parameters.default
      .withMinSuccessfulTests(if (Platform.isJVM) 100 else 10)
      .withMaxDiscardRatio(if (Platform.isJVM) 5.0f else 50.0f)

  lazy val slowCheckConfig =
    Parameters.default
      .withMinSuccessfulTests(10)
      .withMaxDiscardRatio(50.0f)
      .withMaxSize(6)

  /** Checks all given Discipline rules. */
  def checkAll(name: String, ruleSet: Laws#RuleSet, config: Parameters = checkConfig): Unit = {
    for ((id, prop: Prop) ← ruleSet.all.properties)
      test(name + "." + id) {
        check(prop)
      }
  }
}

trait BaseLawsSuiteInstances2 extends BaseLawsSuiteInstances1 {
  implicit def equalityObsObs[A : Eq]: Eq[Observable[Observable[A]]] =
    new Eq[Observable[Observable[A]]] {
      def eqv(x: Observable[Observable[A]], y: Observable[Observable[A]]): Boolean =
        equalityObservable[A].eqv(x.flatten, y.flatten)
    }

  implicit def equalityCoevalCoeval[A : Eq]: Eq[Coeval[Coeval[A]]] =
    new Eq[Coeval[Coeval[A]]] {
      def eqv(x: Coeval[Coeval[A]], y: Coeval[Coeval[A]]): Boolean =
        equalityCoeval[A].eqv(x.flatten, y.flatten)
    }

  implicit def equalityTaskTask[A : Eq]: Eq[Task[Task[A]]] =
    new Eq[Task[Task[A]]] {
      def eqv(x: Task[Task[A]], y: Task[Task[A]]): Boolean =
        equalityTask[A].eqv(x.flatten, y.flatten)
    }
}

trait BaseLawsSuiteInstances1 extends cats.instances.AllInstances with MonixToCatsConversions {
  implicit def arbitraryCoeval[A : Arbitrary]: Arbitrary[Coeval[A]] =
    Arbitrary {
      for {
        a <- implicitly[Arbitrary[A]].arbitrary
        coeval <- Gen.oneOf(Coeval.now(a), Coeval.eval(a), Coeval.evalOnce(a))
      } yield coeval
    }

  implicit def arbitraryObservable[A : Arbitrary]: Arbitrary[Observable[A]] =
    Arbitrary {
      implicitly[Arbitrary[List[A]]].arbitrary
        .map(Observable.fromIterable)
    }

  implicit def arbitraryTask[A : Arbitrary]: Arbitrary[Task[A]] =
    Arbitrary {
      for {
        a <- implicitly[Arbitrary[A]].arbitrary
        task <- Gen.oneOf(Task.now(a), Task.evalOnce(a), Task.eval(a))
      } yield task
    }

  implicit def arbitraryEval[A : Arbitrary]: Arbitrary[Eval[A]] =
    Arbitrary {
      for {
        a <- implicitly[Arbitrary[A]].arbitrary
        eval <- Gen.oneOf(Eval.now(a), Eval.always(a), Eval.later(a))
      } yield eval
    }

  implicit lazy val arbitraryThrowable: Arbitrary[Throwable] =
    Arbitrary {
      implicitly[Arbitrary[Int]].arbitrary
        .map(number => new RuntimeException(number.toString))
    }

  implicit lazy val throwableEq = new Eq[Throwable] {
    override def eqv(x: Throwable, y: Throwable): Boolean =
      x == y
  }

  implicit def tuple3Eq[A : Eq]: Eq[(A,A,A)] =
    new Eq[(A,A,A)] {
      val ev = implicitly[Eq[A]]
      def eqv(x: (A, A, A), y: (A, A, A)): Boolean =
        ev.eqv(x._1, y._1) && ev.eqv(x._2, y._2) && ev.eqv(x._3, y._3)
    }

  implicit def tryEq[A : Eq]: Eq[Try[A]] =
    new Eq[Try[A]] {
      val optA = implicitly[Eq[Option[A]]]
      val optT = implicitly[Eq[Option[Throwable]]]

      def eqv(x: Try[A], y: Try[A]): Boolean =
        if (x.isSuccess) optA.eqv(x.toOption, y.toOption)
        else optT.eqv(x.failed.toOption, y.failed.toOption)
    }

  implicit def equalityObservable[A : Eq]: Eq[Observable[A]] =
    new Eq[Observable[A]] {
      def eqv(x: Observable[A], y: Observable[A]): Boolean = {
        implicit val scheduler = TestScheduler()

        var valueA = Option.empty[Try[Option[List[A]]]]
        var valueB = Option.empty[Try[Option[List[A]]]]

        x.foldLeftF(List.empty[A])((acc,e) => e :: acc).firstOptionL.runAsync(
          new Callback[Option[List[A]]] {
            def onError(ex: Throwable): Unit =
              valueA = Some(Failure(ex))
            def onSuccess(value: Option[List[A]]): Unit =
              valueA = Some(Success(value))
          })

        y.foldLeftF(List.empty[A])((acc,e) => e :: acc).firstOptionL.runAsync(
          new Callback[Option[List[A]]] {
            def onError(ex: Throwable): Unit =
              valueB = Some(Failure(ex))
            def onSuccess(value: Option[List[A]]): Unit =
              valueB = Some(Success(value))
          })

        // simulate synchronous execution
        scheduler.tick(1.hour)
        valueA == valueB
      }
    }

  implicit def equalityCoeval[A : Eq]: Eq[Coeval[A]] =
    new Eq[Coeval[A]] {
      val eqA = implicitly[Eq[Try[A]]]

      def eqv(x: Coeval[A], y: Coeval[A]): Boolean =
        eqA.eqv(x.runTry, y.runTry)
    }

  implicit def equalityTask[A : Eq]: Eq[Task[A]] =
    new Eq[Task[A]] {
      def eqv(x: Task[A], y: Task[A]): Boolean = {
        implicit val scheduler = TestScheduler()

        var valueA = Option.empty[Try[A]]
        var valueB = Option.empty[Try[A]]

        x.runAsync(new Callback[A] {
          def onError(ex: Throwable): Unit =
            valueA = Some(Failure(ex))
          def onSuccess(value: A): Unit =
            valueA = Some(Success(value))
        })

        y.runAsync(new Callback[A] {
          def onError(ex: Throwable): Unit =
            valueB = Some(Failure(ex))
          def onSuccess(value: A): Unit =
            valueB = Some(Success(value))
        })

        // simulate synchronous execution
        scheduler.tick(1.hour)
        valueA == valueB
      }
    }

  implicit def cogenForThrowable: Cogen[Throwable] =
    Cogen[String].contramap(_.toString)
  implicit def cogenForTask[A]: Cogen[Task[A]] =
    Cogen[Unit].contramap(_ => ())
  implicit def cogenForCoeval[A](implicit A: Numeric[A]): Cogen[Coeval[A]] =
    Cogen((x: Coeval[A]) => A.toLong(x.value))
  implicit def cogenForObservable[A]: Cogen[Observable[A]] =
    Cogen[Unit].contramap(_ => ())
}