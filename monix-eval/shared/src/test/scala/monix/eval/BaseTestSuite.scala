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

package monix.eval

import minitest.TestSuite
import minitest.laws.Checkers
import monix.execution.Cancelable
import monix.execution.schedulers.TestScheduler
import org.scalacheck.Prop.{False, Proof, True}
import org.scalacheck.{Arbitrary, Prop}
import scala.concurrent.duration._
import scala.language.implicitConversions
import scala.util.{Failure, Success, Try}

abstract class BaseTestSuite extends TestSuite[TestScheduler] with Checkers {
  def setup(): TestScheduler = TestScheduler()
  def tearDown(env: TestScheduler): Unit = {
    assert(env.state.get.tasks.isEmpty, "should not have tasks left to execute")
  }

  def arbitraryTaskNow[A](implicit A: Arbitrary[A]): Arbitrary[Task[A]] =
    Arbitrary { A.arbitrary.map(a => Task.now(a)) }

  def arbitraryCoevalNow[A](implicit A: Arbitrary[A]): Arbitrary[Coeval[A]] =
    Arbitrary { A.arbitrary.map(a => Coeval.now(a)) }

  def arbitraryTaskEvalOnce[A](implicit A: Arbitrary[A]): Arbitrary[Task[A]] =
    Arbitrary { A.arbitrary.map(a => Task.evalOnce(a)) }

  def arbitraryCoevalEvalOnce[A](implicit A: Arbitrary[A]): Arbitrary[Coeval[A]] =
    Arbitrary { A.arbitrary.map(a => Coeval.evalOnce(a)) }

  def arbitraryEvalAlways[A](implicit A: Arbitrary[A]): Arbitrary[Task[A]] =
    Arbitrary { A.arbitrary.map(a => Task.evalAlways(a)) }

  def arbitraryError[A](implicit ev: Arbitrary[Int]): Arbitrary[Task[A]] =
    Arbitrary { ev.arbitrary.map(nr => Task.error(DummyException(s"dummy $nr"))) }

  def arbitraryAsync[A](implicit A: Arbitrary[A]): Arbitrary[Task[A]] =
    Arbitrary(A.arbitrary.map(a =>
      Task.create { (s,cb) =>
        cb.onSuccess(a)
        Cancelable.empty
      }
    ))

  def arbitraryAsyncError[A](implicit ev: Arbitrary[Int]): Arbitrary[Task[A]] =
    Arbitrary(ev.arbitrary.map(nr =>
      Task.create { (s,cb) =>
        cb.onError(DummyException(s"dummy $nr"))
        Cancelable.empty
      }
    ))

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

      // simulate synchronous execution
      s.tick(1.hour)
      valueA == valueB
    }

  /** Implicitly map [[IsNotEquiv]] to a [[Prop]]. */
  implicit def isNotEqTaskProp[A](isNotEq: IsNotEquiv[Task[A]])(implicit s: TestScheduler): Prop =
    for (r <- isEqTaskProp(IsEquiv(isNotEq.lh, isNotEq.rh))) yield
      r.status match {
        case False => r.copy(status = True)
        case True | Proof => r.copy(status = False)
        case _ => r
      }

  /** Implicitly map [[IsEquiv]] to a [[Prop]]. */
  implicit def isEqSeqTaskProp[A](list: List[IsEquiv[Task[A]]])(implicit s: TestScheduler): Prop =
    list match {
      case Nil => Prop(true)
      case head :: tail =>
        isEqTaskProp(head)(s).flatMap { result =>
          if (result.success && tail.nonEmpty)
            isEqSeqTaskProp(tail)(s)
          else
            Prop(result)
        }
    }

  /** Implicitly map [[IsNotEquiv]] to a [[Prop]]. */
  implicit def isNotEqSeqTaskProp[A](list: List[IsNotEquiv[Task[A]]])(implicit s: TestScheduler): Prop =
    list match {
      case Nil => Prop(true)
      case head :: tail =>
        isNotEqTaskProp(head)(s).flatMap { result =>
          if (result.success && tail.nonEmpty)
            isNotEqSeqTaskProp(tail)(s)
          else
            Prop(result)
        }
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
}



/** For generating dummy exceptions. */
case class DummyException(message: String) extends RuntimeException(message)

/** For expressing equivalence. */
final case class IsEquiv[A](lh: A, rh: A)

/** For negating equivalence. */
final case class IsNotEquiv[A](lh: A, rh: A)
