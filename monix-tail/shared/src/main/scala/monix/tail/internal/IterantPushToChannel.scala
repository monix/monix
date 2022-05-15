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
import monix.catnap.ProducerF
import monix.execution.internal.collection.ChunkedArrayStack
import monix.tail.Iterant.Concat

private[tail] object IterantPushToChannel {
  /**
    * Implementation for [[Iterant.pushToChannel]].
    */
  def apply[F[_], A](source: Iterant[F, A], channel: ProducerF[F, Option[Throwable], A])(
    implicit F: Sync[F]
  ): F[Unit] = {

    F.defer(new Loop(channel).apply(source))
  }

  private final class Loop[F[_], A](channel: ProducerF[F, Option[Throwable], A])(implicit F: Sync[F])
    extends Iterant.Visitor[F, A, F[Unit]] { loop =>

    // -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=
    // For dealing with push results
    private[this] val trueRef = F.pure(true)
    private[this] var rest: F[Iterant[F, A]] = _
    private val bindNext = (continue: Boolean) => {
      if (continue) F.flatMap(rest)(loop)
      else F.unit
    }

    private def process(task: F[Boolean], rest: F[Iterant[F, A]]) = {
      this.rest = rest
      F.flatMap(task)(bindNext)
    }

    // -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=
    // Used in visit(Concat)
    private[this] var stackRef: ChunkedArrayStack[F[Iterant[F, A]]] = _

    private def stackPush(item: F[Iterant[F, A]]): Unit = {
      if (stackRef == null) stackRef = ChunkedArrayStack()
      stackRef.push(item)
    }

    private def stackPop(): F[Iterant[F, A]] = {
      if (stackRef != null) stackRef.pop()
      else null.asInstanceOf[F[Iterant[F, A]]]
    }

    private def isStackEmpty(): Boolean =
      stackRef == null || stackRef.isEmpty

    private[this] val concatContinue: (Unit => F[Unit]) =
      _ =>
        stackPop() match {
          case null => F.unit
          case xs => F.flatMap(xs)(loop)
        }
    // -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=

    def visit(ref: Iterant.Next[F, A]): F[Unit] =
      process(channel.push(ref.item), ref.rest)

    def visit(ref: Iterant.NextBatch[F, A]): F[Unit] = {
      val push = channel.pushMany(ref.batch.toIterable)
      process(push, ref.rest)
    }

    def visit(ref: Iterant.NextCursor[F, A]): F[Unit] = {
      val iter = new Iterable[A] { def iterator = ref.cursor.toIterator }
      val push = channel.pushMany(iter)
      process(push, ref.rest)
    }

    def visit(ref: Iterant.Suspend[F, A]): F[Unit] =
      process(trueRef, ref.rest)

    def visit(ref: Concat[F, A]): F[Unit] = {
      stackPush(ref.rh)
      val left = F.flatMap(ref.lh)(loop)
      F.flatMap(left)(concatContinue)
    }

    def visit[S](ref: Iterant.Scope[F, S, A]): F[Unit] =
      ref.runFold(this)

    def visit(ref: Iterant.Last[F, A]): F[Unit] =
      F.flatMap(channel.push(ref.item)) {
        case true =>
          if (isStackEmpty())
            channel.halt(None)
          else
            F.unit
        case false =>
          F.unit
      }

    def visit(ref: Iterant.Halt[F, A]): F[Unit] =
      ref.e match {
        case None if !isStackEmpty() =>
          F.unit
        case _ =>
          channel.halt(ref.e)
      }

    def fail(e: Throwable): F[Unit] =
      channel.halt(Some(e))
  }
}
