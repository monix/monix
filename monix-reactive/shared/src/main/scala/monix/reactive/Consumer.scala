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

import cats.effect.Effect
import monix.eval.{Callback, Task}
import monix.execution.cancelables.AssignableCancelable
import monix.execution.{Cancelable, Scheduler}
import monix.reactive.internal.consumers._
import monix.reactive.observers.Subscriber

/** The `Consumer` is a specification of how to consume an observable.
  *
  * It is a factory of subscribers with a completion callback attached,
  * being effectively a way to transform observables into
  * [[monix.eval.Task tasks]] for less error prone consuming of streams.
  */
abstract class Consumer[-In, +R] extends ((Observable[In]) => Task[R])
  with Serializable { self =>

  /** Builds a new [[monix.reactive.observers.Subscriber Subscriber]]
    * that can be subscribed to an [[Observable]] for consuming a stream,
    * with a callback that should eventually get called with a materialized
    * result.
    *
    * Notes:
    *
    *  - calling the callback must obey the contract for the
    *    [[monix.eval.Callback Callback]] type
    *  - the given callback should always get called, unless the
    *    upstream gets canceled
    *  - the given callback can be called when the subscriber is
    *    finished processing, but not necessarily
    *  - if the given callback isn't called after the subscriber is
    *    done processing, then the `Task` returned by [[apply]]
    *    loses the ability to cancel the stream, as that `Task` will
    *    complete before the stream is finished
    *
    * @param cb is the [[monix.eval.Callback Callback]] that will get
    *        called once the created subscriber is finished.
    * @param s is the [[monix.execution.Scheduler Scheduler]] that will
    *        get used for subscribing to the source observable and to
    *        process the events.
    *
    * @return a new subscriber that can be used to consume observables.
    */
  def createSubscriber(cb: Callback[R], s: Scheduler): (Subscriber[In], AssignableCancelable)

  /** Given a source [[Observable]], convert it into a [[monix.eval.Task Task]]
    * by piggybacking on [[createSubscriber]].
    */
  final def apply(source: Observable[In]): Task[R] =
    Task.create[R] { (scheduler, cb) =>
      val (out, consumerSubscription) = createSubscriber(cb, scheduler)
      // Start consuming the stream
      val sourceSubscription = source.subscribe(out)
      // Assign the observable subscription to our assignable,
      // thus the subscriber can cancel its subscription
      consumerSubscription := sourceSubscription
      // We might not return the assignable returned by `createSubscriber`
      // because it might be a dummy
      if (consumerSubscription.isInstanceOf[Cancelable.IsDummy])
        sourceSubscription
      else
        consumerSubscription
    }

  /** Given a contravariant mapping function, transform
    * the source consumer by transforming the input.
    */
  final def contramap[In2](f: In2 => In): Consumer[In2, R] =
    new ContraMapConsumer[In2,In,R](self, f)

  /** Given a function that transforms the input stream, uses it
    * to transform the source consumer into one that accepts events
    * of the type specified by the transformation function.
    */
  final def transformInput[In2](f: Observable[In2] => Observable[In]): Consumer[In2, R] =
    new TransformInputConsumer[In2,In,R](self, f)

  /** Given a mapping function, when consuming a stream,
    * applies the mapping function to the final result,
    * thus modifying the output of the source consumer.
    *
    * Note that for applying the mapping function an
    * asynchronous boundary is forced, otherwise it could
    * trigger a stack overflow exception. For more efficient
    * mapping of the result, it's probably better to `map`
    * the resulting `Task` on [[Observable.consumeWith]].
    *
    * @see [[mapTask]] for a variant that can map the output
    *      to a `Task` that can be processed asynchronously.
    */
  final def map[R2](f: R => R2): Consumer[In, R2] =
    new MapConsumer[In,R,R2](self, f)

  /** Given a mapping function, when consuming a stream,
    * applies the mapping function to the final result,
    * thus modifying the output of the source consumer.
    *
    * The mapping function returns results using a generic `F[_]`
    * data type that must implement the `cats.effect.Effect` type
    * class. Examples of such classes are `cats.effect.IO` and
    * [[monix.eval.Task]], thus being able to do asynchronous
    * processing.
    *
    * See [[mapTask]] for the version that's specialized on `Task`.
    */
  final def mapEval[F[_], R2](f: R => F[R2])(implicit F: Effect[F]): Consumer[In, R2] =
    new MapTaskConsumer[In,R,R2](self, r => Task.fromEffect(f(r))(F))

  /** Given a mapping function, when consuming a stream,
    * applies the mapping function to the final result,
    * thus modifying the output of the source consumer.
    *
    * The mapping function returns a [[monix.eval.Task Task]]
    * that can be used to process results asynchronously.
    *
    * Note that for applying the mapping function an
    * asynchronous boundary is forced, otherwise it could
    * trigger a stack overflow exception. For more efficient
    * mapping of the result, it's probably better to `map`
    * the resulting `Task` on [[Observable.consumeWith]].
    *
    * See [[mapEval]] for the version that can work with any
    * data type that implements `cats.effect.Effect`.
    */
  final def mapTask[R2](f: R => Task[R2]): Consumer[In, R2] =
    new MapTaskConsumer[In,R,R2](self, f)
}

