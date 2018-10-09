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

import cats.effect.Async
import monix.execution.Callback
import monix.execution.atomic.Atomic
import monix.execution.rstreams.SingleAssignSubscription
import monix.tail.Iterant
import monix.tail.Iterant.{Last, Next, NextBatch, Scope}
import monix.tail.batches.Batch
import org.reactivestreams.{Publisher, Subscriber, Subscription}

import scala.annotation.tailrec
import scala.collection.immutable.Queue

private[tail] object IterantFromReactivePublisher {
  /**
    * Implementation for `Iterant.fromReactivePublisher`.
    */
  def apply[F[_], A](pub: Publisher[A], requestCount: Int)
    (implicit F: Async[F]): Iterant[F, A] = {

    if (requestCount < 1) {
      Iterant.raiseError(new IllegalArgumentException("requestSize must be greater than 1"))
    } else {
      val acquire =
        F.delay {
          val out = new IterantSubscriber[F, A](requestCount)
          pub.subscribe(out)
          out
        }

      Scope[F, IterantSubscriber[F, A], A](
        acquire,
        _.generate,
        (out, _) => F.delay(out.cancel()))
    }
  }

  private final class IterantSubscriber[F[_], A](bufferSize: Int)
    (implicit F: Async[F])
    extends Subscriber[A] {

    private[this] val sub = SingleAssignSubscription()
    private[this] val state = Atomic(Empty : State[F, A])
    private[this] var requested = 0

    val generate: F[Iterant[F, A]] =
      F.async { cb =>
        if (requested <= 0) {
          requested = bufferSize
          sub.request(requested)
        }
        take(cb)
      }

    @tailrec def onNext(a: A): Unit =
      state.get match {
        case current @ Enqueue(queue, length) =>
          if (!state.compareAndSet(current, Enqueue(queue.enqueue(a), length + 1)))
            onNext(a)

        case current @ Take(cb) =>
          if (!state.compareAndSet(current, Empty))
            onNext(a)
          else {
            // WARNING: multi-threading might be an issue!
            if (requested < Int.MaxValue) requested -= 1
            cb.onSuccess(Next(a, generate))
          }

        case Stop(_) =>
          throw new IllegalStateException("onComplete/onError after onNext is not allowed")
      }

    @tailrec def finish(fa: Iterant[F, A]): Unit =
      state.get match {
        case current @ Enqueue(queue, length) =>
          val update: Iterant[F, A] = length match {
            case 0 => fa
            case 1 =>
              val elem = queue.dequeue._1
              if (fa == Iterant.empty) Last(elem)
              else Next(elem, F.pure(fa))
            case _ =>
              NextBatch[F, A](Batch.fromSeq(queue), F.pure(fa))
          }

          if (!state.compareAndSet(current, Stop(update))) {
            finish(fa)
          }

        case Take(cb) =>
          cb.onSuccess(fa)

        case Stop(_) =>
          throw new IllegalStateException("was already completed")
      }

    def onError(ex: Throwable): Unit =
      finish(Iterant.raiseError(ex))

    def onComplete(): Unit =
      finish(Iterant.empty)

    private object listener extends Callback[Nothing, Iterant[F, A]] {
      var ref: Either[Throwable, Iterant[F, A]] => Unit = _

      def onError(e: Nothing): Unit = ()
      def onSuccess(value: Iterant[F, A]): Unit =
        ref(Right(value))
    }

    private def take(cb: Either[Throwable, Iterant[F, A]] => Unit): Unit =
      state.get match {
        case current @ Enqueue(queue, length) =>
          if (length == 0) {
            listener.ref = cb
            val update = Take(listener)
            if (!state.compareAndSet(current, update)) take(cb)
          }
          else if (!state.compareAndSet(current, Empty)) {
            take(cb)
          }
          else {
            if (requested < Int.MaxValue) requested -= length
            val stream = length match {
              case 1 => Next(queue.dequeue._1, generate)
              case _ => NextBatch(Batch.fromSeq(queue), generate)
            }
            cb(Right(stream))
          }

        case Stop(fa) =>
          cb(Right(fa))

        case Take(_) =>
          cb(Left(new IllegalStateException("Back-pressure contract violation!")))
      }

    def onSubscribe(s: Subscription): Unit =
      sub := s

    def cancel(): Unit =
      sub.cancel()
  }

  private sealed abstract class State[+F[_], +A]

  private final case class Stop[F[_], A](
    fa: Iterant[F, A])
    extends State[F, A]

  private final case class Enqueue[F[_], A](
    queue: Queue[A],
    length: Int)
    extends State[F, A]

  private final case class Take[F[_], A](
    cb: Callback[Nothing, Iterant[F, A]])
    extends State[F, A]

  private val Empty = Enqueue(Queue.empty, 0)
}
