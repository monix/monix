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

package monix.tail.internal

import cats.effect.Sync
import cats.syntax.all._
import monix.execution.internal.collection.{ArrayStack, DropHeadOnOverflowQueue}
import monix.tail.Iterant
import monix.tail.Iterant.{Concat, Halt, Last, Next, NextBatch, NextCursor, Scope, Suspend}
import monix.tail.batches.BatchCursor

private[tail] object IterantTakeLast {
  /**
    * Implementation for `Iterant#takeLast`
    */
  def apply[F[_], A](source: Iterant[F, A], n: Int)(implicit F: Sync[F]): Iterant[F, A] = {
    if (n < 1)
      Iterant.empty
    else {
      Suspend(F.delay(new Loop(n).apply(source)))
    }
  }

  private class Loop[F[_], A](n: Int)(implicit F: Sync[F])
    extends (Iterant[F, A] => Iterant[F, A]) { loop =>

    private val buffer = DropHeadOnOverflowQueue.boxed[A](n)
    private val stack = new ArrayStack[F[Iterant[F, A]]]()

    def apply(source: Iterant[F, A]): Iterant[F, A] = {
      source match {
        case Next(item, rest) =>
          buffer.offer(item)
          Suspend(rest.map(loop))
        case NextCursor(cursor, rest) =>
          while (cursor.hasNext()) buffer.offer(cursor.next())
          Suspend(rest.map(loop))
        case NextBatch(batch, rest) =>
          val cursor = batch.cursor()
          while (cursor.hasNext()) buffer.offer(cursor.next())
          Suspend(rest.map(loop))
        case Suspend(rest) =>
          Suspend(rest.map(loop))
        case Last(item) =>
          handleLast(item)
        case s@Scope(_, _, _) =>
          s.runMap(loop)
        case Concat(lh, rh) =>
          handleConcat(lh, rh)
        case Halt(None) =>
          handleHalt()
        case halt@Halt(Some(_)) =>
          halt
      }
    }

    private def handleLast(item: A): Iterant[F, A] = {
      stack.pop() match {
        case null =>
          buffer.offer(item)
          finalCursor()
        case some =>
          loop(Next(item, some))
      }
    }

    private def handleConcat(lh: F[Iterant[F, A]], rh: F[Iterant[F, A]]): Iterant[F, A] = {
      stack.push(rh)
      Suspend(lh.map(loop))
    }

    private def handleHalt(): Iterant[F, A] = {
      stack.pop() match {
        case null => finalCursor()
        case some => Suspend(some.map(loop))
      }
    }

    private def finalCursor(): Iterant[F, A] = {
      val cursor = BatchCursor.fromIterator(buffer.iterator(true), Int.MaxValue)
      NextCursor(cursor, F.pure(Iterant.empty))
    }
  }

}