/** The companion object of [[Consumer]], defines consumer builders.
  *
  * @define loadBalanceDesc Creates a consumer that, when consuming
  *         the stream, will start multiple subscribers corresponding
  *         and distribute the load between them.
  *
  *         Once each subscriber emits a final result, this consumer will
  *         return a list of aggregated results.
  *
  *         Has the following rules:
  *
  *          - items are pushed on free subscribers, respecting their
  *            contract, each item being pushed to the first available
  *            subscriber in the queue
  *          - in case no free subscribers are available, then the
  *            source gets back-pressured until free subscribers are
  *            available
  *          - in case of `onComplete` or `onError`, all subscribers
  *            that are still active will receive the event
  *          - the `onSuccess` callback of individual subscribers is
  *            aggregated in a list buffer and once the aggregate contains
  *            results from all subscribers, the load-balancing consumer
  *            will emit the aggregate
  *          - the `onError` callback triggered by individual subscribers will
  *            signal that error upstream and cancel the streaming for
  *            every other subscriber
  *          - in case any of the subscribers cancels its subscription
  *            (either returning `Stop` in `onNext` or canceling its assigned
  *             cancelable), it gets excluded from the pool of active
  *            subscribers, but the other active subscribers will still
  *            receive notifications
  *          - if all subscribers canceled (either by returning `Stop`
  *            or by canceling their assignable cancelable reference),
  *            then streaming stops as well
  *
  *         In other words the `Task`, created by applying this consumer to
  *         an observable, will complete once all the subscribers emit a result
  *         or as soon as an error happens.
  *
  * @define loadBalanceReturn a list of aggregated results that
  *         were computed by all of the subscribers as their result
  */
object Consumer {
  /** Creates a [[Consumer]] out of the given function.
    *
    * The function returns an [[Observer]] and takes as input:
    *
    *  - a [[monix.execution.Scheduler Scheduler]] for any asynchronous
    *    execution needs the returned observer might have
    *  - a [[monix.execution.Cancelable Cancelable]] that can be used for
    *    concurrently canceling the stream (in addition to being able to
    *    return `Stop` from `onNext`)
    *  - a [[monix.eval.Callback Callback]] that must be called to signal
    *    the final result, after the observer finished processing the
    *    stream, or an error if the processing finished in error
    *
    * @param f is the input function with an injected `Scheduler`,
    *        `Cancelable`, `Callback` and that returns an `Observer`
    */
  def create[In,Out](f: (Scheduler, Cancelable, Callback[Out]) => Observer[In]): Consumer[In,Out] =
    new CreateConsumer[In,Out](f)

