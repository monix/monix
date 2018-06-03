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
import monix.tail.Iterant.{Concat, Halt, Last, Next, NextBatch, NextCursor, Scope, Suspend}
import monix.tail.batches.{Batch, BatchCursor}
import scala.collection.mutable.ArrayBuffer
import scala.util.control.NonFatal

private[tail] object IterantOnError {
  /** Implementation for `Iterant.onErrorHandleWith`. */
  def handleWith[F[_], A](fa: Iterant[F, A], f: Throwable => Iterant[F, A])(implicit F: Sync[F]): Iterant[F, A] = {
    def extractBatch(ref: BatchCursor[A]): Array[A] = {
      var size = ref.recommendedBatchSize
      val buffer = ArrayBuffer.empty[A]
      while (size > 0 && ref.hasNext()) {
        buffer += ref.next()
        size -= 1
      }
      buffer.toArray[Any].asInstanceOf[Array[A]]
    }

    def loop(fa: Iterant[F, A]): Iterant[F, A] =
      try fa match {

        case Next(a, lt) =>
          Next(a, lt.map(loop))

        case NextCursor(cursor, rest) =>
          try {
            val array = extractBatch(cursor)
            val next =
              if (cursor.hasNext()) F.delay(loop(fa))
              else rest.map(loop)

            if (array.length != 0)
              NextCursor(BatchCursor.fromArray(array), next)
            else
              Suspend(next)
          } catch {
            case e if NonFatal(e) =>
              Iterant.raiseError(e)
          }

        case NextBatch(batch, rest) =>
          try {
            loop(NextCursor(batch.cursor(), rest))
          } catch {
            case e if NonFatal(e) =>
              Iterant.raiseError(e)
          }

        case Suspend(rest) =>
          Suspend(rest.map(loop))
        case Last(_) | Halt(None) =>
          fa
        case Halt(Some(e)) =>
          f(e)

        case Concat(lh, rh) =>
          Concat(lh.map(loop), rh.map(loop))

        case Scope(open, rest, close) =>
          Suspend {
            open.attempt.map {
              case Right(_) =>
                var thrownRef: Throwable = null
                val lh: Iterant[F, A] =
                  Scope(F.unit, rest.map(loop), exit =>
                    F.suspend(F.handleError(close(exit)) { e =>
                      thrownRef = e
                    }))

                Concat(F.pure(lh), F.delay {
                  if (thrownRef == null) Iterant.empty
                  else Halt(Some(thrownRef))
                })

              case Left(ex) =>
                f(ex)
            }
          }

      } catch {
        case e if NonFatal(e) =>
          try f(e) catch { case err if NonFatal(err) => Halt(Some(err)) }
      }

    fa match {
      case NextBatch(_, _) | NextCursor(_, _) =>
        // Suspending execution in order to preserve laziness and
        // referential transparency
        Suspend(F.delay(loop(fa)))
      case _ =>
        loop(fa)
    }
  }

  /** Implementation for `Iterant.attempt`. */
  def attempt[F[_], A](fa: Iterant[F, A])(implicit F: Sync[F]): Iterant[F, Either[Throwable, A]] = {
    type Attempt = Either[Throwable, A]

    def extractBatch(ref: BatchCursor[A]): Array[Attempt] = {
      var size = ref.recommendedBatchSize
      val buffer = ArrayBuffer.empty[Attempt]
      while (size > 0 && ref.hasNext()) {
        buffer += Right(ref.next())
        size -= 1
      }
      buffer.toArray[Attempt]
    }

    def loop(fa: Iterant[F, A]): Iterant[F, Attempt] =
      try fa match {
        case Next(a, rest) =>
          Next(Right(a), rest.map(loop))
        case NextBatch(batch, rest) =>
          loop(NextCursor(batch.cursor(), rest))
        case NextCursor(cursor, rest) =>
          val cb = extractBatch(cursor)
          val batch = Batch.fromArray(cb)
          if (cb.length > 0 && cb.last.isLeft) {
            NextBatch(batch, F.pure(Iterant.empty))
          } else if (!cursor.hasNext()) {
            NextBatch(batch, rest.map(loop))
          } else {
            NextBatch(batch, F.delay(loop(fa)))
          }
        case Suspend(rest) =>
          Suspend(rest.map(loop))
        case Last(a) =>
          Last(Right(a))
        case Halt(None) =>
          fa.asInstanceOf[Iterant[F, Attempt]]
        case Halt(Some(ex)) =>
          Last(Left(ex))

        case Concat(lh, rh) =>
          Concat(lh.map(loop), rh.map(loop))

        case Scope(open, rest, close) =>
          Suspend {
            open.attempt.map {
              case Right(_) =>
                var thrownRef: Throwable = null
                val lh: Iterant[F, Attempt] =
                  Scope(F.unit, rest.map(loop), exit =>
                    F.suspend(F.handleError(close(exit)) { e =>
                      thrownRef = e
                    }))

                Concat(F.pure(lh), F.delay {
                  if (thrownRef == null) Iterant.empty
                  else Last(Left(thrownRef))
                })

              case left @ Left(_) =>
                Last(left.asInstanceOf[Attempt])
            }
          }
      } catch {
        case e if NonFatal(e) =>
          Last(Left(e))
      }

    Suspend(F.delay(loop(fa)).handleError(ex => Last(Left(ex))))
  }
}
