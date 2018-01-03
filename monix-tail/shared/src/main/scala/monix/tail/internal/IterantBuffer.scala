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
import monix.execution.misc.NonFatal
import monix.tail.Iterant.{Halt, Last, Next, NextBatch, NextCursor, Suspend}
import monix.tail.batches.Batch
import monix.tail.internal.IterantUtils.signalError

private[tail] object IterantBuffer {
  /** Implementation for `Iterant.bufferSliding`. */
  def sliding[F[_], A](self: Iterant[F, A], count: Int, skip: Int)
    (implicit F: Sync[F]): Iterant[F, Seq[A]] = {

    build[F, A, Seq[A]](self, count, skip,
      (seq, rest, stop) => Next(seq, rest, stop),
      seq => Last(seq))
  }

  /** Implementation for `Iterant.batched`. */
  def batched[F[_], A](self: Iterant[F, A], count: Int)
    (implicit F: Sync[F]): Iterant[F, A] = {

    build[F, A, A](self, count, count,
      (seq, rest, stop) => NextBatch(Batch.fromAnyArray(seq), rest, stop),
      seq => NextBatch(Batch.fromAnyArray(seq), F.pure(Halt(None)), F.unit))
  }

  private def build[F[_], A, B](
    self: Iterant[F, A],
    count: Int,
    skip: Int,
    f: (Array[A], F[Iterant[F, B]], F[Unit]) => Iterant[F, B],
    last: Array[A] => Iterant[F, B])
    (implicit F: Sync[F]): Iterant[F, B] = {

    val buffer = new Buffer[A](count, skip)

    def process(fa: Iterant[F, A]): F[Iterant[F, B]] = {
      val NextCursor(cursor, rest, stop) = fa

      while (cursor.hasNext()) {
        val seq = buffer.push(cursor.next())
        if (seq != null) {
          val next = if (cursor.hasNext()) F.pure(fa) else rest
          return F.pure(f(seq, next.flatMap(loop), stop))
        }
      }

      rest.flatMap(loop)
    }

    def loop(self: Iterant[F, A]): F[Iterant[F, B]] = {
      try self match {
        case Next(a, rest, stop) =>
          val seq = buffer.push(a)
          if (seq != null)
            F.pure(f(seq, rest.flatMap(loop), stop))
          else
            rest.flatMap(loop)

        case self@NextCursor(_, _, _) =>
          process(self)

        case NextBatch(batch, rest, stop) =>
          process(NextCursor(batch.cursor(), rest, stop))

        case Suspend(rest, _) =>
          rest.flatMap(loop)

        case Last(a) =>
          var seq = buffer.push(a)
          if (seq == null) seq = buffer.rest()
          F.pure {
            if (seq != null && seq.length > 0)
              last(seq)
            else
              Halt(None)
          }

        case ref @ Halt(_) =>
          val self = ref.asInstanceOf[Iterant[F, B]]
          val seq = buffer.rest()
          F.pure {
            if (seq != null && seq.length > 0)
              f(seq, F.pure(self), F.unit)
            else
              self
          }
      }
      catch {
        case NonFatal(e) =>
          F.pure(signalError(self, e))
      }
    }

    Suspend(F.suspend(loop(self)), self.earlyStop)
  }

  private final class Buffer[A](count: Int, skip: Int) {
    private[this] val toDrop = if (count > skip) 0 else skip - count
    private[this] val toRepeat = if (skip > count) 0 else count - skip

    private[this] var isBufferNew = true
    private[this] var buffer = new Array[AnyRef](count)
    private[this] var dropped = 0
    private[this] var length = 0

    def push(elem: A): Array[A] = {
      if (dropped > 0) {
        dropped -= 1
        null
      } else {
        buffer(length) = elem.asInstanceOf[AnyRef]
        length += 1

        if (length < count) null else {
          val oldBuffer = buffer
          buffer = new Array(count)

          if (toRepeat > 0) {
            System.arraycopy(oldBuffer, count-toRepeat, buffer, 0, toRepeat)
            length = toRepeat
          } else {
            dropped = toDrop
            length = 0
          }

          // signaling downstream
          if (isBufferNew) isBufferNew = false
          oldBuffer.asInstanceOf[Array[A]]
        }
      }
    }

    def rest(): Array[A] = {
      val threshold = if (isBufferNew) 0 else toRepeat
      if (length > threshold)
        buffer.take(length).asInstanceOf[Array[A]]
      else
        null
    }
  }
}
