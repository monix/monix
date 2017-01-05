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
    def listToStream(list: List[A], length: Int, idx: Int): Iterant[Task, A] =
      list match {
        case Nil =>
          Iterant.halt(None)
        case ns =>
          if (idx % 3 == 0)
            Iterant.next[Task,A](ns.head, Task(listToStream(ns.tail, length, idx+1)))
          else if (idx % 3 == 1)
            Iterant.suspend[Task,A](Task(listToStream(list, length, idx+1)))
          else {
            val (headSeq, tail) = list.splitAt(4)
            Iterant.nextSeq[Task,A](Cursor.fromSeq(headSeq), Task(listToStream(tail, length, idx+1)))
          }
      }

    Arbitrary {
      val listGen = implicitly[Arbitrary[List[A]]]
      val intGen = implicitly[Arbitrary[Int]]
      for (source <- listGen.arbitrary; i <- intGen.arbitrary) yield
        listToStream(source.reverse, source.length, math.abs(i % 4))
    }
  }

  implicit def arbitraryCoevalStream[A](implicit A: Arbitrary[A]): Arbitrary[CoevalStream[A]] = {
    def listToStream(list: List[A], length: Int, idx: Int): CoevalStream[A] =
      list match {
        case Nil =>
          CoevalStream.halt(None)
        case ns =>
          if (idx % 3 == 0)
            CoevalStream.next(ns.head, Coeval(listToStream(ns.tail, length, idx+1)))
          else if (idx % 3 == 1)
            CoevalStream.suspend(Coeval(listToStream(list, length, idx+1)))
          else {
            val (headSeq, tail) = list.splitAt(4)
            CoevalStream.nextSeq(Cursor.fromSeq(headSeq), Coeval(listToStream(tail, length, idx+1)))
          }
      }

    Arbitrary {
      val listGen = implicitly[Arbitrary[List[A]]]
      val intGen = implicitly[Arbitrary[Int]]
      for (source <- listGen.arbitrary; i <- intGen.arbitrary) yield
        listToStream(source.reverse, source.length, math.abs(i % 4))
    }
  }

  implicit def arbitraryTaskStream[A](implicit A: Arbitrary[A]): Arbitrary[TaskStream[A]] = {
    def listToStream(list: List[A], length: Int, idx: Int): TaskStream[A] =
      list match {
        case Nil =>
          TaskStream.halt(None)
        case ns =>
          if (idx % 3 == 0)
            TaskStream.next(ns.head, Task(listToStream(ns.tail, length, idx+1)))
          else if (idx % 3 == 1)
            TaskStream.suspend(Task(listToStream(list, length, idx+1)))
          else {
            val (headSeq, tail) = list.splitAt(4)
            TaskStream.nextSeq(Cursor.fromSeq(headSeq), Task(listToStream(tail, length, idx+1)))
          }
      }

    Arbitrary {
      val listGen = implicitly[Arbitrary[List[A]]]
      val intGen = implicitly[Arbitrary[Int]]
      for (source <- listGen.arbitrary; i <- intGen.arbitrary) yield
        listToStream(source.reverse, source.length, math.abs(i % 4))
    }
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
