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
import monix.execution.Listener
import monix.execution.atomic.Atomic
import monix.execution.rstreams.ReactivePullStrategy.{FixedWindow, StopAndWait}
import monix.execution.rstreams.{ReactivePullStrategy, SingleAssignSubscription}
import monix.tail.Iterant
import monix.tail.Iterant.{Halt, Last, Next, NextBatch, Scope}
import monix.tail.batches.Batch
import org.reactivestreams.{Publisher, Subscriber, Subscription}

private[tail] object IterantFromReactivePublisher2 {
  /**
    * Implementation for `Iterant.fromReactivePublisher`.
    */
  def apply[F[_], A](pub: Publisher[A], strategy: ReactivePullStrategy)
    (implicit F: Async[F]): Iterant[F, A] = {

    val acquire =
      F.delay {
        val out = strategy match {
          case StopAndWait =>
            new StopAndWaitSubscriber[F, A]
          case FixedWindow(size) =>
            new FixedWindowSubscriber[F, A](size)
        }
        pub.subscribe(out)
        out
      }

    Scope[F, BaseIterantSubscriber[F, A], A](
      acquire,
      _.generate,
      (out, _) => F.delay(out.cancel()))
  }

  private final class StopAndWaitSubscriber[F[_], A](implicit F: Async[F])
    extends BaseIterantSubscriber[F, A] {

    def onNext(elem: A): Unit =
      put(Next(elem, generate))

    def onError(ex: Throwable): Unit =
      put(Iterant.raiseError(ex))

    def onComplete(): Unit =
      put(Iterant.empty)
  }

  private final class FixedWindowSubscriber[F[_], A](bufferSize: Int)
    (implicit F: Async[F])
    extends BaseIterantSubscriber[F, A] {

    private[this] var buffer = new Array[Any](bufferSize)
    private[this] var offset = 0

    def onNext(elem: A): Unit = {
      buffer(offset) = elem
      offset += 1
      if (offset == buffer.length) {
        put(NextBatch(getAndResetBuffer(), generate))
      }
    }

    def onError(ex: Throwable): Unit =
      onFinish(Iterant.raiseError(ex))

    def onComplete(): Unit =
      onFinish(Iterant.empty)

    private def onFinish(halt: Iterant[F, A]) = {
      if (offset == 0)
        put(halt)
      else
        put(NextBatch(getAndResetBuffer(), F.pure(halt)))
    }

    private def getAndResetBuffer() = {
      val b = buffer
      buffer = new Array(bufferSize)
      offset = 0
      Batch.fromArray(b).asInstanceOf[Batch[A]]
    }
  }

  private abstract class BaseIterantSubscriber[F[_], A](implicit F: Async[F])
    extends Subscriber[A] {

    protected val sub = SingleAssignSubscription()
    private[this] val state = Atomic(Empty : State[F, A])

    final val generate: F[Iterant[F, A]] =
      F.async { cb =>
        sub.request(1)
        take(cb)
      }

    private object listener extends Listener[Iterant[F, A]] {
      var ref: Either[Throwable, Iterant[F, A]] => Unit = _

      def onValue(value: Iterant[F, A]): Unit =
        ref(Right(value))
    }

    protected final def take(cb: Either[Throwable, Iterant[F, A]] => Unit): Unit =
      state.get match {
        case Empty =>
          listener.ref = cb
          val update = Take(listener)
          if (!state.compareAndSet(Empty, update)) take(cb)
        case Put(fa) =>
          cb(Right(fa))
        case Take(_) =>
          cb(Left(new IllegalStateException("Back-pressure contract violation!")))
      }

    protected final def put(fa: Iterant[F, A]): Unit =
      state.get match {
        case Empty =>
          if (!state.compareAndSet(Empty, Put(fa))) put(fa)
        case Take(cb) =>
          cb(Right(fa))
        case current @ Put(node) =>
          val update = node match {
            case Next(item, `generate`) =>
              fa match {
                case Halt(None) => Last(item)
                case _ => Next(item, F.pure(fa))
              }
            case NextBatch(batch, `generate`) =>
              NextBatch(batch, F.pure(fa))
            case other =>
              // $COVERAGE-OFF$
              throw new IllegalStateException(s"Invalid state in put(): $other")
              // $COVERAGE-ON$
          }
          if (!state.compareAndSet(current, Put(update)))
            put(fa)
      }

    final def onSubscribe(s: Subscription): Unit =
      sub := s

    final def cancel(): Unit =
      sub.cancel()
  }

  private sealed abstract class State[+F[_], +A]

  private case object Empty
    extends State[Nothing, Nothing]

  private final case class Put[F[_], A](
    fa: Iterant[F, A])
    extends State[F, A]

  private final case class Take[F[_], A](
    cb: Listener[Iterant[F, A]])
    extends State[F, A]
}
