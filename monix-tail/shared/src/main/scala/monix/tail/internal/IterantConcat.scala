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

import cats.effect.Sync
import cats.syntax.all._
import monix.tail.Iterant
import monix.tail.Iterant.{Concat, Halt, Last, Next, NextBatch, NextCursor, Scope, Suspend}
import monix.tail.batches.BatchCursor
import scala.util.control.NonFatal

private[tail] object IterantConcat {
  /**
    * Implementation for `Iterant#flatMap`
    */
  def flatMap[F[_], A, B](source: Iterant[F, A], f: A => Iterant[F, B])
    (implicit F: Sync[F]): Iterant[F, B] = {

    source match {
      case Suspend(_) | Halt(_) =>
        // Fast-path
        unsafeFlatMap(source)(f)
      case _ =>
        // Suspending execution in order to preserve laziness and
        // referential transparency, since the provided function can
        // be side effecting and because processing NextBatch and
        // NextCursor states can have side effects
        Suspend(F.delay(unsafeFlatMap(source)(f)))
    }
  }

  /**
    * Implementation for `Iterant#unsafeFlatMap`
    */
  def unsafeFlatMap[F[_], A, B](source: Iterant[F, A])(f: A => Iterant[F, B])
    (implicit F: Sync[F]): Iterant[F, B] = {

    def generate(item: A, rest: F[Iterant[F, B]]): Iterant[F, B] =
      f(item) match {
        case Last(value) =>
          Next(value, rest)
        case h @ Halt(e) =>
          e match {
            case None => Suspend(rest)
            case _ => h.asInstanceOf[Iterant[F, B]]
          }
        case next =>
          concat(next, rest)
      }

    def evalNextCursor(ref: NextCursor[F, A], cursor: BatchCursor[A], rest: F[Iterant[F, A]]) = {
      if (!cursor.hasNext) {
        Suspend(rest.map(unsafeFlatMap(_)(f)))
      }
      else {
        val item = cursor.next()
        // If iterator is empty then we can skip a beat
        val tail =
          if (cursor.hasNext()) F.delay(flatMap(ref, f))
          else rest.map(unsafeFlatMap(_)(f))

        generate(item, tail)
      }
    }

    try source match {
      case Next(item, rest) =>
        generate(item, rest.map(unsafeFlatMap(_)(f)))

      case ref @ NextCursor(cursor, rest) =>
        evalNextCursor(ref, cursor, rest)

      case Suspend(rest) =>
        Suspend(rest.map(unsafeFlatMap(_)(f)))

      case NextBatch(gen, rest) =>
        val cursor = gen.cursor()
        val ref = NextCursor(cursor, rest)
        evalNextCursor(ref, cursor, rest)

      case Last(item) =>
        f(item)

      case empty @ Halt(_) =>
        empty.asInstanceOf[Iterant[F, B]]

      case Scope(open, use, close) =>
        Scope(open, use.map(unsafeFlatMap(_)(f)), close)

      case Concat(lh, rh) =>
        Concat(lh.map(unsafeFlatMap(_)(f)), rh.map(unsafeFlatMap(_)(f)))

    } catch {
      case ex if NonFatal(ex) =>
        Iterant.raiseError(ex)
    }
  }

  /**
    * Implementation for `Iterant#++`
    */
  def concat[F[_], A](lhs: Iterant[F, A], rhs: F[Iterant[F, A]])
    (implicit F: Sync[F]): Iterant[F, A] = {

    lhs match {
      case Next(a, lt) =>
        Next(a, lt.map(concat(_, rhs)))
      case NextCursor(seq, lt) =>
        NextCursor(seq, lt.map(concat(_, rhs)))
      case NextBatch(gen, rest) =>
        NextBatch(gen, rest.map(concat(_, rhs)))
      case Suspend(lt) =>
        Suspend(lt.map(concat(_, rhs)))
      case Last(item) =>
        Next(item, rhs)
      case Halt(None) =>
        Suspend(rhs)
      case error @ Halt(Some(_)) =>
        error
      case _ =>
        Concat(F.pure(lhs), rhs)
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
          Suspend(F.delay(loop(nextA)))
      }

    // Function `f` may be side-effecting, or it might trigger
    // side-effects, so we must suspend it
    Suspend(F.delay(loop(a)))
  }
}
