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

import monix.execution.Cancelable
import monix.execution.internal.Platform
import monix.execution.schedulers.TestScheduler
import monix.types.tests.Eq
import org.scalacheck.{Arbitrary, Gen}
import org.scalacheck.Test.Parameters

import scala.util.{Failure, Success, Try}
import concurrent.duration._

trait BaseLawsSuite extends monix.types.tests.BaseLawsSuite with ArbitraryInstances {
  override lazy val checkConfig: Parameters =
    Parameters.default
      .withMinSuccessfulTests(if (Platform.isJVM) 100 else 10)
      .withMaxDiscardRatio(if (Platform.isJVM) 5.0f else 50.0f)
      .withMaxSize(32)
}

trait ArbitraryInstances {
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
      for (b <- B.arbitrary) yield (t: Coeval[A]) => b
    }

  implicit def arbitraryTaskToLong[A, B](implicit A: Arbitrary[A], B: Arbitrary[B]): Arbitrary[Task[A] => B] =
    Arbitrary {
      for (b <- B.arbitrary) yield (t: Task[A]) => b
    }

  implicit def isEqCoeval[A](implicit A: Eq[A]): Eq[Coeval[A]] =
    new Eq[Coeval[A]] {
      def apply(lh: Coeval[A], rh: Coeval[A]): Boolean = {
        val valueA = lh.runTry
        val valueB = rh.runTry

        (valueA.isFailure && valueB.isFailure) ||
          A(valueA.get, valueB.get)
      }
    }

  implicit def isEqTask[A](implicit A: Eq[A]): Eq[Task[A]] =
    new Eq[Task[A]] {
      def apply(lh: Task[A], rh: Task[A]): Boolean = {
        implicit val s = TestScheduler()
        var valueA = Option.empty[Try[A]]
        var valueB = Option.empty[Try[A]]

        lh.runAsync(new Callback[A] {
          def onError(ex: Throwable): Unit =
            valueA = Some(Failure(ex))

          def onSuccess(value: A): Unit =
            valueA = Some(Success(value))
        })

        rh.runAsync(new Callback[A] {
          def onError(ex: Throwable): Unit =
            valueB = Some(Failure(ex))

          def onSuccess(value: A): Unit =
            valueB = Some(Success(value))
        })

        // simulate synchronous execution
        s.tick(1.hour)

        if (valueA.isEmpty)
          valueB.isEmpty
        else {
          (valueA.get.isFailure && valueB.get.isFailure) ||
            A(valueA.get.get, valueB.get.get)
        }
      }
    }

  implicit def isEqList[A](implicit A: Eq[A]): Eq[List[A]] =
    new Eq[List[A]] {
      def apply(x: List[A], y: List[A]): Boolean = {
        val cx = x.iterator
        val cy = y.iterator
        var areEqual = true
        var hasX = true
        var hasY = true

        while (areEqual && hasX && hasY) {
          hasX = cx.hasNext
          hasY = cy.hasNext
          if (!hasX || !hasY)
            areEqual = hasX == hasY
          else
            areEqual = A(cx.next(), cy.next())
        }

        areEqual
      }
    }
}
