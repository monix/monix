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
import monix.tail.cursors.Generator
import monix.types.tests.Eq
import org.scalacheck.Arbitrary

import scala.util.{Failure, Success, Try}
import scala.concurrent.duration._

trait ArbitraryInstances extends monix.eval.ArbitraryInstances {
  def arbitraryListToLazyStream[A](list: List[A], idx: Int): LazyStream[A] = {
    def loop(list: List[A], idx: Int): LazyStream[A] =
      list match {
        case Nil =>
          LazyStream.haltS(None)
        case x :: Nil if idx % 2 == 1 =>
          LazyStream.lastS(x)
        case ns =>
          if (idx % 6 == 0)
            LazyStream.nextS(ns.head, Coeval(loop(ns.tail, idx+1)), Coeval.unit)
          else if (idx % 6 == 1)
            LazyStream.suspend(Coeval(loop(list, idx+1)))
          else  if (idx % 6 == 2) {
            val (headSeq, tail) = list.splitAt(3)
            LazyStream.nextSeqS(Cursor.fromIndexedSeq(headSeq.toVector), Coeval(loop(tail, idx+1)), Coeval.unit)
          }
          else if (idx % 6 == 3) {
            LazyStream.suspendS(Coeval(loop(ns, idx + 1)), Coeval.unit)
          }
          else if (idx % 6 == 4) {
            val (headSeq, tail) = list.splitAt(3)
            LazyStream.nextGenS(Generator.fromIndexedSeq(headSeq.toVector), Coeval(loop(tail, idx+1)), Coeval.unit)
          }
          else {
            LazyStream.nextGenS(Generator.empty, Coeval(loop(ns, idx + 1)), Coeval.unit)
          }
      }

    LazyStream.suspend(loop(list, idx))
  }

  implicit def arbitraryLazyStream[A](implicit A: Arbitrary[A]): Arbitrary[LazyStream[A]] =
    Arbitrary {
      val listGen = implicitly[Arbitrary[List[A]]]
      val intGen = implicitly[Arbitrary[Int]]
      for (source <- listGen.arbitrary; i <- intGen.arbitrary) yield
        arbitraryListToLazyStream(source.reverse, math.abs(i % 4))
    }

  def arbitraryListToAsyncStream[A](list: List[A], idx: Int): AsyncStream[A] = {
    def loop(list: List[A], idx: Int): AsyncStream[A] =
      list match {
        case Nil =>
          AsyncStream.haltS(None)
        case x :: Nil if idx % 2 == 1 =>
          AsyncStream.lastS(x)
        case ns =>
          if (idx % 6 == 0)
            AsyncStream.nextS(ns.head, Task.eval(loop(ns.tail, idx+1)), Task.unit)
          else if (idx % 6 == 1)
            AsyncStream.suspend(Task.eval(loop(list, idx+1)))
          else  if (idx % 6 == 2) {
            val (headSeq, tail) = list.splitAt(3)
            AsyncStream.nextSeqS(Cursor.fromIndexedSeq(headSeq.toVector), Task.eval(loop(tail, idx+1)), Task.unit)
          }
          else if (idx % 6 == 3) {
            AsyncStream.suspendS(Task.eval(loop(ns, idx + 1)), Task.unit)
          }
          else if (idx % 6 == 4) {
            val (headSeq, tail) = list.splitAt(3)
            AsyncStream.nextGenS(Generator.fromIndexedSeq(headSeq.toVector), Task.eval(loop(tail, idx+1)), Task.unit)
          }
          else {
            AsyncStream.nextGenS(Generator.empty, Task.eval(loop(ns, idx + 1)), Task.unit)
          }
      }
    
    AsyncStream.suspend(loop(list, idx))
  }

  implicit def arbitraryAsyncStream[A](implicit A: Arbitrary[A]): Arbitrary[AsyncStream[A]] =
    Arbitrary {
      val listGen = implicitly[Arbitrary[List[A]]]
      val intGen = implicitly[Arbitrary[Int]]
      for (source <- listGen.arbitrary; i <- intGen.arbitrary) yield
        arbitraryListToAsyncStream(source.reverse, math.abs(i % 4))
    }

  implicit def isEqLazyStream[A](implicit A: Eq[List[A]]): Eq[LazyStream[A]] =
    new Eq[LazyStream[A]] {
      def apply(lh: LazyStream[ A], rh: LazyStream[ A]): Boolean = {
        val valueA = lh.toListL.runTry
        val valueB = rh.toListL.runTry

        (valueA.isFailure && valueB.isFailure) || {
          val la = valueA.get
          val lb = valueB.get
          A(la, lb)
        }
      }
    }

  implicit def isEqAsyncStream[A](implicit A: Eq[List[A]]): Eq[AsyncStream[A]] =
    new Eq[AsyncStream[A]] {
      def apply(lh: AsyncStream[ A], rh: AsyncStream[ A]): Boolean = {
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
