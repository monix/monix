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
package internal

import cats.effect.Sync
import cats.syntax.all._
import monix.execution.internal.collection.ArrayStack

import scala.util.control.NonFatal
import monix.tail.Iterant.{Concat, Halt, Last, Next, NextBatch, NextCursor, Scope, Suspend}
import monix.tail.batches.BatchCursor

import scala.collection.mutable.ArrayBuffer

private[tail] object IterantScan {
  /** Implementation for `Iterant#scan`. */
  def apply[F[_], A, S](fa: Iterant[F, A], initial: => S, f: (S, A) => S)
    (implicit F: Sync[F]): Iterant[F, S] = {
    // Given that `initial` is a by-name value, we have
    // to suspend
    val task = F.delay {
      try new Loop(initial, f).apply(fa)
      catch { case e if NonFatal(e) => Halt[F, S](Some(e)) }
    }
    Suspend(task)
  }

  class Loop[F[_], A, S](initial: S, f: (S, A) => S)(implicit F: Sync[F])
    extends (Iterant[F, A] => Iterant[F, S])
  {
    private[this] var state = initial
    private[this] val stack = new ArrayStack[F[Iterant[F, A]]]()

    def apply(fa: Iterant[F, A]): Iterant[F, S] = try fa match {
      case Next(a, rest) =>
        state = f(state, a)
        Next(state, rest.map(this))

      case NextCursor(cursor, rest) =>
        processCursor(cursor, rest)

      case NextBatch(batch, rest) =>
        val cursor = batch.cursor()
        processCursor(cursor, rest)

      case Suspend(rest) =>
        Suspend(rest.map(this))

      case s @ Scope(_, _, _) =>
        s.runMap(this)

      case Concat(lh, rh) =>
        stack.push(rh)
        Suspend(lh.map(this))

      case Last(a) =>
        state = f(state, a)
        val next = stack.pop()
        if (next == null) {
          Last(state)
        } else {
          Next(state, next.map(this))
        }

      case halt @ Halt(opt) =>
        val next = stack.pop()
        if (opt.nonEmpty || next == null) {
          halt.asInstanceOf[Iterant[F, S]]
        } else {
          Suspend(next.map(this))
        }
    } catch {
      case e if NonFatal(e) =>
        Iterant.raiseError(e)
    }

    private[this] def processCursor(cursor: BatchCursor[A], rest: F[Iterant[F, A]]) = {
      if (!cursor.hasNext())
        Suspend(rest.map(this))
      else if (cursor.recommendedBatchSize <= 1) {
        state = f(state, cursor.next())
        val next: F[Iterant[F, A]] =
          if (cursor.hasNext()) F.pure(NextCursor(cursor, rest))
          else rest

        Next(state, next.map(this))
      } else {
        val buffer = ArrayBuffer.empty[S]
        var toProcess = cursor.recommendedBatchSize

        while (toProcess > 0 && cursor.hasNext()) {
          state = f(state, cursor.next())
          buffer += state
          toProcess -= 1
        }

        val next: F[Iterant[F, A]] =
          if (cursor.hasNext()) F.pure(NextCursor(cursor, rest))
          else rest

        val elems = BatchCursor.fromArray(buffer.toArray[Any]).asInstanceOf[BatchCursor[S]]
        NextCursor(elems, next.map(this))
      }
    }
  }
}
