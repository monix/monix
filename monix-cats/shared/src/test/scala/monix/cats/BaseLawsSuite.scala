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
import org.scalacheck.Test.Parameters.Default
import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}

trait BaseLawsSuite extends SimpleTestSuite with Discipline with BaseLawsSuiteInstances1 {
  override val checkConfig: Parameters = new Default {
    override val minSuccessfulTests: Int =
      if (Platform.isJVM) 100 else 10
    override val maxDiscardRatio: Float =
      if (Platform.isJVM) 5.0f else 50.0f
  }

  val slowCheckConfig = if (Platform.isJVM) checkConfig else
    new Default {
      override val maxSize: Int = 1
      override val minSuccessfulTests: Int = 1
    }
}

trait BaseLawsSuiteInstances1 extends BaseLawsSuiteInstances0 {
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

  implicit def equalityCoeval[A : Eq]: Eq[Coeval[A]] =
    new Eq[Coeval[A]] {
      val eqA = implicitly[Eq[Try[A]]]

      def eqv(x: Coeval[A], y: Coeval[A]): Boolean =
        eqA.eqv(x.runAttempt.asScala, y.runAttempt.asScala)
    }
}

trait BaseLawsSuiteInstances0 extends AllInstances with cats.std.AllInstances {
  implicit def arbitraryObservable[A : Arbitrary]: Arbitrary[Observable[A]] =
    Arbitrary {
      implicitly[Arbitrary[List[A]]].arbitrary
        .map(Observable.fromIterable)
    }

  implicit def arbitraryAsyncIterator[A : Arbitrary]: Arbitrary[TaskEnumerator[A]] =
    Arbitrary {
      val listA = implicitly[Arbitrary[List[A]]].arbitrary
      for (list <- listA) yield TaskEnumerator.wait(TaskEnumerator.fromList(list, 16))
    }

  implicit def arbitraryLazyIterator[A : Arbitrary]: Arbitrary[CoevalEnumerator[A]] =
    Arbitrary {
      val listA = implicitly[Arbitrary[List[A]]].arbitrary
      for (list <- listA) yield CoevalEnumerator.wait(CoevalEnumerator.fromList(list, 16))
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

  implicit val arbitraryThrowable: Arbitrary[Throwable] =
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

  implicit val throwableEq = new Eq[Throwable] {
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

  implicit def equalityObs[A : Eq]: Eq[Observable[A]] =
    new Eq[Observable[A]] {
      def eqv(x: Observable[A], y: Observable[A]): Boolean = {
        implicit val scheduler = TestScheduler()

        var valueA = Option.empty[Try[Option[List[A]]]]
        var valueB = Option.empty[Try[Option[List[A]]]]

        x.foldLeftF(List.empty[A])((acc,e) => e :: acc).firstL.runAsync(
          new Callback[Option[List[A]]] {
            def onError(ex: Throwable): Unit =
              valueA = Some(Failure(ex))
            def onSuccess(value: Option[List[A]]): Unit =
              valueA = Some(Success(value))
          })

        y.foldLeftF(List.empty[A])((acc,e) => e :: acc).firstL.runAsync(
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

  implicit def equalityAsyncIterator[A : Eq]: Eq[TaskEnumerator[A]] =
    new Eq[TaskEnumerator[A]] {
      def eqv(x: TaskEnumerator[A], y: TaskEnumerator[A]): Boolean = {
        implicit val scheduler = TestScheduler()

        var valueA = Option.empty[Try[List[A]]]
        var valueB = Option.empty[Try[List[A]]]

        x.foldLeftL(List.empty[A])((acc,e) => e :: acc).runAsync(
          new Callback[List[A]] {
            def onError(ex: Throwable): Unit =
              valueA = Some(Failure(ex))
            def onSuccess(value: List[A]): Unit =
              valueA = Some(Success(value))
          })

        y.foldLeftL(List.empty[A])((acc,e) => e :: acc).runAsync(
          new Callback[List[A]] {
            def onError(ex: Throwable): Unit =
              valueB = Some(Failure(ex))
            def onSuccess(value: List[A]): Unit =
              valueB = Some(Success(value))
          })

        // simulate synchronous execution
        scheduler.tick(1.hour)
        valueA == valueB
      }
    }

  implicit def equalityLazyIterator[A : Eq]: Eq[CoevalEnumerator[A]] =
    new Eq[CoevalEnumerator[A]] {
      def eqv(x: CoevalEnumerator[A], y: CoevalEnumerator[A]): Boolean =
        x.toListL.runTry == y.toListL.runTry
    }
}