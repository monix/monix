/*
 * Copyright (c) 2014-2021 by The Monix Project Developers.
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

package monix.reactive.internal.operators

import monix.execution.Ack
import monix.execution.Ack.{ Continue, Stop }
import monix.reactive.Observable.Operator
import monix.reactive.observers.Subscriber

import scala.collection.mutable.ListBuffer
import scala.concurrent.Future
import scala.util.control.NonFatal

private[reactive] final class BufferWhileOperator[A](p: A => Boolean, inclusive: Boolean) extends Operator[A, Seq[A]] {
  def apply(out: Subscriber[Seq[A]]): Subscriber[A] =
    new Subscriber[A] {
      implicit val scheduler = out.scheduler

      private[this] var isDone = false
      private[this] var ack: Future[Ack] = Continue
      private[this] var buffer = ListBuffer.empty[A]

      def onNext(elem: A): Future[Ack] =
        if (isDone) Stop
        else {
          // Protects calls to user code from within an operator
          var streamError = true
          try {
            val keepBuffering = p(elem)
            streamError = false

            if (!keepBuffering) {
              if (inclusive) {
                buffer.append(elem)
                val toEmit = buffer.toList
                buffer = ListBuffer.empty
                ack = out.onNext(toEmit)
              } else if (buffer.nonEmpty) {
                val toEmit = buffer.toList
                buffer = ListBuffer(elem)
                ack = out.onNext(toEmit)
              } else {
                buffer.append(elem)
              }

              ack
            } else {
              buffer.append(elem)
              Continue
            }
          } catch {
            case NonFatal(ex) if streamError =>
              onError(ex)
              Stop
          }
        }

      def onError(ex: Throwable): Unit =
        if (!isDone) {
          isDone = true
          buffer = null // GC purposes
          out.onError(ex)
        }

      def onComplete(): Unit =
        if (!isDone) {
          isDone = true

          if (buffer.nonEmpty) {
            val bundleToSend = buffer.toList
            // In case the last onNext isn't finished, then
            // we need to apply back-pressure, otherwise this
            // onNext will break the contract.
            ack.syncOnContinue {
              out.onNext(bundleToSend)
              out.onComplete()
            }
          } else {
            // We can just stream directly
            out.onComplete()
          }

          // GC relief
          buffer = null
        }
    }
}
