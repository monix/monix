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

package monix.reactive

import minitest.TestSuite
import minitest.laws.Checkers
import monix.eval.{Callback, Coeval, Task}
import monix.execution.schedulers.TestScheduler
import org.scalacheck.{Arbitrary, Prop}

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.language.implicitConversions
import scala.util.{Failure, Success, Try}

/** For expressing equivalence. */
final case class IsEquiv[A](lh: A, rh: A)

/** For negating equivalence. */
final case class IsNotEquiv[A](lh: A, rh: A)

trait BaseLawsTestSuite extends TestSuite[TestScheduler] with Checkers {
  def setup(): TestScheduler = TestScheduler()
  def tearDown(env: TestScheduler): Unit = {
    assert(env.state.get.tasks.isEmpty, "should not have tasks left to execute")
  }

  implicit def arbitraryObservable[A : Arbitrary]: Arbitrary[Observable[A]] =
    Arbitrary {
      implicitly[Arbitrary[List[A]]].arbitrary
        .map(Observable.fromIterable)
    }

  implicit lazy val arbitraryThrowable: Arbitrary[Throwable] =
    Arbitrary {
      implicitly[Arbitrary[Int]].arbitrary
        .map(number => new RuntimeException(number.toString))
    }

  /** Implicitly map [[IsEquiv]] to a [[Prop]]. */
  implicit def isEqObservableProp[A](isEq: IsEquiv[Observable[A]])(implicit s: TestScheduler): Prop =
    Prop {
      val fa = isEq.lh.foldLeftF(List.empty[A])((acc,e) => e :: acc).firstOptionL.runAsync
      val fb = isEq.rh.foldLeftF(List.empty[A])((acc,e) => e :: acc).firstOptionL.runAsync
      // simulate asynchronous execution
      s.tick(1.hour)
      fa.value == fb.value
    }

  /** Implicitly map [[IsEquiv]] to a [[Prop]]. */
  implicit def isEqTaskProp[A](isEq: IsEquiv[Task[A]])(implicit s: TestScheduler): Prop =
    Prop {
      var valueA = Option.empty[Try[A]]
      var valueB = Option.empty[Try[A]]

      isEq.lh.runAsync(new Callback[A] {
        def onError(ex: Throwable): Unit =
          valueA = Some(Failure(ex))
        def onSuccess(value: A): Unit =
          valueA = Some(Success(value))
      })

      isEq.rh.runAsync(new Callback[A] {
        def onError(ex: Throwable): Unit =
          valueB = Some(Failure(ex))
        def onSuccess(value: A): Unit =
          valueB = Some(Success(value))
      })

      // simulate asynchronous execution
      s.tick(1.hour)
      valueA == valueB
    }

  implicit def isEqFutureProp[A](isEq: IsEquiv[Future[A]])(implicit s: TestScheduler): Prop =
    Prop {
      // simulate asynchronous execution
      s.tick(1.hour)
      isEq.lh.value == isEq.rh.value
    }

  /** Implicitly map [[IsEquiv]] to a [[Prop]]. */
  implicit def isEqCoevalProp[A](isEq: IsEquiv[Coeval[A]]): Prop =
    Prop { isEq.lh.runTry == isEq.rh.runTry }

  /** Implicitly map [[IsNotEquiv]] to a [[Prop]]. */
  implicit def isNotEqCoevalProp[A](isNotEq: IsNotEquiv[Coeval[A]]): Prop =
    Prop { isNotEq.lh.runTry != isNotEq.rh.runTry }

  /** Implicitly map [[IsEquiv]] to a [[Prop]]. */
  implicit def isEqSeqCoevalProp[A](list: List[IsEquiv[Coeval[A]]]): Prop =
    Prop { list.forall(isEq => isEq.lh.runTry == isEq.rh.runTry ) }

  /** Implicitly map [[IsNotEquiv]] to a [[Prop]]. */
  implicit def isNotEqSeqCoevalProp[A](list: List[IsNotEquiv[Coeval[A]]]): Prop =
    Prop { list.forall(isNotEq => isNotEq.lh.runTry != isNotEq.rh.runTry ) }

  /** Syntax for equivalence in tests. */
  implicit final class IsEqArrow[A](val lhs: A) {
    def ===(rhs: A): IsEquiv[A] = IsEquiv(lhs, rhs)
  }

  /** Syntax for negating equivalence in tests. */
  implicit final class IsNotEqArrow[A](val lhs: A) {
    def =!=(rhs: A): IsNotEquiv[A] = IsNotEquiv(lhs, rhs)
  }
}
