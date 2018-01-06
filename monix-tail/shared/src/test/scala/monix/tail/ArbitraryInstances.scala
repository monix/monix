/*
 * Copyright (c) 2014-2018 by The Monix Project Developers.
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

import cats.Eq
import cats.effect.{IO, Sync}
import monix.eval.{Coeval, Task}
import monix.execution.exceptions.DummyException
import monix.execution.schedulers.TestScheduler
import monix.tail.batches.{Batch, BatchCursor}
import org.scalacheck.{Arbitrary, Cogen}

trait ArbitraryInstances extends monix.eval.ArbitraryInstances {
  def arbitraryListToIterant[F[_], A](list: List[A], idx: Int, allowErrors: Boolean = true)
    (implicit F: Sync[F]): Iterant[F, A] = {

    def loop(list: List[A], idx: Int): Iterant[F, A] =
      list match {
        case Nil =>
          if (!allowErrors || math.abs(idx % 4) != 0)
            Iterant[F].haltS(None)
          else
            Iterant[F].haltS(Some(DummyException("arbitrary")))

        case x :: Nil if math.abs(idx % 2) == 1 =>
          Iterant[F].lastS(x)

        case ns =>
          math.abs(idx % 14) match {
            case 0 | 1 =>
              Iterant[F].nextS(ns.head, F.delay(loop(ns.tail, idx+1)), F.unit)
            case 2 | 3 =>
              Iterant[F].suspend(F.delay(loop(list, idx+1)))
            case 4 | 5 =>
              Iterant[F].suspendS(F.delay(loop(ns, idx + 1)), F.unit)
            case n @ (6 | 7 | 8) =>
              val (headSeq, tail) = list.splitAt(3)
              val cursor = BatchCursor.fromIterator(headSeq.toVector.iterator, n - 5)
              Iterant[F].nextCursorS(cursor, F.delay(loop(tail, idx+1)), F.unit)
            case n @ (9 | 10 | 11) =>
              val (headSeq, tail) = list.splitAt(3)
              val batch = Batch.fromSeq(headSeq.toVector, n - 8)
              Iterant[F].nextBatchS(batch, F.delay(loop(tail, idx + 1)), F.unit)
            case 12 | 13 =>
              Iterant[F].nextBatchS(Batch.empty, F.delay(loop(ns, idx + 1)), F.unit)
          }
      }

    Iterant[F].suspend(loop(list, idx))
  }

  implicit def arbitraryIterantCoeval[A](implicit A: Arbitrary[A]): Arbitrary[Iterant[Coeval, A]] =
    Arbitrary {
      val listGen = implicitly[Arbitrary[List[A]]]
      val intGen = implicitly[Arbitrary[Int]]
      for (source <- listGen.arbitrary; i <- intGen.arbitrary) yield
        arbitraryListToIterant[Coeval, A](source.reverse, math.abs(i))
    }

  implicit def arbitraryIterantTask[A](implicit A: Arbitrary[A]): Arbitrary[Iterant[Task, A]] =
    Arbitrary {
      val listGen = implicitly[Arbitrary[List[A]]]
      val intGen = implicitly[Arbitrary[Int]]
      for (source <- listGen.arbitrary; i <- intGen.arbitrary) yield
        arbitraryListToIterant[Task, A](source.reverse, math.abs(i))
    }

  implicit def arbitraryIterantIO[A](implicit A: Arbitrary[A]): Arbitrary[Iterant[IO, A]] =
    Arbitrary {
      val listGen = implicitly[Arbitrary[List[A]]]
      val intGen = implicitly[Arbitrary[Int]]
      for (source <- listGen.arbitrary; i <- intGen.arbitrary) yield
        arbitraryListToIterant[IO, A](source.reverse, math.abs(i))
    }

  implicit def isEqIterantCoeval[A](implicit A: Eq[List[A]]): Eq[Iterant[Coeval, A]] =
    new Eq[Iterant[Coeval, A]] {
      def eqv(lh: Iterant[Coeval,  A], rh: Iterant[Coeval,  A]): Boolean = {
        val valueA = lh.toListL.runTry
        val valueB = rh.toListL.runTry

        (valueA.isFailure && valueB.isFailure) || {
          val la = valueA.get
          val lb = valueB.get
          A.eqv(la, lb)
        }
      }
    }

  implicit def isEqIterantTask[A](implicit A: Eq[List[A]]): Eq[Iterant[Task, A]] =
    new Eq[Iterant[Task, A]] {
      def eqv(lh: Iterant[Task,  A], rh: Iterant[Task,  A]): Boolean = {
        implicit val s = TestScheduler()
        equalityTask[List[A]].eqv(lh.toListL, rh.toListL)
      }
    }

  implicit def isEqIterantIO[A](implicit A: Eq[List[A]]): Eq[Iterant[IO, A]] =
    new Eq[Iterant[IO, A]] {
      def eqv(lh: Iterant[IO,  A], rh: Iterant[IO,  A]): Boolean = {
        implicit val s = TestScheduler()
        equalityIO[List[A]].eqv(lh.toListL, rh.toListL)
      }
    }

  implicit def cogenForIterant[F[_], A]: Cogen[Iterant[F, A]] =
    Cogen[Unit].contramap(_ => ())
}
