/*
 * Copyright (c) 2014-2021 by The Monix Project Developers.
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

private[tail] object IterantDropWhileWithIndex {
  /**
    * Implementation for `Iterant#dropWhileWithIndex`
    */
  def apply[F[_], A](source: Iterant[F, A], p: (A, Int) => Boolean)(implicit F: Sync[F]): Iterant[F, A] = {
    Suspend(F.delay(new Loop(p).apply(source)))
  }

  private class Loop[F[_], A](p: (A, Int) => Boolean)(implicit F: Sync[F])
    extends Iterant.Visitor[F, A, Iterant[F, A]] { loop =>

    private[this] var index = 0
    private[this] var dropFinished = false

    private def getAndIncrement(): Int = {
      val old = index
      index += 1
      old
    }

    def visit(ref: Next[F, A]): Iterant[F, A] =
      if (dropFinished) ref
      else {
        val item = ref.item
        if (p(item, getAndIncrement()))
          Suspend(ref.rest.map(this))
        else {
          dropFinished = true
          ref
        }
      }

    def visit(ref: NextBatch[F, A]): Iterant[F, A] =
      if (dropFinished) ref
      else visit(ref.toNextCursor())

    def visit(ref: NextCursor[F, A]): Iterant[F, A] =
      if (dropFinished) ref
      else {
        val cursor = ref.cursor
        var keepDropping = true
        var item: A = null.asInstanceOf[A]

        while (keepDropping && cursor.hasNext()) {
          item = cursor.next()
          keepDropping = p(item, getAndIncrement())
        }

        if (keepDropping)
          Suspend(ref.rest.map(this))
        else {
          dropFinished = true
          if (cursor.hasNext())
            Next(item, F.pure(ref))
          else
            Next(item, ref.rest)
        }
      }

    def visit(ref: Suspend[F, A]): Iterant[F, A] =
      if (dropFinished) ref else Suspend(ref.rest.map(this))

    def visit(ref: Concat[F, A]): Iterant[F, A] =
      if (dropFinished) ref else ref.runMap(this)

    def visit[S](ref: Scope[F, S, A]): Iterant[F, A] =
      if (dropFinished) ref else ref.runMap(this)

    def visit(ref: Last[F, A]): Iterant[F, A] =
      if (!dropFinished && p(ref.item, getAndIncrement()))
        Halt(None)
      else {
        dropFinished = true
        ref
      }

    def visit(ref: Halt[F, A]): Iterant[F, A] =
      ref

    def fail(e: Throwable): Iterant[F, A] =
      Iterant.raiseError(e)
  }
}
