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

import cats.syntax.functor._
import cats.effect.Sync
import monix.execution.internal.collection.ChunkedArrayStack
import monix.tail.Iterant
import monix.tail.Iterant._

private[tail] object IterantSwitchIfEmpty {
  /**
    * Implementation for `Iterant.switchIfEmpty`.
    */
  def apply[F[_], A](primary: Iterant[F, A], backup: Iterant[F, A])(implicit F: Sync[F]): Iterant[F, A] = {

    primary match {
      case Next(_, _) | Last(_) =>
        primary
      case Halt(e) =>
        e match {
          case None => backup
          case _ => primary
        }
      case _ =>
        Suspend(F.delay(new Loop[F, A](backup).apply(primary)))
    }
  }

  private final class Loop[F[_], A](backup: Iterant[F, A])(implicit F: Sync[F])
    extends Iterant.Visitor[F, A, Iterant[F, A]] { self =>

    private[this] var isEmpty = true
    private[this] var stackRef: ChunkedArrayStack[F[Iterant[F, A]]] = _

    private[this] def isStackEmpty: Boolean =
      stackRef == null || stackRef.isEmpty

    private def stackPush(item: F[Iterant[F, A]]): Unit = {
      if (stackRef == null) stackRef = ChunkedArrayStack()
      stackRef.push(item)
    }

    private def stackPop(): F[Iterant[F, A]] = {
      if (stackRef != null) stackRef.pop()
      else null.asInstanceOf[F[Iterant[F, A]]]
    }

    def visit(ref: Next[F, A]): Iterant[F, A] =
      if (isStackEmpty) {
        ref
      } else {
        isEmpty = false
        Next(ref.item, ref.rest.map(this))
      }

    def visit(ref: NextBatch[F, A]): Iterant[F, A] =
      if (!isEmpty) {
        if (isStackEmpty) ref
        else NextBatch(ref.batch, ref.rest.map(this))
      } else {
        visit(ref.toNextCursor())
      }

    def visit(ref: NextCursor[F, A]): Iterant[F, A] = {
      val cursor = ref.cursor
      if (!isEmpty || cursor.hasNext()) {
        if (isStackEmpty) {
          ref
        } else {
          isEmpty = false
          NextCursor(cursor, ref.rest.map(this))
        }
      } else {
        Suspend(ref.rest.map(this))
      }
    }

    def visit(ref: Suspend[F, A]): Iterant[F, A] =
      Suspend(ref.rest.map(this))

    def visit(ref: Concat[F, A]): Iterant[F, A] = {
      stackPush(ref.rh)
      Suspend(ref.lh.map(this))
    }

    def visit[S](ref: Scope[F, S, A]): Iterant[F, A] =
      ref.runMap(this)

    def visit(ref: Last[F, A]): Iterant[F, A] = {
      isEmpty = false
      stackPop() match {
        case null => ref
        case xs => Next(ref.item, xs.map(this))
      }
    }

    def visit(ref: Halt[F, A]): Iterant[F, A] =
      ref.e match {
        case None =>
          stackPop() match {
            case null =>
              if (isEmpty) backup else ref
            case xs =>
              Suspend(xs.map(this))
          }
        case _ =>
          ref
      }

    def fail(e: Throwable): Iterant[F, A] =
      Iterant.raiseError(e)
  }
}
