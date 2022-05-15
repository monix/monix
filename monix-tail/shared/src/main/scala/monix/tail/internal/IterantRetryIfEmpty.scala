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

package monix.tail
package internal

import cats.effect.Sync
import cats.syntax.all._
import monix.tail.Iterant.{ Concat, Halt, Last, Next, NextBatch, NextCursor, Scope, Suspend }

private[tail] object IterantRetryIfEmpty {
  /**
    * Implementation for `Iterant.retryIfEmpty`.
    */
  def apply[F[_], A](source: Iterant[F, A], maxRetries: Option[Int])(implicit F: Sync[F]): Iterant[F, A] =
    source match {
      case Last(_) | Next(_, _) =>
        source
      case Halt(_) if maxRetries.exists(_ > 0) =>
        source
      case _ =>
        Suspend(F.delay {
          new Loop[F, A](source, maxRetries).cycle()
        })
    }

  private final class Loop[F[_], A](source: Iterant[F, A], maxRetries: Option[Int])(implicit F: Sync[F])
    extends Iterant.Visitor[F, A, Iterant[F, A]] {

    private[this] var hasElements = false
    private[this] var retriesRemaining = maxRetries.getOrElse(-1)
    private[this] val retryTask = F.delay {
      if (hasElements || retriesRemaining == 0)
        Iterant.empty[F, A]
      else {
        if (retriesRemaining > 0) retriesRemaining -= 1
        cycle()
      }
    }

    def cycle(): Iterant[F, A] = {
      Concat(F.pure(this.apply(source)), retryTask)
    }

    override def visit(ref: Next[F, A]): Iterant[F, A] = {
      hasElements = true
      ref
    }

    override def visit(ref: NextBatch[F, A]): Iterant[F, A] = {
      if (hasElements)
        ref
      else
        visit(NextCursor(ref.batch.cursor(), ref.rest))
    }

    override def visit(ref: NextCursor[F, A]): Iterant[F, A] = {
      hasElements = hasElements || !ref.cursor.isEmpty
      if (hasElements) ref
      else Suspend(ref.rest.map(this))
    }

    override def visit(ref: Suspend[F, A]): Iterant[F, A] =
      Suspend(ref.rest.map(this))

    override def visit(ref: Concat[F, A]): Iterant[F, A] = {
      if (hasElements) ref
      else Concat(ref.lh.map(this), ref.rh.map(this))
    }

    override def visit[S](ref: Scope[F, S, A]): Iterant[F, A] = {
      if (hasElements) ref
      else ref.runMap(this)
    }

    override def visit(ref: Last[F, A]): Iterant[F, A] = {
      hasElements = true
      ref
    }

    override def visit(ref: Halt[F, A]): Iterant[F, A] =
      ref

    override def fail(e: Throwable): Iterant[F, A] =
      Iterant.raiseError(e)
  }
}
