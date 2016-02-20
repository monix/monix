/*
 * Copyright (c) 2014-2016 by its authors. Some rights reserved.
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

package monix.streams

import monix.execution.Scheduler
import monix.streams.OverflowStrategy.{Evicted, Synchronous}
import monix.streams.Pipe.LiftedPipe
import monix.streams.broadcast._
import monix.streams.ObservableLike.Operator
import monix.streams.observers.{Subscriber, BufferedSubscriber, SyncObserver}
import scala.language.reflectiveCalls

/** Represents a factory for an input/output channel for
  * broadcasting input to multiple subscribers.
  */
abstract class Pipe[I, +O]
  extends ObservableLike[O, ({type λ[+α] = Pipe[I, α]})#λ] {

  /** Returns an input/output pair that can be used to
    * push input to a single subscriber.
    *
    * This means that the returned observable should be subscribed
    * at most once, otherwise the behavior is undefined.
    *
    * @see [[multicast]] for creating a safe observable that can
    *     be subscribed many times.
    */
  def unicast: (Observer[I], Observable[O])

  /** Returns an input/output pair that can be used to
    * push input to multiple subscribers.
    */
  def multicast(implicit s: Scheduler): (Observer[I], Observable[O]) = {
    val (in,out) = unicast
    val proc = PublishProcessor[O]()
    out.unsafeSubscribeFn(proc)
    (in,proc)
  }

  /** Returns an input/output pair with an input that can be
    * used synchronously and concurrently (without back-pressure or
    * multi-threading issues) to push signals to multiple subscribers.
    *
    * @param strategy is the [[OverflowStrategy]] used for the underlying
    *                 multi-producer/single-consumer buffer
    */
  def concurrent(strategy: Synchronous)(implicit s: Scheduler): (SyncObserver[I], Observable[O]) = {
    val (in,out) = multicast(s)
    val buffer = BufferedSubscriber.synchronous[I](Subscriber(in, s), strategy, null)
    (buffer, out)
  }

  /** Returns an input/output pair with an input that can be
    * used synchronously and concurrently (without back-pressure or
    * multi-threading issues) to push signals to multiple subscribers.
    *
    * @param strategy is the [[OverflowStrategy]] used for the underlying
    *                 multi-producer/single-consumer buffer
    * @param onOverflow is a callback called in case of dropped signals
    */
  def concurrent(strategy: Evicted, onOverflow: Long => I)
    (implicit s: Scheduler): (SyncObserver[I], Observable[O]) = {

    val (in,out) = multicast(s)
    val buffer = BufferedSubscriber.synchronous[I](Subscriber(in, s), strategy, onOverflow)
    (buffer, out)
  }

  // provides observable-like operators
  override def lift[B](op: Operator[O, B]): Pipe[I, B] =
    new LiftedPipe(this, op)
}

object Pipe {
  /** Processor recipe for building [[PublishProcessor]] instances. */
  def publish[T](): Pipe[T,T] =
    new Pipe[T,T] {
      def unicast: (Observer[T], Observable[T]) = {
        val p = PublishProcessor[T]()
        (p,p)
      }

      override def multicast(implicit s: Scheduler): (Observer[T], Observable[T]) =
        unicast
    }

  /** Processor recipe for building [[BehaviorProcessor]] instances. */
  def behavior[T](initial: => T): Pipe[T,T] =
    new Pipe[T,T] {
      def unicast: (Observer[T], Observable[T]) = {
        val p = BehaviorProcessor[T](initial)
        (p,p)
      }

      override def multicast(implicit s: Scheduler): (Observer[T], Observable[T]) =
        unicast
    }

  /** Processor recipe for building [[AsyncProcessor]] instances. */
  def async[T](): Pipe[T,T] =
    new Pipe[T,T] {
      def unicast: (Observer[T], Observable[T]) = {
        val p = AsyncProcessor[T]()
        (p,p)
      }

      override def multicast(implicit s: Scheduler): (Observer[T], Observable[T]) =
        unicast
    }

  /** Processor recipe for building unbounded [[ReplayProcessor]] instances. */
  def replay[T](): Pipe[T,T] =
    new Pipe[T,T] {
      def unicast: (Observer[T], Observable[T]) = {
        val p = ReplayProcessor[T]()
        (p,p)
      }

      override def multicast(implicit s: Scheduler): (Observer[T], Observable[T]) =
        unicast
    }

  /** Processor recipe for building unbounded [[ReplayProcessor]] instances.
    *
    * @param initial is an initial sequence of elements that will be pushed
    *        to subscribers before any elements emitted by the source.
    */
  def replayPopulated[T](initial: => Seq[T]): Pipe[T,T] =
    new Pipe[T,T] {
      def unicast: (Observer[T], Observable[T]) = {
        val p = ReplayProcessor[T](initial:_*)
        (p,p)
      }

      override def multicast(implicit s: Scheduler): (Observer[T], Observable[T]) =
        unicast
    }

  /** Processor recipe for building [[ReplayProcessor]] instances
    * with a maximum `capacity` (after which old items start being dropped).
    *
    * @param capacity indicates the minimum capacity of the underlying buffer,
    *        with the implementation being free to increase it.
    */
  def replaySized[T](capacity: Int): Pipe[T,T] =
    new Pipe[T,T] {
      def unicast: (Observer[T], Observable[T]) = {
        val p = ReplayProcessor.createWithSize[T](capacity)
        (p,p)
      }

      override def multicast(implicit s: Scheduler): (Observer[T], Observable[T]) =
        unicast
    }

  private final class LiftedPipe[I,+O,+U](self: Pipe[I,O], op: Operator[O, U])
    extends Pipe[I,U] {

    def unicast: (Observer[I], Observable[U]) = {
      val (in,out) = self.unicast
      val outU = out.lift(op)
      (in, outU)
    }

    override def multicast(implicit s: Scheduler): (Observer[I], Observable[U]) = {
      val (in,out) = self.multicast(s)
      val outU = out.lift(op)
      (in, outU)
    }
  }
}