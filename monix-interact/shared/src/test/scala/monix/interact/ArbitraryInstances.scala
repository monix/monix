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

package monix.interact

import monix.eval.{Callback, Coeval, Task}
import monix.execution.schedulers.TestScheduler
import monix.types.tests.Eq
import org.scalacheck.Arbitrary
import scala.util.{Failure, Success, Try}
import scala.concurrent.duration._

trait ArbitraryInstances extends monix.eval.ArbitraryInstances {
  implicit def arbitraryStream[A](implicit A: Arbitrary[A]): Arbitrary[Iterant[Task,A]] = {
    def loop(list: List[A], length: Int, idx: Int): Iterant[Task, A] =
      list match {
        case Nil =>
          Iterant.haltS(None)
        case ns =>
          if (idx % 4 == 0)
            Iterant.nextS[Task,A](ns.head, Task(loop(ns.tail, length, idx+1)), Task.unit)
          else if (idx % 4 == 1)
            Iterant.suspend[Task,A](Task(loop(list, length, idx+1)))
          else if (idx % 4 == 2) {
            val (headSeq, tail) = list.splitAt(4)
            Iterant.nextSeqS[Task,A](Cursor.fromSeq(headSeq), Task(loop(tail, length, idx+1)), Task.unit)
          }
          else {
            val (headSeq, tail) = list.splitAt(4)
            Iterant.nextSeqS[Task,A](Cursor.fromIndexedSeq(headSeq.toVector), Task(loop(tail, length, idx+1)), Task.unit)
          }
      }

    Arbitrary {
      val listGen = implicitly[Arbitrary[List[A]]]
      val intGen = implicitly[Arbitrary[Int]]
      for (source <- listGen.arbitrary; i <- intGen.arbitrary) yield
        Iterant.suspend(loop(source.reverse, source.length, math.abs(i % 4)))
    }
  }

  def arbitraryListToCoevalStream[A](list: List[A], idx: Int): CoevalStream[A] = {
    def loop(list: List[A], idx: Int): CoevalStream[A] =
      list match {
        case Nil =>
          CoevalStream.haltS(None)
        case ns =>
          if (idx % 4 == 0)
            CoevalStream.nextS(ns.head, Coeval(loop(ns.tail, idx+1)), Coeval.unit)
          else if (idx % 4 == 1)
            CoevalStream.suspend(Coeval(loop(list, idx+1)))
          else  if (idx % 4 == 2) {
            val (headSeq, tail) = list.splitAt(4)
            CoevalStream.nextSeqS(Cursor.fromIndexedSeq(headSeq.toVector), Coeval(loop(tail, idx+1)), Coeval.unit)
          }
          else {
            val (headSeq, tail) = list.splitAt(4)
            CoevalStream.nextSeqS(Cursor.fromSeq(headSeq), Coeval(loop(tail, idx+1)), Coeval.unit)
          }
      }

    CoevalStream.suspend(loop(list, idx))
  }

  implicit def arbitraryCoevalStream[A](implicit A: Arbitrary[A]): Arbitrary[CoevalStream[A]] =
    Arbitrary {
      val listGen = implicitly[Arbitrary[List[A]]]
      val intGen = implicitly[Arbitrary[Int]]
      for (source <- listGen.arbitrary; i <- intGen.arbitrary) yield
        arbitraryListToCoevalStream(source.reverse, math.abs(i % 4))
    }

  def arbitraryListToTaskStream[A](list: List[A], idx: Int): TaskStream[A] = {
    def loop(list: List[A], idx: Int): TaskStream[A] =
      list match {
        case Nil =>
          TaskStream.haltS(None)
        case ns =>
          if (idx % 4 == 0)
            TaskStream.nextS(ns.head, Task(loop(ns.tail, idx+1)), Task.unit)
          else if (idx % 4 == 1)
            TaskStream.suspend(Task(loop(list, idx+1)))
          else if (idx % 4 == 2) {
            val (headSeq, tail) = list.splitAt(4)
            TaskStream.nextSeqS(Cursor.fromIndexedSeq(headSeq.toVector), Task(loop(tail, idx+1)), Task.unit)
          }
          else {
            val (headSeq, tail) = list.splitAt(4)
            TaskStream.nextSeqS(Cursor.fromSeq(headSeq), Task(loop(tail, idx+1)), Task.unit)
          }
      }

    TaskStream.suspend(loop(list, idx))
  }

  implicit def arbitraryTaskStream[A](implicit A: Arbitrary[A]): Arbitrary[TaskStream[A]] =
    Arbitrary {
      val listGen = implicitly[Arbitrary[List[A]]]
      val intGen = implicitly[Arbitrary[Int]]
      for (source <- listGen.arbitrary; i <- intGen.arbitrary) yield
        arbitraryListToTaskStream(source.reverse, math.abs(i % 4))
    }

  implicit def isEqStream[A](implicit A: Eq[List[A]]): Eq[Iterant[Task,A]] =
    new Eq[Iterant[Task,A]] {
      def apply(lh: Iterant[Task, A], rh: Iterant[Task, A]): Boolean = {
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

  implicit def isEqCoevalStream[A](implicit A: Eq[List[A]]): Eq[CoevalStream[A]] =
    new Eq[CoevalStream[A]] {
      def apply(lh: CoevalStream[ A], rh: CoevalStream[ A]): Boolean = {
        val valueA = lh.toListL.runTry
        val valueB = rh.toListL.runTry

        (valueA.isFailure && valueB.isFailure) || {
          val la = valueA.get
          val lb = valueB.get
          A(la, lb)
        }
      }
    }

  implicit def isEqTaskStream[A](implicit A: Eq[List[A]]): Eq[TaskStream[A]] =
    new Eq[TaskStream[A]] {
      def apply(lh: TaskStream[ A], rh: TaskStream[ A]): Boolean = {
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