  /** Given a function taking a `Scheduler` and returning an [[Observer]],
    * builds a consumer from it.
    *
    * You can use the `Scheduler` as the execution context, for working
    * with `Future`, for forcing asynchronous boundaries or for executing
    * tasks with a delay.
    */
  def fromObserver[In](f: Scheduler => Observer[In]): Consumer[In, Unit] =
    new FromObserverConsumer[In](f)

  /** A consumer that immediately cancels its upstream after subscription. */
  def cancel[A]: Consumer.Sync[A, Unit] =
    CancelledConsumer

  /** A consumer that triggers an error and immediately cancels its
    * upstream after subscription.
    */
  def raiseError[In, R](ex: Throwable): Consumer.Sync[In,R] =
    new RaiseErrorConsumer(ex)

  /** Given a fold function and an initial state value, applies the
    * fold function to every element of the stream and finally signaling
    * the accumulated value.
    *
    * @param initial is a lazy value that will be fed at first
    *        in the fold function as the initial state.
    * @param f is the function that calculates a new state on each
    *        emitted value by the stream, for accumulating state
    */
  def foldLeft[S,A](initial: => S)(f: (S,A) => S): Consumer.Sync[A,S] =
    new FoldLeftConsumer[A,S](initial _, f)

  /** Given a fold function and an initial state value, applies the
    * fold function to every element of the stream and finally signaling
    * the accumulated value.
    *
    * The given fold function returns an `F[A]` value, where `F` is
    * any data type that implements `cats.effect.Effect` (e.g. `Task`,
    * `Coeval`), thus able to do asynchronous processing, with
    * ordering of calls being guaranteed.
    *
    * @param initial is a lazy value that will be fed at first
    *        in the fold function as the initial state.
    *
    * @param f is the function that calculates a new state on each
    *        emitted value by the stream, for accumulating state,
    *        returning a `F[A]` capable of lazy or asynchronous
    *        execution.
    */
  def foldLeftEval[F[_], S, A](initial: => S)(f: (S, A) => F[S])(implicit F: Effect[F]): Consumer[A, S] =
    new FoldLeftTaskConsumer[A,S](initial _, (s, a) => Task.fromEffect(f(s, a))(F))

  /** Given a fold function and an initial state value, applies the
    * fold function to every element of the stream and finally signaling
    * the accumulated value.
    *
    * The given fold function returns a `Task` that can execute an
    * asynchronous operation, with ordering of calls being guaranteed.
    *
    * @param initial is a lazy value that will be fed at first
    *        in the fold function as the initial state.
    * @param f is the function that calculates a new state on each
    *        emitted value by the stream, for accumulating state,
    *        returning a `Task` capable of asynchronous execution.
    */
  def foldLeftTask[S,A](initial: => S)(f: (S,A) => Task[S]): Consumer[A,S] =
    new FoldLeftTaskConsumer[A,S](initial _, f)

  /** A consumer that will produce the first streamed value on
    * `onNext` after which the streaming gets cancelled.
    *
    * In case the stream is empty and so no `onNext` happen before
    * `onComplete`, then the a `NoSuchElementException` will get
    * triggered.
    */
  def head[A]: Consumer.Sync[A, A] =
    new HeadConsumer[A]

  /** A consumer that will produce the first streamed value on
    * `onNext` after which the streaming gets cancelled.
    *
    * In case the stream is empty and so no `onNext` happen before
    * `onComplete`, then the a `NoSuchElementException` will get
    * triggered.
    */
  def headOption[A]: Consumer.Sync[A, Option[A]] =
    new HeadOptionConsumer[A]

  /** A consumer that will produce a [[Notification]] of the first value
    * received (`onNext`, `onComplete` or `onError`), after which the
    * streaming gets cancelled.
    *
    *  - [[Notification.OnNext OnNext]] will be signaled on the first `onNext`
    *    event if it happens and the streaming will be stopped by `Stop`.
    *  - [[Notification.OnComplete OnComplete]] will be signaled if the stream
    *    was empty and thus completed without any `onNext`.
    *  - [[Notification.OnError OnError]] will be signaled if the stream
    *    was completed in error before the first `onNext` happened.
    */
  def firstNotification[A]: Consumer.Sync[A, Notification[A]] =
    new FirstNotificationConsumer[A]

