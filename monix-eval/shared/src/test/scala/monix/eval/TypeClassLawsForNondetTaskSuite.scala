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

import monix.execution.Cancelable
import monix.execution.schedulers.TestScheduler
import monix.types.tests._
import org.scalacheck.Arbitrary
import scala.util.{Failure, Success, Try}
import concurrent.duration._

object TypeClassLawsForNondetTaskSuite extends MemoizableLawsSuite[Task,Int,Long,Short]
  with SuspendableLawsSuite[Task,Int,Long,Short]
  with MonadErrorLawsSuite[Task,Int,Long,Short,Throwable]
  with CobindLawsSuite[Task,Int,Long,Short]
  with MonadRecLawsSuite[Task,Int,Long,Short] {

  override def F: Task.TypeClassInstances = Task.nondeterminism

  implicit def arbitraryTask[A](implicit A: Arbitrary[A]): Arbitrary[Task[A]] =
    Arbitrary {
      val intGen = implicitly[Arbitrary[Int]]
      for (a <- A.arbitrary; int <- intGen.arbitrary) yield {
        if (int % 4 == 0) Task.now(a)
        else if (int % 4 == 1) Task.evalOnce(a)
        else if (int % 4 == 2) Task.eval(a)
        else Task.create[A] { (s,cb) => cb.onSuccess(a); Cancelable.empty }
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

  implicit def arbitraryTaskToLong[A,B](implicit A: Arbitrary[A], B: Arbitrary[B]): Arbitrary[Task[A] => B] =
    Arbitrary {
      for (b <- B.arbitrary) yield (t: Task[A]) => b
    }

  monadEvalErrorCheck("Task.nondeterminism")
  memoizableCheck("Task.nondeterminism", includeSupertypes = true)
  monadErrorCheck("Task.nondeterminism", includeSupertypes = false)
  monadRecCheck("Task.nondeterminism", includeSupertypes = false)
  cobindCheck("Task.nondeterminism", includeSupertypes = false)
}
