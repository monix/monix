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
import monix.streams.ObservableLike.{Operator, Transformer}
import monix.streams.OverflowStrategy.Synchronous
import monix.streams.Pipe.{LiftedPipe, TransformedPipe}
import monix.streams.observers.{BufferedSubscriber, Subscriber, SyncObserver}
import monix.streams.subjects._
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
    val proc = PublishSubject[O]()
    out.unsafeSubscribeFn(Subscriber(proc, s))
    (in,proc)
  }

  /** Returns an input/output pair with an input that can be
    * used synchronously and concurrently (without back-pressure or
    * multi-threading issues) to push signals to multiple subscribers.
    *
    * @param strategy is the [[OverflowStrategy]] used for the underlying
    *                 multi-producer/single-consumer buffer
    */
  def concurrent(strategy: Synchronous[I])(implicit s: Scheduler): (SyncObserver[I], Observable[O]) = {
    val (in,out) = multicast(s)
    val buffer = BufferedSubscriber.synchronous[I](Subscriber(in, s), strategy)
    (buffer, out)
  }

  // provides observable-like operators
  override def liftByOperator[B](op: Operator[O, B]): Pipe[I, B] =
    new LiftedPipe(this, op)

  /** Transforms the source using the given transformer function. */
  override def transform[B](transformer: Transformer[O, B]): Pipe[I, B] =
    new TransformedPipe(this, transformer)
}

object Pipe {
  /** Subject recipe for building [[PublishSubject]] instances. */
  def publish[T](): Pipe[T,T] =
    new Pipe[T,T] {
      def unicast: (Observer[T], Observable[T]) = {
        val p = PublishSubject[T]()
        (p,p)
      }

      override def multicast(implicit s: Scheduler): (Observer[T], Observable[T]) =
        unicast
    }

  /** Subject recipe for building [[PublishToOneSubject]] instances. */
  def publishToOne[T](): Pipe[T,T] =
    new Pipe[T,T] {
      def unicast: (Observer[T], Observable[T]) = {
        val p = PublishToOneSubject[T]()
        (p,p)
      }
    }

  /** Subject recipe for building [[BehaviorSubject]] instances. */
  def behavior[T](initial: => T): Pipe[T,T] =
    new Pipe[T,T] {
      def unicast: (Observer[T], Observable[T]) = {
        val p = BehaviorSubject[T](initial)
        (p,p)
      }

      override def multicast(implicit s: Scheduler): (Observer[T], Observable[T]) =
        unicast
    }

  /** Subject recipe for building [[AsyncSubject]] instances. */
  def async[T](): Pipe[T,T] =
    new Pipe[T,T] {
      def unicast: (Observer[T], Observable[T]) = {
        val p = AsyncSubject[T]()
        (p,p)
      }

      override def multicast(implicit s: Scheduler): (Observer[T], Observable[T]) =
        unicast
    }

  /** Subject recipe for building unbounded [[ReplaySubject]] instances. */
  def replay[T](): Pipe[T,T] =
    new Pipe[T,T] {
      def unicast: (Observer[T], Observable[T]) = {
        val p = ReplaySubject[T]()
        (p,p)
      }

      override def multicast(implicit s: Scheduler): (Observer[T], Observable[T]) =
        unicast
    }

  /** Subject recipe for building unbounded [[ReplaySubject]] instances.
    *
    * @param initial is an initial sequence of elements that will be pushed
    *        to subscribers before any elements emitted by the source.
    */
  def replayPopulated[T](initial: => Seq[T]): Pipe[T,T] =
    new Pipe[T,T] {
      def unicast: (Observer[T], Observable[T]) = {
        val p = ReplaySubject[T](initial:_*)
        (p,p)
      }

      override def multicast(implicit s: Scheduler): (Observer[T], Observable[T]) =
        unicast
    }

  /** Subject recipe for building [[ReplaySubject]] instances
    * with a maximum `capacity` (after which old items start being dropped).
    *
    * @param capacity indicates the minimum capacity of the underlying buffer,
    *        with the implementation being free to increase it.
    */
  def replaySized[T](capacity: Int): Pipe[T,T] =
    new Pipe[T,T] {
      def unicast: (Observer[T], Observable[T]) = {
        val p = ReplaySubject.createWithSize[T](capacity)
        (p,p)
      }

      override def multicast(implicit s: Scheduler): (Observer[T], Observable[T]) =
        unicast
    }

  private final class LiftedPipe[I,+O,+U](self: Pipe[I,O], op: Operator[O, U])
    extends Pipe[I,U] {

    def unicast: (Observer[I], Observable[U]) = {
      val (in,out) = self.unicast
      val outU = out.liftByOperator(op)
      (in, outU)
    }

    override def multicast(implicit s: Scheduler): (Observer[I], Observable[U]) = {
      val (in,out) = self.multicast(s)
      val outU = out.liftByOperator(op)
      (in, outU)
    }
  }

  private final class TransformedPipe[I,+O,+U](self: Pipe[I,O], f: Transformer[O, U])
    extends Pipe[I,U] {

    override def unicast: (Observer[I], Observable[U]) = {
      val (in,out) = self.unicast
      (in, f(out))
    }

    override def multicast(implicit s: Scheduler): (Observer[I], Observable[U]) = {
      val (in,out) = self.multicast(s)
      (in, f(out))
    }
  }
}