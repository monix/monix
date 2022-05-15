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

import java.io.PrintStream
import cats.effect.{ ExitCase, Sync }
import cats.syntax.all._
import monix.tail.Iterant
import monix.tail.Iterant.{ Concat, Halt, Last, Next, NextBatch, NextCursor, Scope, Suspend }

private[tail] object IterantDump {
  /**
    * Implementation for `Iterant#dump`
    */
  def apply[F[_], A](source: Iterant[F, A], prefix: String, out: PrintStream = System.out)(
    implicit F: Sync[F]
  ): Iterant[F, A] = {
    Suspend(F.delay(new Loop(prefix, out).apply(source)))
  }

  private class Loop[F[_], A](prefixInit: String, out: PrintStream)(implicit F: Sync[F])
    extends Iterant.Visitor[F, A, Iterant[F, A]] { loop =>

    private[this] var pos = 0L
    private[this] var prefix = prefixInit

    def visit(ref: Next[F, A]): Iterant[F, A] = {
      out.println(s"$pos: $prefix --> next --> ${ref.item}")
      pos += 1
      Next[F, A](ref.item, moveNext(ref.rest))
    }

    def visit(ref: NextBatch[F, A]): Iterant[F, A] = {
      val dumped = ref.batch.map { el =>
        out.println(s"$pos: $prefix --> next-batch --> $el")
        pos += 1
        el
      }
      NextBatch[F, A](dumped, moveNext(ref.rest))
    }

    def visit(ref: NextCursor[F, A]): Iterant[F, A] = {
      val dumped = ref.cursor.map { el =>
        out.println(s"$pos: $prefix --> next-cursor --> $el")
        pos += 1
        el
      }
      NextCursor[F, A](dumped, moveNext(ref.rest))
    }

    def visit(ref: Suspend[F, A]): Iterant[F, A] = {
      out.println(s"$pos: $prefix --> suspend")
      pos += 1
      Suspend[F, A](moveNext(ref.rest))
    }

    def visit(ref: Concat[F, A]): Iterant[F, A] = {
      val oldPrefix = prefix
      val oldPos = pos
      out.println(s"$pos: $prefix --> concat")
      pos += 1

      Concat(
        F.defer {
          prefix = s"$oldPrefix --> concat-lh ($oldPos)"
          moveNext(ref.lh)
        },
        F.defer {
          prefix = oldPrefix
          moveNext(ref.rh)
        }
      )
    }

    def visit[S](ref: Scope[F, S, A]): Iterant[F, A] = {
      val oldPrefix = prefix
      val oldPos = pos
      out.println(s"$pos: $prefix --> resource")
      pos += 1

      Scope[F, S, A](
        ref.acquire.map { v =>
          prefix = s"$oldPrefix --> resource ($oldPos)"
          v
        },
        AndThen(ref.use).andThen(moveNext),
        (s, ec) =>
          F.defer {
            out.println(s"$pos: $prefix --> release")
            pos += 1
            prefix = oldPrefix
            ref.release(s, ec)
          }
      )
    }

    def visit(ref: Last[F, A]): Iterant[F, A] = {
      out.println(s"$pos: $prefix --> last --> ${ref.item}")
      pos += 1
      Last(ref.item)
    }

    def visit(ref: Halt[F, A]): Iterant[F, A] = {
      out.println(s"$pos: $prefix --> halt --> ${ref.e.map(_.toString).getOrElse("no error")}")
      pos += 1
      ref
    }

    def fail(e: Throwable): Iterant[F, A] = {
      out.println(s"$pos: $prefix --> unexpected error --> $e")
      pos += 1
      Iterant.raiseError(e)
    }

    def moveNext(rest: F[Iterant[F, A]]): F[Iterant[F, A]] =
      F.guaranteeCase(rest) {
        case ExitCase.Error(e) =>
          F.delay {
            out.println(s"$pos: $prefix --> effect error --> $e")
            pos += 1
          }
        case ExitCase.Canceled =>
          F.delay {
            out.println(s"$pos: $prefix --> effect cancelled")
            pos += 1
          }
        case ExitCase.Completed =>
          F.unit
      }
        .map(this)
  }
}
