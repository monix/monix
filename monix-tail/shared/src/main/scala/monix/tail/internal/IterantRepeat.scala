/*
 * Copyright (c) 2014-2019 by The Monix Project Developers.
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
import monix.execution.internal.collection.ChunkedArrayStack
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
        Suspend(F.delay(new Loop(source).apply(source)))
    }

  private def repeatOne[F[_], A](item: A)
    (implicit F: Sync[F]): Iterant[F, A] = {

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

  private final class Loop[F[_], A](source: Iterant[F, A])
    (implicit F: Sync[F])
    extends Iterant.Visitor[F, A, Iterant[F, A]] {

    private[this] var isEmpty = true
    private[this] var stack: ChunkedArrayStack[F[Iterant[F, A]]] = _

    def visit(ref: Next[F, A]): Iterant[F, A] = {
      if (isEmpty) isEmpty = false
      Next(ref.item, ref.rest.map(this))
    }

    def visit(ref: NextBatch[F, A]): Iterant[F, A] = {
      if (isEmpty) {
        val cursor = ref.batch.cursor()
        visit(NextCursor(cursor, ref.rest))
      } else {
        NextBatch[F, A](ref.batch, ref.rest.map(this))
      }
    }

    def visit(ref: NextCursor[F, A]): Iterant[F, A] = {
      val cursor = ref.cursor
      if (isEmpty) isEmpty = cursor.isEmpty
      NextCursor[F, A](cursor, ref.rest.map(this))
    }

    def visit(ref: Suspend[F, A]): Iterant[F, A] =
      Suspend[F, A](ref.rest.map(this))

    def visit(ref: Concat[F, A]): Iterant[F, A] = {
      if (stack == null) stack = ChunkedArrayStack()
      stack.push(ref.rh)
      Suspend(ref.lh.map(this))
    }

    def visit[S](ref: Scope[F, S, A]): Iterant[F, A] =
      ref.runMap(this)

    def visit(ref: Last[F, A]): Iterant[F, A] = {
      val next =
        if (stack == null) null.asInstanceOf[F[Iterant[F, A]]]
        else stack.pop()

      next match {
        case null =>
          isEmpty = true
          Next(ref.item, F.pure(source).map(this))
        case rest =>
          isEmpty = false
          Next(ref.item, rest.map(this))
      }
    }

    def visit(ref: Halt[F, A]): Iterant[F, A] =
      ref.e match {
        case None =>
          val next =
            if (stack == null) null.asInstanceOf[F[Iterant[F, A]]]
            else stack.pop()

          next match {
            case null =>
              if (isEmpty) ref else {
                isEmpty = true
                Suspend(F.pure(source).map(this))
              }
            case rest =>
              Suspend(rest.map(this))
          }
        case _ =>
          ref
      }

    def fail(e: Throwable): Iterant[F, A] =
      Iterant.raiseError(e)
  }
}