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

package monix.reactive

import minitest.TestSuite
import minitest.laws.Checkers
import monix.eval.{Callback, Coeval, Task}
import monix.execution.Scheduler
import org.scalacheck.{Arbitrary, Prop}
import scala.concurrent.duration._
import scala.concurrent.{Await, Promise}
import scala.language.implicitConversions
import scala.util.Try

trait BaseConcurrencySuite extends TestSuite[Scheduler] with Checkers {
  def setup(): Scheduler = Scheduler.global
  def tearDown(env: Scheduler): Unit = ()

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
  implicit def isEqObservableProp[A](isEq: IsEquiv[Observable[A]])(implicit s: Scheduler): Prop =
    Prop {
      val promiseLeft = Promise[Option[List[A]]]()
      val promiseRight = Promise[Option[List[A]]]()

      isEq.lh.foldLeftF(List.empty[A])((acc,e) => e :: acc).firstOptionL
        .runAsync(Callback.fromPromise(promiseLeft))
      isEq.rh.foldLeftF(List.empty[A])((acc,e) => e :: acc).firstOptionL
        .runAsync(Callback.fromPromise(promiseRight))

      val valueLeft = Await.result(promiseLeft.future, 5.minutes)
      val valueRight = Await.result(promiseRight.future, 5.minutes)
      valueLeft == valueRight
    }

  /** Implicitly map [[IsEquiv]] to a [[Prop]]. */
  implicit def isEqTaskProp[A](isEq: IsEquiv[Task[A]])(implicit s: Scheduler): Prop =
    Prop {
      val promiseLeft = Promise[Try[A]]()
      val promiseRight = Promise[Try[A]]()

      isEq.lh.materialize.runAsync(Callback.fromPromise(promiseLeft))
      isEq.rh.materialize.runAsync(Callback.fromPromise(promiseRight))

      val valueLeft = Await.result(promiseLeft.future, 5.minutes)
      val valueRight = Await.result(promiseRight.future, 5.minutes)
      valueLeft == valueRight
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