  /** A simple consumer that consumes all elements of the
    * stream and then signals its completion.
    */
  def complete[A]: Consumer.Sync[A, Unit] =
    CompleteConsumer

  /** Builds a consumer that will consume the stream, applying the given
    * function to each element and then finally signaling its completion.
    *
    * @param cb is the function that will be called for each element
    */
  def foreach[A](cb: A => Unit): Consumer.Sync[A, Unit] =
    new ForeachConsumer[A](cb)

  /** Builds a consumer that will consume the stream, applying the given
    * function to each element and then finally signaling its completion.
    *
    * The given callback function returns a `F[A]` value that can
    * execute an asynchronous operation, with ordering of calls being
    * guaranteed, given that the `F[_]` data type is any type that
    * implements `cats.effect.Effect` (e.g. `Task`, `IO`).
    *
    * @param cb is the function that will be called for each element
    */
  def foreachEval[F[_], A](cb: A => F[Unit])(implicit F: Effect[F]): Consumer[A, Unit] =
    foreachTask(a => Task.fromEffect(cb(a))(F))

  /** Builds a consumer that will consume the stream, applying the given
    * function to each element and then finally signaling its completion.
    *
    * The given callback function returns a `Task` that can execute an
    * asynchronous operation, with ordering of calls being guaranteed.
    *
    * @param cb is the function that will be called for each element
    */
  def foreachTask[A](cb: A => Task[Unit]): Consumer[A, Unit] =
    new ForeachAsyncConsumer[A](cb)

  /** Builds a consumer that will consume the stream, applying the given
    * function to each element, in parallel, then finally signaling its
    * completion.
    *
    * @param parallelism is the maximum number of (logical) threads to use
    * @param cb is the function that will be called for each element
    */
  def foreachParallel[A](parallelism: Int)(cb: A => Unit): Consumer[A, Unit] =
    loadBalance(parallelism, foreach(cb)).map(_ => ())

  /** Builds a consumer that will consume the stream, applying the given
    * function to each element, in parallel, then finally signaling its
    * completion.
    *
    * The given callback function returns a `Task` that can execute an
    * asynchronous operation, with ordering of calls being guaranteed
    * per subscriber.
    *
    * @param parallelism is the maximum number of (logical) threads to use
    * @param cb is the function that will be called for each element
    */
  def foreachParallelTask[A](parallelism: Int)(cb: A => Task[Unit]): Consumer[A, Unit] =
    loadBalance(parallelism, foreachTask(cb)).map(_ => ())

  /** $loadBalanceDesc
    *
    * @param parallelism is the number of subscribers that will get
    *        initialized to process incoming events in parallel.
    * @param consumer is the subscriber factory that will initialize
    *        all needed subscribers, in number equal to the specified
    *        parallelism and thus that will be fed in parallel
    *
    * @return $loadBalanceReturn
    */
  def loadBalance[A,R](parallelism: Int, consumer: Consumer[A,R]): Consumer[A, List[R]] =
    new LoadBalanceConsumer[A,R](parallelism, Array(consumer))

  /** $loadBalanceDesc
    *
    * @param consumers is a list of consumers that will initialize
    *        the subscribers that will process events in parallel,
    *        with the parallelism factor being equal to the number
    *        of consumers specified in this list.
    *
    * @return $loadBalanceReturn
    */
  def loadBalance[A,R](consumers: Consumer[A,R]*): Consumer[A, List[R]] =
    new LoadBalanceConsumer[A,R](consumers.length, consumers.toArray)

  /** Defines a synchronous [[Consumer]] that builds
    * [[monix.reactive.observers.Subscriber.Sync synchronous subscribers]].
    */
  trait Sync[-In, +R] extends Consumer[In, R] {
    override def createSubscriber(cb: Callback[R], s: Scheduler): (Subscriber.Sync[In], AssignableCancelable)
  }
}
