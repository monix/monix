/*
 * Copyright (c) 2014-2022 Monix Contributors.
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
import monix.tail.Iterant.{ Concat, Halt, Last, Next, NextBatch, NextCursor, Scope, Suspend }
import monix.tail.batches.BatchCursor
import scala.util.control.NonFatal

private[tail] object IterantConcat {
  /**
    * Implementation for `Iterant#flatMap`
    */
  def flatMap[F[_], A, B](source: Iterant[F, A], f: A => Iterant[F, B])(implicit F: Sync[F]): Iterant[F, B] = {

    source match {
      case Halt(_) =>
        // Fast-path
        source.asInstanceOf[Iterant[F, B]]
      case Suspend(rest) =>
        // Fast-path
        Suspend(rest.map(new UnsafeFlatMapLoop[F, A, B](f)))
      case _ =>
        // Suspending execution in order to preserve laziness and
        // referential transparency, since the provided function can
        // be side effecting and because processing NextBatch and
        // NextCursor states can have side effects
        Suspend(F.delay(new UnsafeFlatMapLoop(f).apply(source)))
    }
  }

  /**
    * Implementation for `Iterant#unsafeFlatMap`
    */
  def unsafeFlatMap[F[_], A, B](source: Iterant[F, A])(f: A => Iterant[F, B])(implicit F: Sync[F]): Iterant[F, B] = {

    source match {
      case Last(item) =>
        try f(item)
        catch { case e if NonFatal(e) => Iterant.raiseError(e) }
      case empty @ Halt(_) =>
        empty.asInstanceOf[Iterant[F, B]]
      case _ =>
        new UnsafeFlatMapLoop(f).apply(source)
    }
  }

  private final class UnsafeFlatMapLoop[F[_], A, B](f: A => Iterant[F, B])(implicit F: Sync[F])
    extends Iterant.Visitor[F, A, Iterant[F, B]] {
    loop =>

    def visit(ref: Next[F, A]): Iterant[F, B] =
      generate(ref.item, ref.rest.map(loop))
    def visit(ref: NextCursor[F, A]): Iterant[F, B] =
      evalNextCursor(ref, ref.cursor, ref.rest)

    def visit(ref: NextBatch[F, A]): Iterant[F, B] = {
      val cursor = ref.batch.cursor()
      val rest = ref.rest
      val nextRef = NextCursor(cursor, rest)
      evalNextCursor(nextRef, cursor, rest)
    }

    def visit(ref: Suspend[F, A]): Iterant[F, B] =
      Suspend(ref.rest.map(loop))
    def visit(ref: Concat[F, A]): Iterant[F, B] =
      ref.runMap(loop)
    def visit[S](ref: Scope[F, S, A]): Iterant[F, B] =
      ref.runMap(loop)
    def visit(ref: Last[F, A]): Iterant[F, B] =
      f(ref.item)
    def visit(ref: Halt[F, A]): Iterant[F, B] =
      ref.asInstanceOf[Iterant[F, B]]
    def fail(e: Throwable): Iterant[F, B] =
      Iterant.raiseError(e)

    private def generate(item: A, rest: F[Iterant[F, B]]): Iterant[F, B] =
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

    private def evalNextCursor(ref: NextCursor[F, A], cursor: BatchCursor[A], rest: F[Iterant[F, A]]) = {
      if (!cursor.hasNext()) {
        Suspend(rest.map(loop))
      } else {
        val item = cursor.next()
        // If iterator is empty then we can skip a beat
        val tail =
          if (cursor.hasNext()) F.pure(ref).map(loop)
          else rest.map(loop)

        generate(item, tail)
      }
    }
  }

  /**
    * Implementation for `Iterant#++`
    */
  def concat[F[_], A](lhs: Iterant[F, A], rhs: F[Iterant[F, A]])(implicit F: Sync[F]): Iterant[F, A] = {

    lhs match {
      case Last(item) =>
        Next(item, rhs)
      case Halt(e) =>
        e match {
          case None => Suspend(rhs)
          case _ => lhs
        }
      case _ =>
        Concat(F.pure(lhs), rhs)
    }
  }

  /**
    * Implementation for `Iterant.tailRecM`
    */
  def tailRecM[F[_], A, B](a: A)(f: A => Iterant[F, Either[A, B]])(implicit F: Sync[F]): Iterant[F, B] = {

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
