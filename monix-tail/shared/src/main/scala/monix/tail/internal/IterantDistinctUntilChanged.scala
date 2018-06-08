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

import cats.Eq
import cats.effect.Sync
import cats.syntax.all._
import scala.util.control.NonFatal

import monix.tail.Iterant
import monix.tail.Iterant.{Concat, Halt, Last, Next, NextBatch, NextCursor, Scope, Suspend}
import monix.tail.batches.BatchCursor
import scala.collection.mutable.ArrayBuffer

import monix.execution.internal.collection.ArrayStack

private[tail] object IterantDistinctUntilChanged {
  /** Implementation for `distinctUntilChangedByKey`. */
  def apply[F[_], A, K](self: Iterant[F, A], f: A => K)
    (implicit F: Sync[F], K: Eq[K]): Iterant[F, A] = {
    Suspend(F.delay(new Loop(f).apply(self)))
  }

  private class Loop[F[_], A, K](f: A => K)(implicit F: Sync[F], K: Eq[K])
    extends (Iterant[F, A] => Iterant[F, A]) {

    private var current: K = null.asInstanceOf[K]
    private val stack = new ArrayStack[F[Iterant[F, A]]]()

    def apply(self: Iterant[F, A]): Iterant[F, A] = {
      try self match {
        case Next(a, rest) =>
          val k = f(a)
          if (current == null || K.neqv(current, k)) {
            current = k
            Next(a, rest.map(this))
          } else {
            Suspend(rest.map(this))
          }

        case node @ NextCursor(_, _) =>
          processCursor(node)
        case NextBatch(ref, rest) =>
          processCursor(NextCursor(ref.cursor(), rest))

        case Suspend(rest) =>
          Suspend(rest.map(this))
        case s @ Scope(_, _, _) =>
          s.runMap(this)
        case Concat(lh, rh) =>
          stack.push(rh)
          Suspend(lh.map(this))

        case Last(a) =>
          val k = f(a)
          val rest = stack.pop()
          if (current == null || K.neqv(current, k)) {
            current = k
            if (rest != null) Next(a, rest.map(this))
            else self
          } else {
            if (rest != null) Suspend(rest.map(this))
            else Iterant.empty
          }
        case Halt(opt) =>
          val next = stack.pop()
          if (opt.nonEmpty || next == null) self
          else Suspend(next.map(this))
      } catch {
        case e if NonFatal(e) =>
          Iterant.raiseError(e)
      }
    }

    def processCursor(self: NextCursor[F, A]): Iterant[F, A] = {
      val NextCursor(cursor, rest) = self

      if (!cursor.hasNext()) {
        Suspend(rest.map(this))
      }
      else if (cursor.recommendedBatchSize <= 1) {
        val a = cursor.next()
        val k = f(a)
        if (current == null || K.neqv(current, k)) {
          current = k
          Next(a, F.delay(this(self)))
        }
        else Suspend(F.delay(this(self)))
      }
      else {
        val buffer = ArrayBuffer.empty[A]
        var count = cursor.recommendedBatchSize

        // We already know hasNext == true
        do {
          val a = cursor.next()
          val k = f(a)
          count -= 1

          if (current == null || K.neqv(current, k)) {
            current = k
            buffer += a
          }
        } while (count > 0 && cursor.hasNext())

        val next =
          if (cursor.hasNext())
            F.delay(this(self))
          else
            rest.map(this)

        if (buffer.isEmpty)
          Suspend(next)
        else {
          val ref = BatchCursor.fromArray(buffer.toArray[Any]).asInstanceOf[BatchCursor[A]]
          NextCursor(ref, next)
        }
      }
    }
  }
}
