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

package monix.tail.internal

import cats.Applicative
import cats.effect.Sync
import cats.syntax.all._
import monix.tail.Iterant
import monix.tail.Iterant.{Halt, Last, Next, NextBatch, NextCursor, Suspend}
import monix.tail.batches.BatchCursor
import monix.tail.internal.IterantUtils.signalError
import monix.execution.misc.NonFatal

private[tail] object IterantConcat {
  /**
    * Implementation for `Iterant#flatMap`
    */
  def flatMap[F[_], A, B](source: Iterant[F, A], f: A => Iterant[F, B])
    (implicit F: Sync[F]): Iterant[F, B] = {

    source match {
      case Suspend(_, _) | Halt(_) =>
        // Fast-path
        unsafeFlatMap(source)(f)
      case _ =>
        // Suspending execution in order to preserve laziness and
        // referential transparency, since the provided function can
        // be side effecting and because processing NextBatch and
        // NextCursor states can have side effects
        Suspend(F.delay(unsafeFlatMap(source)(f)), source.earlyStop)
    }
  }

  /**
    * Implementation for `Iterant#unsafeFlatMap`
    */
  def unsafeFlatMap[F[_], A, B](source: Iterant[F, A])(f: A => Iterant[F, B])
    (implicit F: Sync[F]): Iterant[F, B] = {

    def generate(item: A, rest: F[Iterant[F, B]], stop: F[Unit]): Iterant[F, B] =
      f(item) match {
        case next @ (Next(_,_,_) | NextCursor(_,_,_) | NextBatch(_,_,_) | Suspend(_,_)) =>
          concat(next.doOnEarlyStop(stop), Suspend(rest, stop))
        case Last(value) =>
          Next(value, rest, stop)
        case Halt(None) =>
          Suspend(rest, stop)
        case Halt(Some(ex)) =>
          signalError(source, ex)
      }

    def evalNextCursor(ref: NextCursor[F, A], cursor: BatchCursor[A], rest: F[Iterant[F, A]], stop: F[Unit]) = {
      if (!cursor.hasNext) {
        Suspend(rest.map(unsafeFlatMap(_)(f)), stop)
      }
      else {
        val item = cursor.next()
        // If iterator is empty then we can skip a beat
        val tail =
          if (cursor.hasNext()) F.delay(flatMap(ref, f))
          else rest.map(unsafeFlatMap(_)(f))

        generate(item, tail, stop)
      }
    }

    try source match {
      case Next(item, rest, stop) =>
        generate(item, rest.map(unsafeFlatMap(_)(f)), stop)

      case ref @ NextCursor(cursor, rest, stop) =>
        evalNextCursor(ref, cursor, rest, stop)

      case Suspend(rest, stop) =>
        Suspend(rest.map(unsafeFlatMap(_)(f)), stop)

      case NextBatch(gen, rest, stop) =>
        val cursor = gen.cursor()
        val ref = NextCursor(cursor, rest, stop)
        evalNextCursor(ref, cursor, rest, stop)

      case Last(item) =>
        f(item)

      case empty @ Halt(_) =>
        empty.asInstanceOf[Iterant[F, B]]
    } catch {
      case ex if NonFatal(ex) => signalError(source, ex)
    }
  }

  /**
    * Implementation for `Iterant#++`
    */
  def concat[F[_], A](lhs: Iterant[F, A], rhs: Iterant[F, A])
    (implicit F: Applicative[F]): Iterant[F, A] = {

    lhs match {
      case Next(a, lt, stop) =>
        Next(a, lt.map(concat(_, rhs)), stop)
      case NextCursor(seq, lt, stop) =>
        NextCursor(seq, lt.map(concat(_, rhs)), stop)
      case NextBatch(gen, rest, stop) =>
        NextBatch(gen, rest.map(concat(_, rhs)), stop)
      case Suspend(lt, stop) =>
        Suspend(lt.map(concat(_, rhs)), stop)
      case Last(item) =>
        Next(item, F.pure(rhs), rhs.earlyStop)
      case Halt(None) =>
        rhs
      case error @ Halt(Some(_)) =>
        error
    }
  }

  /**
    * Implementation for `Iterant.tailRecM`
    */
  def tailRecM[F[_], A, B](a: A)(f: A => Iterant[F, Either[A, B]])
    (implicit F: Sync[F]): Iterant[F, B] = {

    def loop(a: A): Iterant[F, B] =
      unsafeFlatMap(f(a)) {
        case Right(b) =>
          Last(b)
        case Left(nextA) =>
          Suspend(F.delay(loop(nextA)), F.unit)
      }

    // Function `f` may be side-effecting, or it might trigger
    // side-effects, so we must suspend it
    Suspend(F.delay(loop(a)), F.unit)
  }
}
