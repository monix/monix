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

import monix.eval.Callback
import monix.execution.internal.Platform
import monix.execution.schedulers.TestScheduler
import monix.types.tests._
import org.scalacheck.Arbitrary
import org.scalacheck.Test.Parameters
import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}

object TypeClassLawsForObservableSuite
  extends MemoizableLawsSuite[Observable,Int,Long,Short]
  with SuspendableLawsSuite[Observable,Int,Long,Short]
  with MonadErrorLawsSuite[Observable,Int,Long,Short,Throwable]
  with CobindLawsSuite[Observable,Int,Long,Short]
  with MonadFilterLawsSuite[Observable,Int,Long,Short]
  with MonoidKLawsSuite[Observable,Int]
  with MonadRecLawsSuite[Observable,Int,Long,Short] {

  override lazy val checkConfig: Parameters =
    Parameters.default
      .withMinSuccessfulTests(if (Platform.isJVM) 100 else 10)
      .withMaxDiscardRatio(if (Platform.isJVM) 5.0f else 50.0f)
      .withMaxSize(10)

  override def F: Observable.TypeClassInstances =
    Observable.typeClassInstances

  implicit def arbitraryObservable[A](implicit A: Arbitrary[A]): Arbitrary[Observable[A]] =
    Arbitrary {
      val list = implicitly[Arbitrary[List[A]]]
      for (seq <- list.arbitrary) yield Observable.fromIterable(seq)
    }

  implicit def isEqObservable[A](implicit A: Eq[A]): Eq[Observable[A]] =
    new Eq[Observable[A]] {
      def apply(lh: Observable[A], rh: Observable[A]): Boolean = {
        implicit val s = TestScheduler()
        var valueA = Option.empty[Try[List[A]]]
        var valueB = Option.empty[Try[List[A]]]

        lh.toListL.runAsync(new Callback[List[A]] {
          def onError(ex: Throwable): Unit =
            valueA = Some(Failure(ex))
          def onSuccess(value: List[A]): Unit =
            valueA = Some(Success(value))
        })

        rh.toListL.runAsync(new Callback[List[A]] {
          def onError(ex: Throwable): Unit =
            valueB = Some(Failure(ex))
          def onSuccess(value: List[A]): Unit =
            valueB = Some(Success(value))
        })

        // simulate synchronous execution
        s.tick(1.hour)

        if (valueA.isEmpty)
          valueB.isEmpty
        else {
          (valueA.get.isFailure && valueB.get.isFailure) || {
            val la = valueA.get.get
            val lb = valueB.get.get
            la.length == lb.length && la.zip(lb).forall(x => A(x._1, x._2))
          }
        }
      }
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

  implicit def arbitraryObservableToLong[A,B](implicit A: Arbitrary[A], B: Arbitrary[B]): Arbitrary[Observable[A] => B] =
    Arbitrary {
      for (b <- B.arbitrary) yield (t: Observable[A]) => b
    }

  // Actual tests ...

  monadEvalErrorCheck("Observable")
  memoizableCheck("Observable", includeSupertypes = true)
  monadErrorCheck("Observable", includeSupertypes = false)
  monadFilterCheck("Observable", includeSupertypes = false)
  monadRecCheck("Observable", includeSupertypes = false)
  cobindCheck("Observable", includeSupertypes = false)
  monoidKCheck("Observable", includeSupertypes = true)
}
