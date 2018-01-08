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

package monix.reactive

import monix.execution.Scheduler
import monix.execution.misc.NonFatal
import monix.reactive.Observable.Operator
import monix.reactive.OverflowStrategy.{Synchronous, Unbounded}
import monix.reactive.Pipe.{LiftedPipe, TransformedPipe}
import monix.reactive.observers.{BufferedSubscriber, Subscriber}
import monix.reactive.subjects._

/** Represents a factory for an input/output channel for
  * broadcasting input to multiple subscribers.
  */
abstract class Pipe[I, +O] extends Serializable {
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
    */
  def concurrent(implicit s: Scheduler): (Observer.Sync[I], Observable[O]) =
    concurrent(Unbounded)(s)

  /** Returns an input/output pair with an input that can be
    * used synchronously and concurrently (without back-pressure or
    * multi-threading issues) to push signals to multiple subscribers.
    *
    * @param strategy is the [[OverflowStrategy]] used for the underlying
    *                 multi-producer/single-consumer buffer
    */
  def concurrent(strategy: Synchronous[I])(implicit s: Scheduler): (Observer.Sync[I], Observable[O]) = {
    val (in,out) = multicast(s)
    val buffer = BufferedSubscriber.synchronous[I](Subscriber(in, s), strategy)
    (buffer, out)
  }

  // provides observable-like operators
  final def liftByOperator[B](op: Operator[O, B]): Pipe[I, B] =
    new LiftedPipe(this, op)

  /** Transforms the source using the given transformer function. */
  final def transform[B](f: Observable[O] => Observable[B]): Pipe[I, B] =
    new TransformedPipe(this, f)
}

object Pipe {
  /** Given a [[MulticastStrategy]] returns the corresponding [[Pipe]]. */
  def apply[A](strategy: MulticastStrategy[A]): Pipe[A,A] =
    strategy match {
      case MulticastStrategy.Publish =>
        Pipe.publish[A]
      case MulticastStrategy.Behavior(initial) =>
        Pipe.behavior[A](initial)
      case MulticastStrategy.Async =>
        Pipe.async[A]
      case MulticastStrategy.Replay(initial) =>
        Pipe.replay[A](initial)
      case MulticastStrategy.ReplayLimited(capacity, initial) =>
        Pipe.replayLimited[A](capacity, initial)
    }

  /** Subject recipe for building
    * [[monix.reactive.subjects.PublishSubject PublishSubject]] instances.
    */
  def publish[A]: Pipe[A,A] =
    new Pipe[A,A] {
      def unicast: (Observer[A], Observable[A]) = {
        val p = PublishSubject[A]()
        (p,p)
      }

      override def multicast(implicit s: Scheduler): (Observer[A], Observable[A]) =
        unicast
    }

  /** Subject recipe for building
    * [[monix.reactive.subjects.PublishToOneSubject PublishToOneSubject]]
    * instances.
    */
  def publishToOne[A]: Pipe[A,A] =
    new Pipe[A,A] {
      def unicast: (Observer[A], Observable[A]) = {
        val p = PublishToOneSubject[A]()
        (p,p)
      }
    }

  /** Subject recipe for building
    * [[monix.reactive.subjects.BehaviorSubject BehaviorSubject]]
    * instances.
    */
  def behavior[A](initial: A): Pipe[A,A] =
    new Pipe[A,A] {
      def unicast: (Observer[A], Observable[A]) = {
        val p = BehaviorSubject[A](initial)
        (p,p)
      }

      override def multicast(implicit s: Scheduler): (Observer[A], Observable[A]) =
        unicast
    }

  /** Subject recipe for building
    * [[monix.reactive.subjects.AsyncSubject AsyncSubject]] instances.
    */
  def async[A]: Pipe[A,A] =
    new Pipe[A,A] {
      def unicast: (Observer[A], Observable[A]) = {
        val p = AsyncSubject[A]()
        (p,p)
      }

      override def multicast(implicit s: Scheduler): (Observer[A], Observable[A]) =
        unicast
    }

  /** Subject recipe for building unbounded
    * [[monix.reactive.subjects.ReplaySubject monix.reactive.subjects.]] instances.
    */
  def replay[A]: Pipe[A,A] =
    replay(Seq.empty)

  /** Subject recipe for building unbounded
    * [[monix.reactive.subjects.ReplaySubject ReplaySubject]]
    * instances.
    *
    * @param initial is an initial sequence of elements that will be pushed
    *        to subscribers before any elements emitted by the source.
    */
  def replay[A](initial: Seq[A]): Pipe[A,A] =
    new Pipe[A,A] {
      def unicast: (Observer[A], Observable[A]) = {
        val p = ReplaySubject.create[A](initial)
        (p,p)
      }

      override def multicast(implicit s: Scheduler): (Observer[A], Observable[A]) =
        unicast
    }

  /** Subject recipe for building
    * [[monix.reactive.subjects.ReplaySubject ReplaySubject]] instances
    * with a maximum `capacity` (after which old items start being dropped).
    *
    * @param capacity indicates the minimum capacity of the underlying buffer,
    *        with the implementation being free to increase it.
    */
  def replayLimited[A](capacity: Int): Pipe[A,A] =
    replayLimited(capacity, Seq.empty)

  /** Subject recipe for building
    * [[monix.reactive.subjects.ReplaySubject ReplaySubject]] instances
    * with a maximum `capacity` (after which old items start being dropped).
    *
    * @param capacity indicates the minimum capacity of the underlying buffer,
    *        with the implementation being free to increase it.
    * @param initial is an initial sequence of elements that will be pushed
    *        to subscribers before any elements emitted by the source.
    */
  def replayLimited[A](capacity: Int, initial: Seq[A]): Pipe[A,A] =
    new Pipe[A,A] {
      def unicast: (Observer[A], Observable[A]) = {
        val p = ReplaySubject.createLimited[A](capacity, initial)
        (p,p)
      }

      override def multicast(implicit s: Scheduler): (Observer[A], Observable[A]) =
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

  private final class TransformedPipe[I,+O,+U](self: Pipe[I, O], f: Observable[O] => Observable[U])
    extends Pipe[I,U] {

    override def unicast: (Observer[I], Observable[U]) = {
      try {
        val (in,out) = self.unicast
        (in, f(out))
      } catch {
        case e if NonFatal(e) =>
          (Observer.stopped, Observable.raiseError(e))
      }
    }

    override def multicast(implicit s: Scheduler): (Observer[I], Observable[U]) = {
      try {
        val (in,out) = self.multicast(s)
        (in, f(out))
      } catch {
        case e if NonFatal(e) =>
          (Observer.stopped, Observable.raiseError(e))
      }
    }
  }
}