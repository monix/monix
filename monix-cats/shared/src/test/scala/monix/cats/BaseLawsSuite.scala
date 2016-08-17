/*
 * Copyright (c) 2014-2016 by its authors. Some rights reserved.
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

package monix.cats

import cats.data.Xor
import cats.{Eval, Eq}
import minitest.SimpleTestSuite
import minitest.laws.Discipline
import monix.eval._
import monix.execution.internal.Platform
import monix.execution.schedulers.TestScheduler
import monix.reactive.Observable
import org.scalacheck.Arbitrary
import org.scalacheck.Test.Parameters
import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}

trait BaseLawsSuite extends SimpleTestSuite with Discipline with BaseLawsSuiteInstances {
  override lazy val checkConfig: Parameters =
    Parameters.default
      .withMinSuccessfulTests(if (Platform.isJVM) 100 else 10)
      .withMaxDiscardRatio(if (Platform.isJVM) 5.0f else 50.0f)

  lazy val slowCheckConfig = if (Platform.isJVM) checkConfig else
    Parameters.default
      .withMaxSize(1)
      .withMinSuccessfulTests(1)
}

trait BaseLawsSuiteInstances extends AllInstances with cats.std.AllInstances {
  implicit def arbitraryCoeval[A : Arbitrary]: Arbitrary[Coeval[A]] =
    Arbitrary {
      val int = implicitly[Arbitrary[Int]].arbitrary
      for (chance <- int; a <- implicitly[Arbitrary[A]].arbitrary) yield
        if (chance % 3 == 0)
          Coeval.now(a)
        else if (chance % 3 == 1)
          Coeval.evalAlways(a)
        else
          Coeval.evalOnce(a)
    }

  implicit def arbitraryObservable[A : Arbitrary]: Arbitrary[Observable[A]] =
    Arbitrary {
      implicitly[Arbitrary[List[A]]].arbitrary
        .map(Observable.fromIterable)
    }

  implicit def arbitraryTaskStream[A : Arbitrary]: Arbitrary[TaskStream[A]] =
    Arbitrary {
      implicitly[Arbitrary[List[A]]].arbitrary
        .map(list => TaskStream.fromList(list, 32))
    }

  implicit def arbitraryCoevalStream[A : Arbitrary]: Arbitrary[CoevalStream[A]] =
    Arbitrary {
      implicitly[Arbitrary[List[A]]].arbitrary
        .map(list => CoevalStream.fromList(list, 32))
    }

  implicit def arbitraryTask[A : Arbitrary]: Arbitrary[Task[A]] =
    Arbitrary {
      implicitly[Arbitrary[A]].arbitrary
        .map(a => Task.evalAlways(a))
    }

  implicit def arbitraryEval[A : Arbitrary]: Arbitrary[Eval[A]] =
    Arbitrary {
      val int = implicitly[Arbitrary[Int]].arbitrary
      val aa = implicitly[Arbitrary[A]].arbitrary

      int.flatMap(chance => aa.map(a =>
        if (chance % 3 == 0) Eval.now(a)
        else if (chance % 3 == 1) Eval.always(a)
        else Eval.later(a)))
    }

  implicit lazy val arbitraryThrowable: Arbitrary[Throwable] =
    Arbitrary {
      implicitly[Arbitrary[Int]].arbitrary
        .map(number => new RuntimeException(number.toString))
    }

  implicit def arbitrary[E : Arbitrary, A : Arbitrary]: Arbitrary[E Xor A] =
    Arbitrary {
      val int = implicitly[Arbitrary[Int]].arbitrary
      val aa = implicitly[Arbitrary[A]].arbitrary
      val ae = implicitly[Arbitrary[E]].arbitrary

      for (i <- int; a <- aa; e <- ae) yield
        if (i % 2 == 0) Xor.left(e) else Xor.right(a)
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

  implicit def equalityTaskStream[A : Eq]: Eq[TaskStream[A]] =
    new Eq[TaskStream[A]] {
      def eqv(x: TaskStream[A], y: TaskStream[A]): Boolean =
        equalityTask[List[A]].eqv(x.toListL, y.toListL)
    }

  implicit def equalityCoevalStream[A : Eq]: Eq[CoevalStream[A]] =
    new Eq[CoevalStream[A]] {
      def eqv(x: CoevalStream[A], y: CoevalStream[A]): Boolean =
        equalityCoeval[List[A]].eqv(x.toListL, y.toListL)
    }

  implicit def equalityCoeval[A : Eq]: Eq[Coeval[A]] =
    new Eq[Coeval[A]] {
      val eqA = implicitly[Eq[Try[A]]]

      def eqv(x: Coeval[A], y: Coeval[A]): Boolean =
        eqA.eqv(x.runAttempt.asScala, y.runAttempt.asScala)
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
}