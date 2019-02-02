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
import cats.effect.{Resource, Sync}
import monix.tail.Iterant
import monix.tail.Iterant.{Concat, NextCursor, Suspend}
import cats.implicits._

private[tail] object IterantUncons {
  type Uncons[F[_], A] = Resource[F, (Option[A], Iterant[F, A])]
  def apply[F[_]: Sync, A](source: Iterant[F, A]): Uncons[F, A] =
    Resource.liftF(Sync[F].delay(new Loop[F, A])).flatMap(f => f(source))

  class Loop[F[_]: Sync, A] extends Iterant.Visitor[F, A, Uncons[F, A]] {
    def visit(ref: Iterant.Next[F, A]): Uncons[F, A] =
      Resource.pure((Some(ref.item), Suspend(ref.rest)))

    def visit(ref: Iterant.NextBatch[F, A]): Uncons[F, A] =
      this(ref.toNextCursor())

    def visit(ref: Iterant.NextCursor[F, A]): Uncons[F, A] =
      if (ref.cursor.isEmpty) Resource.liftF(ref.rest).flatMap(this)
      else {
        val head = ref.cursor.next()
        val tail = if (ref.cursor.isEmpty) Suspend(ref.rest)
                   else NextCursor(ref.cursor, ref.rest)
        Resource.pure((Some(head), tail))
      }

    def visit(ref: Iterant.Suspend[F, A]): Uncons[F, A] =
      Resource.liftF(ref.rest).flatMap(this)

    def visit(ref: Iterant.Concat[F, A]): Uncons[F, A] =
      Resource.liftF(ref.lh).flatMap(this).flatMap {
        case (s @ Some(_), tail) => Resource.pure((s, Concat(tail.pure[F], ref.rh)))
        case (None, _) => Resource.liftF(ref.rh).flatMap(this)
      }

    def visit[S](ref: Iterant.Scope[F, S, A]): Uncons[F, A] =
      Resource.makeCase(ref.acquire)(ref.release).flatMap(rs => this(Suspend(ref.use(rs))))

    def visit(ref: Iterant.Last[F, A]): Uncons[F, A] =
      Resource.pure((Some(ref.item), Iterant.empty))

    def visit(ref: Iterant.Halt[F, A]): Uncons[F, A] = ref.e match {
      case Some(ex) => Resource.liftF(ex.raiseError[F, (Option[A], Iterant[F, A])])
      case None => Resource.pure((None, Iterant.empty))
    }

    def fail(e: Throwable): Uncons[F, A] = Resource.liftF(e.raiseError[F, (Option[A], Iterant[F, A])])
  }
}
