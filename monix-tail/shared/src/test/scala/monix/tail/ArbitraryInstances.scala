/*
 * Copyright (c) 2014-2017 by its authors. Some rights reserved.
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

package monix.tail

import monix.eval.{Callback, Coeval, Task}
import monix.execution.schedulers.TestScheduler
import monix.types.tests.Eq
import org.scalacheck.Arbitrary

import scala.util.{Failure, Success, Try}
import scala.concurrent.duration._

trait ArbitraryInstances extends monix.eval.ArbitraryInstances {
  def arbitraryListToIterantCoeval[A](list: List[A], idx: Int): Iterant[Coeval, A] = {
    def loop(list: List[A], idx: Int): Iterant[Coeval, A] =
      list match {
        case Nil =>
          Iterant[Coeval].haltS(None)
        case x :: Nil if idx % 2 == 1 =>
          Iterant[Coeval].lastS(x)
        case ns =>
          if (idx % 6 == 0)
            Iterant[Coeval].nextS(ns.head, Coeval(loop(ns.tail, idx+1)), Coeval.unit)
          else if (idx % 6 == 1)
            Iterant[Coeval].suspend(Coeval(loop(list, idx+1)))
          else  if (idx % 6 == 2) {
            val (headSeq, tail) = list.splitAt(3)
            val bs = if (idx % 7 < 3) 1 else 3
            val cursor = BatchCursor.fromIterator(headSeq.toVector.iterator, bs)
            Iterant[Coeval].nextCursorS(cursor, Coeval(loop(tail, idx+1)), Coeval.unit)
          }
          else if (idx % 6 == 3) {
            Iterant[Coeval].suspendS(Coeval(loop(ns, idx + 1)), Coeval.unit)
          }
          else if (idx % 6 == 4) {
            val (headSeq, tail) = list.splitAt(3)
            val bs = if (idx % 7 < 3) 1 else 3
            val batch = Batch.fromSeq(headSeq.toVector, bs)
            Iterant[Coeval].nextBatchS(batch, Coeval(loop(tail, idx+1)), Coeval.unit)
          }
          else {
            Iterant[Coeval].nextBatchS(Batch.empty, Coeval(loop(ns, idx + 1)), Coeval.unit)
          }
      }

    Iterant[Coeval].suspend(loop(list, idx))
  }

  implicit def arbitraryIterantCoeval[A](implicit A: Arbitrary[A]): Arbitrary[Iterant[Coeval, A]] =
    Arbitrary {
      val listGen = implicitly[Arbitrary[List[A]]]
      val intGen = implicitly[Arbitrary[Int]]
      for (source <- listGen.arbitrary; i <- intGen.arbitrary) yield
        arbitraryListToIterantCoeval(source.reverse, math.abs(i))
    }

  def arbitraryListToIterantTask[A](list: List[A], idx: Int): Iterant[Task, A] = {
    def loop(list: List[A], idx: Int): Iterant[Task, A] =
      list match {
        case Nil =>
          Iterant[Task].haltS(None)
        case x :: Nil if idx % 2 == 1 =>
          Iterant[Task].lastS(x)
        case ns =>
          if (idx % 6 == 0)
            Iterant[Task].nextS(ns.head, Task.eval(loop(ns.tail, idx+1)), Task.unit)
          else if (idx % 6 == 1)
            Iterant[Task].suspend(Task.eval(loop(list, idx+1)))
          else  if (idx % 6 == 2) {
            val (headSeq, tail) = list.splitAt(3)
            val bs = if (idx % 7 < 3) 1 else 3
            val cursor = BatchCursor.fromIterator(headSeq.toVector.iterator, bs)
            Iterant[Task].nextCursorS(cursor, Task.eval(loop(tail, idx+1)), Task.unit)
          }
          else if (idx % 6 == 3) {
            Iterant[Task].suspendS(Task.eval(loop(ns, idx + 1)), Task.unit)
          }
          else if (idx % 6 == 4) {
            val (headSeq, tail) = list.splitAt(3)
            val bs = if (idx % 7 < 3) 1 else 3
            val batch = Batch.fromSeq(headSeq.toVector, bs)
            Iterant[Task].nextBatchS(batch, Task.eval(loop(tail, idx+1)), Task.unit)
          }
          else {
            Iterant[Task].nextBatchS(Batch.empty, Task.eval(loop(ns, idx + 1)), Task.unit)
          }
      }

    Iterant[Task].suspend(loop(list, idx))
  }

  implicit def arbitraryIterantTask[A](implicit A: Arbitrary[A]): Arbitrary[Iterant[Task, A]] =
    Arbitrary {
      val listGen = implicitly[Arbitrary[List[A]]]
      val intGen = implicitly[Arbitrary[Int]]
      for (source <- listGen.arbitrary; i <- intGen.arbitrary) yield
        arbitraryListToIterantTask(source.reverse, math.abs(i))
    }

  implicit def isEqIterantCoeval[A](implicit A: Eq[List[A]]): Eq[Iterant[Coeval, A]] =
    new Eq[Iterant[Coeval, A]] {
      def apply(lh: Iterant[Coeval,  A], rh: Iterant[Coeval,  A]): Boolean = {
        val valueA = lh.toListL.runTry
        val valueB = rh.toListL.runTry

        (valueA.isFailure && valueB.isFailure) || {
          val la = valueA.get
          val lb = valueB.get
          A(la, lb)
        }
      }
    }

  implicit def isEqIterantTask[A](implicit A: Eq[List[A]]): Eq[Iterant[Task, A]] =
    new Eq[Iterant[Task, A]] {
      def apply(lh: Iterant[Task,  A], rh: Iterant[Task,  A]): Boolean = {
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
            A(la, lb)
          }
        }
      }
    }
}
