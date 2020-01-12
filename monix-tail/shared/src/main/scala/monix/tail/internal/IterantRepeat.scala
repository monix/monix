/*
 * Copyright (c) 2014-2020 by The Monix Project Developers.
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
import monix.execution.internal.Platform
import monix.tail.Iterant
import monix.tail.Iterant.{Concat, Halt, Last, Next, NextBatch, NextCursor, Scope, Suspend}
import monix.tail.batches.{BatchCursor, GenericBatch, GenericCursor}

private[tail] object IterantRepeat {
  /**
    * Implementation for `Iterant.repeat`.
    */
  def apply[F[_], A](source: Iterant[F, A])(implicit F: Sync[F]): Iterant[F, A] =
    source match {
      case Halt(_) => source
      case Last(item) => repeatOne(item)
      case _ =>
        Suspend(F.delay { new Loop[F, A](source).cycle() })
    }

  private def repeatOne[F[_], A](item: A)(implicit F: Sync[F]): Iterant[F, A] = {
    val batch = new GenericBatch[A] {
      def cursor(): BatchCursor[A] =
        new GenericCursor[A] {
          def hasNext(): Boolean = true
          def next(): A = item
          def recommendedBatchSize: Int =
            Platform.recommendedBatchSize
        }
    }
    NextBatch(batch, F.pure(Iterant.empty))
  }

  private final class Loop[F[_], A](source: Iterant[F, A])(implicit F: Sync[F])
    extends Iterant.Visitor[F, A, Iterant[F, A]] {

    private[this] var hasElements = false
    private[this] val repeatTask = F.delay {
      if (hasElements)
        cycle()
      else
        Iterant.empty[F, A]
    }

    def cycle(): Iterant[F, A] = {
      Concat(F.pure(apply(source)), repeatTask)
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
      if (hasElements)
        ref
      else
        Suspend(ref.rest.map(this))
    }

    override def visit(ref: Suspend[F, A]): Iterant[F, A] =
      Suspend(ref.rest.map(this))

    override def visit(ref: Concat[F, A]): Iterant[F, A] = {
      if (hasElements)
        ref
      else
        Concat(ref.lh.map(this), ref.rh.map(this))
    }

    override def visit[S](ref: Scope[F, S, A]): Iterant[F, A] = {
      if (hasElements)
        ref
      else
        ref.runMap(this)
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
