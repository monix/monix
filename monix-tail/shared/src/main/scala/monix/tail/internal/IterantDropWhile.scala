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
import monix.execution.internal.collection.ArrayStack

import scala.util.control.NonFatal
import monix.tail.Iterant
import monix.tail.Iterant.{Concat, Halt, Last, Next, NextBatch, NextCursor, Scope, Suspend}
import monix.tail.batches.BatchCursor

import scala.annotation.tailrec

private[tail] object IterantDropWhile {
  /**
    * Implementation for `Iterant#dropWhile`
    */
  def apply[F[_], A](source: Iterant[F, A], p: A => Boolean)
    (implicit F: Sync[F]): Iterant[F, A] = {
    Suspend(F.delay(new Loop(p).apply(source)))
  }

  private class Loop[F[_], A](p: A => Boolean)
    (implicit F: Sync[F])
    extends (Iterant[F, A] => Iterant[F, A]) { loop =>
    private[this] var stack: ArrayStack[F[Iterant[F, A]]] = _

    def apply(source: Iterant[F, A]): Iterant[F, A] = {
      try source match {
        case Next(item, rest) =>
          handleNext(item, rest)
        case ref@NextCursor(cursor, rest) =>
          evalCursor(F.pure(ref), cursor, rest, 0)
        case NextBatch(batch, rest) =>
          val cursor = batch.cursor()
          val ref = NextCursor(cursor, rest)
          evalCursor(F.pure(ref), cursor, rest, 0)
        case Suspend(rest) =>
          Suspend(rest.map(loop))
        case Last(elem) =>
          handleLast(elem)
        case halt@Halt(None) =>
          handleHalt(halt)
        case halt@Halt(_) =>
          halt
        case s@Scope(_, _, _) =>
          s.runMap(loop)
        case Concat(lh, rh) =>
          handleConcat(lh, rh)
      } catch {
        case ex if NonFatal(ex) =>
          Halt(Some(ex))
      }
    }

    private def handleNext(elem: A, rest: F[Iterant[F, A]]) = {
      if (p(elem)) Suspend(rest.map(loop))
      else {
        val next =
          if (stack != null) stack.pop()
          else null.asInstanceOf[F[Iterant[F, A]]]

        next match {
          case null => Next(elem, rest)
          case stream => Next(elem, rest.map(_ ++ stream))
        }
      }
    }

    // Reusable logic for NextCursor / NextBatch branches
    @tailrec
    private def evalCursor(ref: F[Iterant[F, A]], cursor: BatchCursor[A], rest: F[Iterant[F, A]], dropped: Int): Iterant[F, A] = {
      if (!cursor.hasNext())
        Suspend(rest.map(loop))
      else if (dropped >= cursor.recommendedBatchSize)
        Suspend(ref.map(loop))
      else {
        val elem = cursor.next()
        if (p(elem))
          evalCursor(ref, cursor, rest, dropped + 1)
        else if (cursor.hasNext()) {
          concatStack(elem, ref)
        }
        else {
          concatStack(elem, rest)
        }
      }
    }

    private def concatStack(elem: A, rest: F[Iterant[F, A]]) = {
      val next =
        if (stack != null) stack.pop()
        else null.asInstanceOf[F[Iterant[F, A]]]

      next match {
        case null => Next(elem, rest)
        case stream => Next(elem, rest.map(_ ++ stream))
      }
    }

    private def handleConcat(lh: F[Iterant[F, A]], rh: F[Iterant[F, A]]): Iterant[F, A] = {
      if (stack == null) stack = new ArrayStack()
      stack.push(rh)
      Suspend(lh.map(loop))
    }

    private def handleLast(elem: A): Iterant[F, A] = {
      val next =
        if (stack != null) stack.pop()
        else null.asInstanceOf[F[Iterant[F, A]]]

      next match {
        case null => if (p(elem)) Iterant.empty else Last(elem)
        case stream => if (p(elem)) Suspend(stream.map(loop)) else Next(elem, stream)
      }
    }

    private def handleHalt(halt: Halt[F, A]): Iterant[F, A] = {
      val next =
        if (stack != null) stack.pop()
        else null.asInstanceOf[F[Iterant[F, A]]]

      next match {
        case null => halt
        case stream => Suspend(stream.map(loop))
      }
    }
  }

}