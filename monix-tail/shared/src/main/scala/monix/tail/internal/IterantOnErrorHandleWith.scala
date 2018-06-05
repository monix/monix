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

import cats.effect.{ExitCase, Sync}
import cats.syntax.all._
import monix.execution.internal.collection.ArrayStack
import monix.tail.Iterant.{Concat, Halt, Last, Next, NextBatch, NextCursor, Scope, Suspend}
import monix.tail.batches.{Batch, BatchCursor}

import scala.collection.mutable.ArrayBuffer
import scala.util.control.NonFatal

private[tail] object IterantOnErrorHandleWith {
  /**
    * Implementation for `Iterant.onErrorHandleWith`.
    */
  def apply[F[_], A](fa: Iterant[F, A], f: Throwable => Iterant[F, A])
    (implicit F: Sync[F]): Iterant[F, A] = {

    val loop = new HandleWithLoop(f)
    fa match {
      case NextBatch(_, _) | NextCursor(_, _) | Concat(_, _) =>
        // Suspending execution in order to preserve laziness and
        // referential transparency
        Suspend(F.delay(loop(fa)))
      case _ =>
        loop(fa)
    }
  }

  /**
    * Describing the loop as a class because we can control memory
    * allocation better this way.
    */
  private final class HandleWithLoop[F[_], A](f: Throwable => Iterant[F, A])
    (implicit F: Sync[F])
    extends (Iterant[F, A] => Iterant[F, A]) { loop =>

    private[this] var stack: ArrayStack[F[Iterant[F, A]]] = _

    def apply(node: Iterant[F, A]): Iterant[F, A] =
      try node match {
        case Next(a, rest) =>
          Next(a, continueWith(rest))
        case node @ NextCursor(cursor, rest) =>
          handleCursor(node, cursor, rest)
        case NextBatch(batch, rest) =>
          handleBatch(batch, rest)
        case Suspend(rest) =>
          Suspend(continueWith(rest))
        case Last(_) | Halt(None) =>
          handleLast(node)
        case Halt(Some(e)) =>
          f(e)
        case Concat(lh, rh) =>
          handleConcat(lh, rh)
        case Scope(open, use, close) =>
          handleScope(open, use, close)
      } catch {
        case e if NonFatal(e) => Halt(Some(e))
      }

    def continueWith(rest: F[Iterant[F, A]]): F[Iterant[F, A]] =
      rest.handleError(f).map(this)

    def extractBatch(ref: BatchCursor[A]): Array[A] = {
      var size = ref.recommendedBatchSize
      val buffer = ArrayBuffer.empty[A]
      while (size > 0 && ref.hasNext()) {
        buffer += ref.next()
        size -= 1
      }
      buffer.toArray[Any].asInstanceOf[Array[A]]
    }

    def handleBatch(batch: Batch[A], rest: F[Iterant[F, A]]): Iterant[F, A] = {
      var handleError = true
      try {
        val cursor = batch.cursor()
        handleError = false
        handleCursor(NextCursor(cursor, rest), cursor, rest)
      } catch {
        case e if NonFatal(e) && handleError =>
          f(e)
      }
    }

    def handleCursor(node: NextCursor[F, A], cursor: BatchCursor[A], rest: F[Iterant[F, A]]): Iterant[F, A] = {
      try {
        val array = extractBatch(cursor)
        val next =
          if (cursor.hasNext()) F.delay(loop(node))
          else continueWith(rest)

        if (array.length != 0)
          NextBatch(Batch.fromArray(array), next)
        else
          Suspend(next)
      } catch {
        case e if NonFatal(e) => f(e)
      }
    }

    def handleConcat(lh: F[Iterant[F, A]], rh: F[Iterant[F, A]]): Iterant[F, A] = {
      if (stack == null) stack = new ArrayStack()
      stack.push(rh)
      Suspend(continueWith(lh))
    }

    def handleLast(node: Iterant[F, A]): Iterant[F, A] = {
      val next =
        if (stack != null) stack.pop()
        else null.asInstanceOf[F[Iterant[F, A]]]

      next match {
        case null => node
        case stream =>
          node match {
            case Last(a) => Next(a, continueWith(stream))
            case _ => Suspend(continueWith(stream))
          }
      }
    }

    def handleScope(open: F[Unit], use: F[Iterant[F, A]], close: ExitCase[Throwable] => F[Unit]): Iterant[F, A] =
      Suspend {
        open.attempt.map {
          case Right(_) =>
            var thrownRef: Throwable = null
            val lh: Iterant[F, A] =
              Scope(F.unit, use.map(loop), exit =>
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
  }
}
