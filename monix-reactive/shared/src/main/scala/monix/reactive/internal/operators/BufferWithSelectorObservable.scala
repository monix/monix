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

package monix.reactive.internal.operators

import monix.execution.Ack.{ Continue, Stop }
import monix.execution.cancelables.{ CompositeCancelable, SingleAssignCancelable }
import monix.execution.{ Ack, Cancelable }
import monix.reactive.Observable
import monix.reactive.observers.Subscriber
import scala.collection.mutable.ListBuffer
import scala.concurrent.{ Future, Promise }

private[reactive] final class BufferWithSelectorObservable[+A, S](
  source: Observable[A],
  sampler: Observable[S],
  maxSize: Int,
  sizeOf: A => Int
) extends Observable[Seq[A]] {

  def unsafeSubscribeFn(downstream: Subscriber[Seq[A]]): Cancelable = {
    val upstreamSubscription = SingleAssignCancelable()
    val samplerSubscription = SingleAssignCancelable()
    val composite = CompositeCancelable(upstreamSubscription, samplerSubscription)

    upstreamSubscription := source.unsafeSubscribeFn(new Subscriber[A] { upstreamSubscriber =>
      implicit val scheduler = downstream.scheduler

      // MUST BE synchronized by `self`
      private[this] var buffer = ListBuffer.empty[A]
      // Maintain internal buffer weight not to compute the weight
      // of the buffer each time an element is added.
      // So to keep complexity to O(1) for each added element.
      // MUST BE synchronized by `self`
      private[this] var bufferWeight: Int = 0
      // MUST BE synchronized by `self`
      private[this] var promise = Promise[Ack]()
      // To be written in onComplete/onError, to be read from tick
      private[this] var upstreamIsDone = false
      // MUST BE synchronized by `self`.
      private[this] var downstreamIsDone = false

      def onNext(elem: A): Future[Ack] =
        upstreamSubscriber.synchronized {
          if (downstreamIsDone) Stop
          else {
            buffer += elem
            bufferWeight += sizeOf(elem)
            if (maxSize > 0 && bufferWeight >= maxSize)
              promise.future
            else
              Continue
          }
        }

      def onError(ex: Throwable): Unit =
        upstreamSubscriber.synchronized {
          if (!downstreamIsDone) {
            downstreamIsDone = true
            samplerSubscription.cancel()
            downstream.onError(ex)
          }
        }

      def onComplete(): Unit =
        upstreamSubscriber.synchronized {
          upstreamIsDone = true
        }

      samplerSubscription := sampler.unsafeSubscribeFn(new Subscriber[S] {
        implicit val scheduler = downstream.scheduler

        def onNext(elem: S): Future[Ack] =
          upstreamSubscriber.synchronized(signalNext())

        def onError(ex: Throwable): Unit =
          upstreamSubscriber.onError(ex)

        def onComplete(): Unit =
          upstreamSubscriber.synchronized {
            upstreamIsDone = true
            signalNext()
            ()
          }

        // MUST BE synchronized by `self`
        private def signalNext(): Future[Ack] =
          if (downstreamIsDone) Stop
          else {
            val next = {
              val signal = buffer.toList
              // Refresh Buffer
              buffer = ListBuffer.empty[A]
              bufferWeight = 0
              // Refresh back-pressure promise, but only if we have
              // a maxSize to worry about
              if (maxSize > 0) {
                val oldPromise = promise
                promise = Promise()
                oldPromise.success(Continue)
              }
              // Actual signaling
              val ack = downstream.onNext(signal)
              // Callback for cleaning
              ack.syncOnStopOrFailure { _ =>
                downstreamIsDone = true
                upstreamSubscription.cancel()
              }
            }

            if (!upstreamIsDone) next
            else {
              downstreamIsDone = true
              upstreamSubscription.cancel()
              if (next != Stop) downstream.onComplete()
              Stop
            }
          }
      })
    })

    composite
  }
}
