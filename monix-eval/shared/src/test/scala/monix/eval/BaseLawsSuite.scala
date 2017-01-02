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
import monix.execution.internal.Platform
import monix.execution.schedulers.TestScheduler
import monix.types.tests.Eq
import org.scalacheck.Arbitrary
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
      val intGen = implicitly[Arbitrary[Int]]
      for (a <- A.arbitrary; int <- intGen.arbitrary) yield {
        if (int % 3 == 0) Coeval.now(a)
        else if (int % 3 == 1) Coeval.evalOnce(a)
        else Coeval.eval(a)
      }
    }

  implicit def arbitraryTask[A](implicit A: Arbitrary[A]): Arbitrary[Task[A]] =
    Arbitrary {
      val intGen = implicitly[Arbitrary[Int]]
      for (a <- A.arbitrary; int <- intGen.arbitrary) yield {
        if (int % 4 == 0) Task.now(a)
        else if (int % 4 == 1) Task.evalOnce(a)
        else if (int % 4 == 2) Task.eval(a)
        else Task.create[A] { (_,cb) => cb.onSuccess(a); Cancelable.empty }
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

  implicit def arbitraryCoevalToLong[A,B](implicit A: Arbitrary[A], B: Arbitrary[B]): Arbitrary[Coeval[A] => B] =
    Arbitrary {
      for (b <- B.arbitrary) yield (_: Coeval[A]) => b
    }

  implicit def arbitraryTaskToLong[A,B](implicit A: Arbitrary[A], B: Arbitrary[B]): Arbitrary[Task[A] => B] =
    Arbitrary {
      for (b <- B.arbitrary) yield (_: Task[A]) => b
    }

  implicit def arbitraryStream[A](implicit A: Arbitrary[A]): Arbitrary[Streamable[Task,A]] = {
    def listToStream(list: List[A], length: Int, idx: Int): Streamable[Task, A] =
      list match {
        case Nil =>
          Streamable.halt(None)
        case ns =>
          if (idx % 3 == 0)
            Streamable.next[Task,A](ns.head, Task(listToStream(ns.tail, length, idx+1)))
          else if (idx % 3 == 1)
            Streamable.suspend[Task,A](Task(listToStream(list, length, idx+1)))
          else {
            val (headSeq, tail) = list.splitAt(4)
            Streamable.nextSeq[Task,A](headSeq, Task(listToStream(tail, length, idx+1)))
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
            CoevalStream.nextSeq(headSeq, Coeval(listToStream(tail, length, idx+1)))
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
            TaskStream.nextSeq(headSeq, Task(listToStream(tail, length, idx+1)))
          }
      }

    Arbitrary {
      val listGen = implicitly[Arbitrary[List[A]]]
      val intGen = implicitly[Arbitrary[Int]]
      for (source <- listGen.arbitrary; i <- intGen.arbitrary) yield
        listToStream(source.reverse, source.length, math.abs(i % 4))
    }
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

  implicit def isEqStream[A](implicit A: Eq[List[A]]): Eq[Streamable[Task,A]] =
    new Eq[Streamable[Task,A]] {
      def apply(lh: Streamable[Task, A], rh: Streamable[Task, A]): Boolean = {
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
