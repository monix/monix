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

import cats.effect.{Async, Timer}
import cats.syntax.all._
import monix.eval.Callback
import monix.execution.misc.AsyncVar
import monix.execution.rstreams.ReactivePullStrategy
import monix.execution.schedulers.TrampolineExecutionContext.immediate
import monix.tail.Iterant
import monix.tail.Iterant.{Halt, Next, NextBatch, Scope}
import monix.tail.batches.Batch
import org.reactivestreams.{Publisher, Subscriber, Subscription}

// TODO: this can be migrated to cats-effect MVar, but would require Effect[F]
private[tail] object IterantFromReactivePublisher {
  class UnfoldSubscriber[F[_], A] (
    bufferSize: Int
  )(implicit
    F: Async[F],
    timer: Timer[F]
  )
    extends Subscriber[A]
  {
    private[this] val result = AsyncVar.empty[Iterant[F, A]]
    private[this] var subscription: Subscription = _
    private[this] var offset = 0
    private[this] var buffer: Array[Any] =
      if (bufferSize == 1) null else new Array(bufferSize)

    private[this] val take: F[Iterant[F, A]] = F.async[Iterant[F, A]] { cb =>
      result.unsafeTake(Callback.fromAttempt(cb)) match {
        case null => ()
        case a => cb(Right(a))
      }
    } <* timer.shift

    val generate = Scope(
      F.unit,
      take.map(_ ++ take),
      _ => F.delay {
        if (subscription ne null)
          subscription.cancel()
        subscription = null
      }
    )

    def onSubscribe(s: Subscription): Unit = {
      subscription = s
      s.request(bufferSize)
    }

    def onNext(t: A): Unit = {
      if (bufferSize == 1) {
        result.put(Next(t, take))
          .foreach(_ =>
            if (subscription ne null) {
              subscription.request(1)
            }
          )(immediate)
      } else {
        buffer(offset) = t
        offset += 1
        if (offset == bufferSize) {
          val batch = Batch.fromArray(buffer).asInstanceOf[Batch[A]]
          offset = 0
          buffer = new Array(bufferSize)
          result.put(NextBatch(batch, take))
            .foreach(_ =>
              if (subscription ne null) {
                subscription.request(bufferSize)
              })(immediate)
        }
      }
    }

    private def completeWith(iterant: Iterant[F, A]) = {
      if (offset > 0) {
        val batch = Batch.fromArray(buffer, 0, offset).asInstanceOf[Batch[A]]
        result.put(NextBatch(batch, F.pure(iterant)))
      } else {
        result.put(iterant)
      }
    }

    def onError(t: Throwable): Unit = {
      completeWith(Halt(Some(t)))
    }

    def onComplete(): Unit = {
      completeWith(Halt(None))
    }
  }

  def apply[F[_]: Async: Timer, A](publisher: Publisher[A], strategy: ReactivePullStrategy): Iterant[F, A] =
    Iterant.suspend[F, A] {
      strategy match {
        case ReactivePullStrategy.Batched(size) =>
          val unfold = new UnfoldSubscriber[F, A](size)
          /*_*/publisher.subscribe(unfold)/*_*/
          unfold.generate
      }
    }
}
