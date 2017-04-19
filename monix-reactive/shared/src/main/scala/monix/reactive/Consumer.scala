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

package monix.reactive

import monix.eval.Coeval.{Error, Now}
import monix.eval.{Callback, Task}
import monix.execution.Ack.{Continue, Stop}
import monix.execution.atomic.{Atomic, PaddingStrategy}
import monix.execution.cancelables.{AssignableCancelable, SingleAssignmentCancelable}
import monix.execution.misc.NonFatal
import monix.execution.{Ack, Cancelable, Scheduler}
import monix.reactive.Consumer.LoadBalanceConsumer.IndexedSubscriber
import monix.reactive.Consumer.{ContraMapConsumer, MapAsyncConsumer, MapConsumer}
import monix.reactive.observers.Subscriber

import scala.annotation.tailrec
import scala.collection.immutable.{BitSet, Queue}
import scala.collection.mutable.ListBuffer
import scala.concurrent.{Future, Promise}
import scala.util.{Failure, Success, Try}

/** The `Consumer` is a specification of how to consume an observable.
  *
  * It is a factory of subscribers with a completion callback attached,
  * being effectively a way to transform observables into
  * [[monix.eval.Task tasks]] for less error prone consuming of streams.
  */
trait Consumer[-In, +R] extends ((Observable[In]) => Task[R])
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
  def apply(source: Observable[In]): Task[R] =
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
  def contramap[In2](f: In2 => In): Consumer[In2, R] =
    new ContraMapConsumer[In2,In,R](self,f)

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
    * @see [[mapAsync]] for a variant that can map the output
    *     to a `Task` that can be processed asynchronously.
    */
  def map[R2](f: R => R2): Consumer[In, R2] =
    new MapConsumer[In,R,R2](self, f)

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
    */
  def mapAsync[R2](f: R => Task[R2]): Consumer[In, R2] =
    new MapAsyncConsumer[In,R,R2](self, f)
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
    * The given fold function returns a `Task` that can execute an
    * asynchronous operation, with ordering of calls being guaranteed.
    *
    * @param initial is a lazy value that will be fed at first
    *        in the fold function as the initial state.
    * @param f is the function that calculates a new state on each
    *        emitted value by the stream, for accumulating state,
    *        returning a `Task` capable of asynchronous execution.
    */
  def foldLeftAsync[S,A](initial: => S)(f: (S,A) => Task[S]): Consumer[A,S] =
    new FoldLeftAsyncConsumer[A,S](initial _, f)

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
    * The given callback function returns a `Task` that can execute an
    * asynchronous operation, with ordering of calls being guaranteed.
    *
    * @param cb is the function that will be called for each element
    */
  def foreachAsync[A](cb: A => Task[Unit]): Consumer[A, Unit] =
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
  def foreachParallelAsync[A](parallelism: Int)(cb: A => Task[Unit]): Consumer[A, Unit] =
    loadBalance(parallelism, foreachAsync(cb)).map(_ => ())

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

  /** Implementation for [[Consumer.create]]. */
  private final class CreateConsumer[-In,+Out](
    f: (Scheduler, Cancelable, Callback[Out]) => Observer[In])
    extends Consumer[In,Out] {

    def createSubscriber(cb: Callback[Out], s: Scheduler): (Subscriber[In], AssignableCancelable) = {
      val conn = SingleAssignmentCancelable()

      Try(f(s, conn, cb)) match {
        case Failure(ex) =>
          Consumer.raiseError(ex).createSubscriber(cb,s)

        case Success(out) =>
          val sub = Subscriber(out, s)
          (sub, conn)
      }
    }
  }

  /** Implementation for [[Consumer.contramap]]. */
  private final class ContraMapConsumer[In2, -In, +R]
    (source: Consumer[In, R], f: In2 => In)
    extends Consumer[In2, R] {

    def createSubscriber(cb: Callback[R], s: Scheduler): (Subscriber[In2], AssignableCancelable) = {
      val (out, c) = source.createSubscriber(cb, s)

      val out2 = new Subscriber[In2] {
        implicit val scheduler = out.scheduler
        // For protecting the contract
        private[this] var isDone = false

        def onError(ex: Throwable): Unit =
          if (!isDone) { isDone = true; out.onError(ex) }
        def onComplete(): Unit =
          if (!isDone) { isDone = true; out.onComplete() }

        def onNext(elem2: In2): Future[Ack] = {
          // Protects calls to user code from within the operator and
          // stream the error downstream if it happens, but if the
          // error happens because of calls to `onNext` or other
          // protocol calls, then the behavior should be undefined.
          var streamErrors = true
          try {
            val elem = f(elem2)
            streamErrors = false
            out.onNext(elem)
          } catch {
            case NonFatal(ex) if streamErrors =>
              onError(ex)
              Stop
          }
        }
      }

      (out2, c)
    }
  }

  /** Implementation for [[Consumer.map]]. */
  private final class MapConsumer[In, R, R2](source: Consumer[In,R], f: R => R2)
    extends Consumer[In, R2] {

    def createSubscriber(cb: Callback[R2], s: Scheduler): (Subscriber[In], AssignableCancelable) = {
      val cb1 = new Callback[R] {
        def onSuccess(value: R): Unit =
          s.execute(new Runnable {
            // Forcing an asynchronous boundary, otherwise
            // this isn't a safe operation.
            def run(): Unit = {
              var streamErrors = true
              try {
                val r2 = f(value)
                streamErrors = false
                cb.onSuccess(r2)
              } catch {
                case NonFatal(ex) =>
                  if (streamErrors) cb.onError(ex)
                  else s.reportFailure(ex)
              }
            }
          })

        def onError(ex: Throwable): Unit =
          s.execute(new Runnable {
            def run(): Unit = cb.onError(ex)
          })
      }

      source.createSubscriber(cb1, s)
    }
  }

  /** Implementation for [[Consumer.mapAsync]]. */
  private final class MapAsyncConsumer[In, R, R2](source: Consumer[In,R], f: R => Task[R2])
    extends Consumer[In, R2] {

    def createSubscriber(cb: Callback[R2], s: Scheduler): (Subscriber[In], AssignableCancelable) = {
      val asyncCallback = new Callback[R] {
        def onSuccess(value: R): Unit =
          s.execute(new Runnable {
            // Forcing async boundary, otherwise we might
            // end up with stack-overflows or other problems
            def run(): Unit = {
              implicit val scheduler = s
              // For protecting the contract, as if a call was already made to
              // `onSuccess`, then we can't call `onError`
              var streamErrors = true
              try {
                val task = f(value)
                streamErrors = false
                task.runAsync(cb)
              } catch {
                case NonFatal(ex) =>
                  if (streamErrors) cb.onError(ex)
                  else s.reportFailure(ex)
              }
            }
          })

        def onError(ex: Throwable): Unit = {
          // Forcing async boundary, otherwise we might
          // end up with stack-overflows or other problems
          s.execute(new Runnable { def run(): Unit = cb.onError(ex) })
        }
      }

      source.createSubscriber(asyncCallback, s)
    }
  }

  /** Implementation for [[cancel]]. */
  private object CancelledConsumer extends Consumer.Sync[Any, Unit] {
    def createSubscriber(cb: Callback[Unit], s: Scheduler): (Subscriber.Sync[Any], AssignableCancelable) = {
      val out = new Subscriber.Sync[Any] {
        implicit val scheduler = s
        def onNext(elem: Any): Ack = Stop
        def onComplete(): Unit = ()
        def onError(ex: Throwable): Unit = scheduler.reportFailure(ex)
      }

      // Forcing async boundary to prevent problems
      s.execute(new Runnable { def run() = cb.onSuccess(()) })
      (out, AssignableCancelable.alreadyCanceled)
    }
  }

  /** Implementation for [[headOption]] */
  private class HeadOptionConsumer[A] extends Consumer.Sync[A, Option[A]] {
    override def createSubscriber(cb: Callback[Option[A]], s: Scheduler): (Subscriber.Sync[A], AssignableCancelable) = {
      val out = new Subscriber.Sync[A] {
        implicit val scheduler = s
        private[this] var isDone = false

        def onNext(elem: A): Ack = {
          isDone = true
          cb.onSuccess(Some(elem))
          Stop
        }

        def onComplete(): Unit =
          if (!isDone) {
            isDone = true
            cb.onSuccess(None)
          }

        def onError(ex: Throwable): Unit =
          if (!isDone) {
            isDone = true
            cb.onError(ex)
          }
      }

      (out, AssignableCancelable.dummy)
    }
  }

  /** Implementation for [[head]] */
  private class HeadConsumer[A] extends Consumer.Sync[A, A] {
    override def createSubscriber(cb: Callback[A], s: Scheduler): (Subscriber.Sync[A], AssignableCancelable) = {
      val out = new Subscriber.Sync[A] {
        implicit val scheduler = s
        private[this] var isDone = false

        def onNext(elem: A): Ack = {
          isDone = true
          cb.onSuccess(elem)
          Stop
        }

        def onComplete(): Unit =
          if (!isDone) {
            isDone = true
            cb.onError(new NoSuchElementException("head"))
          }

        def onError(ex: Throwable): Unit =
          if (!isDone) {
            isDone = true
            cb.onError(ex)
          }
      }

      (out, AssignableCancelable.dummy)
    }
  }

  /** Implementation for [[firstNotification]] */
  private class FirstNotificationConsumer[A] extends Consumer.Sync[A, Notification[A]] {
    override def createSubscriber(cb: Callback[Notification[A]], s: Scheduler): (Subscriber.Sync[A], AssignableCancelable) = {
      val out = new Subscriber.Sync[A] {
        implicit val scheduler = s
        private[this] var isDone = false

        def onNext(elem: A): Ack = {
          isDone = true
          cb.onSuccess(Notification.OnNext(elem))
          Stop
        }

        def onComplete(): Unit =
          if (!isDone) {
            isDone = true
            cb.onSuccess(Notification.OnComplete)
          }

        def onError(ex: Throwable): Unit =
          if (!isDone) {
            isDone = true
            cb.onSuccess(Notification.OnError(ex))
          }
      }

      (out, AssignableCancelable.dummy)
    }
  }

  /** Implementation for [[foldLeft]]. */
  private final class FoldLeftConsumer[A,R](initial: () => R, f: (R,A) => R)
    extends Consumer.Sync[A,R] {

    def createSubscriber(cb: Callback[R], s: Scheduler): (Subscriber.Sync[A], AssignableCancelable) = {
      val out = new Subscriber.Sync[A] {
        implicit val scheduler = s
        private[this] var isDone = false
        private[this] var state = initial()

        def onNext(elem: A): Ack = {
          // Protects calls to user code from within the operator,
          // as a matter of contract.
          try {
            state = f(state, elem)
            Continue
          } catch {
            case NonFatal(ex) =>
              onError(ex)
              Stop
          }
        }

        def onComplete(): Unit =
          if (!isDone) {
            isDone = true
            cb.onSuccess(state)
          }

        def onError(ex: Throwable): Unit =
          if (!isDone) {
            isDone = true
            cb.onError(ex)
          }
      }

      (out, AssignableCancelable.dummy)
    }
  }

  /** Implementation for [[foldLeftAsync]]. */
  private final class FoldLeftAsyncConsumer[A,R](initial: () => R, f: (R,A) => Task[R])
    extends Consumer[A,R] {

    def createSubscriber(cb: Callback[R], s: Scheduler): (Subscriber[A], AssignableCancelable) = {
      val out = new Subscriber[A] {
        implicit val scheduler = s
        private[this] var isDone = false
        private[this] var state = initial()

        def onNext(elem: A): Future[Ack] = {
          // Protects calls to user code from within the operator,
          // as a matter of contract.
          try {
            val task = f(state, elem).materializeAttempt.map {
              case Now(update) =>
                state = update
                Continue
              case Error(ex) =>
                onError(ex)
                Stop
            }

            task.runAsync
          }
          catch {
            case NonFatal(ex) =>
              onError(ex)
              Stop
          }
        }

        def onComplete(): Unit =
          if (!isDone) {
            isDone = true
            cb.onSuccess(state)
          }

        def onError(ex: Throwable): Unit =
          if (!isDone) {
            isDone = true
            cb.onError(ex)
          }
      }

      (out, AssignableCancelable.dummy)
    }
  }

  /** Implementation for [[complete]] */
  private object CompleteConsumer extends Consumer.Sync[Any, Unit] {
    override def createSubscriber(cb: Callback[Unit], s: Scheduler): (Subscriber.Sync[Any], AssignableCancelable) = {
      val out = new Subscriber.Sync[Any] {
        implicit val scheduler = s
        def onNext(elem: Any): Ack = Continue
        def onComplete(): Unit = cb.onSuccess(())
        def onError(ex: Throwable): Unit = cb.onError(ex)
      }

      (out, AssignableCancelable.dummy)
    }
  }

  /** Implementation for [[foreach]]. */
  private final class ForeachConsumer[A](f: A => Unit)
    extends Consumer.Sync[A, Unit] {

    def createSubscriber(cb: Callback[Unit], s: Scheduler): (Subscriber.Sync[A], AssignableCancelable) = {
      val out = new Subscriber.Sync[A] {
        implicit val scheduler = s
        private[this] var isDone = false

        def onNext(elem: A): Ack = {
          try {
            f(elem)
            Continue
          } catch {
            case NonFatal(ex) =>
              onError(ex)
              Stop
          }
        }

        def onComplete(): Unit =
          if (!isDone) {
            isDone = true
            cb.onSuccess(())
          }

        def onError(ex: Throwable): Unit =
          if (!isDone) {
            isDone = true
            cb.onError(ex)
          }
      }

      (out, AssignableCancelable.dummy)
    }
  }

  /** Implementation for [[foreachAsync]]. */
  private final class ForeachAsyncConsumer[A](f: A => Task[Unit])
    extends Consumer[A, Unit] {

    def createSubscriber(cb: Callback[Unit], s: Scheduler): (Subscriber[A], AssignableCancelable) = {
      val out = new Subscriber[A] {
        implicit val scheduler = s
        private[this] var isDone = false

        def onNext(elem: A): Future[Ack] = {
          try {
            f(elem).coeval.value match {
              case Left(future) =>
                future.map(_ => Continue)
              case Right(()) =>
                Continue
            }
          } catch {
            case NonFatal(ex) =>
              onError(ex)
              Stop
          }
        }

        def onComplete(): Unit =
          if (!isDone) {
            isDone = true
            cb.onSuccess(())
          }

        def onError(ex: Throwable): Unit =
          if (!isDone) {
            isDone = true
            cb.onError(ex)
          }
      }

      (out, AssignableCancelable.dummy)
    }
  }

  /** Implementation for [[Consumer.raiseError]]. */
  private final class RaiseErrorConsumer(ex: Throwable)
    extends Consumer.Sync[Any,Nothing] {

    def createSubscriber(cb: Callback[Nothing], s: Scheduler): (Subscriber.Sync[Any], AssignableCancelable) = {
      val out = new Subscriber.Sync[Any] {
        implicit val scheduler = s
        def onNext(elem: Any): Ack = Stop
        def onComplete(): Unit = ()
        def onError(ex: Throwable): Unit = scheduler.reportFailure(ex)
      }

      // Forcing async boundary to prevent problems
      s.execute(new Runnable { def run() = cb.onError(ex) })
      (out, AssignableCancelable.alreadyCanceled)
    }
  }

  /** Implementation for [[Consumer.fromObserver]]. */
  private final class FromObserverConsumer[In](f: Scheduler => Observer[In])
    extends Consumer[In, Unit] {

    def createSubscriber(cb: Callback[Unit], s: Scheduler): (Subscriber[In], AssignableCancelable) = {
      Try(f(s)) match {
        case Failure(ex) =>
          Consumer.raiseError(ex).createSubscriber(cb,s)

        case Success(out) =>
          val sub = new Subscriber[In] { self =>
            implicit val scheduler = s

            private[this] val isDone = Atomic(false)
            private def signal(ex: Throwable): Unit =
              if (!isDone.getAndSet(true)) {
                if (ex == null) {
                  try out.onComplete()
                  finally cb.onSuccess(())
                }
                else {
                  try out.onError(ex)
                  finally cb.onError(ex)
                }
              }

            def onNext(elem: In): Future[Ack] = {
              val ack = try out.onNext(elem) catch {
                case NonFatal(ex) => Future.failed(ex)
              }

              ack.syncOnComplete {
                case Success(result) =>
                  if (result == Stop) signal(null)
                case Failure(ex) =>
                  signal(ex)
              }

              ack
            }

            def onComplete(): Unit = signal(null)
            def onError(ex: Throwable): Unit = signal(ex)
          }

          (sub, AssignableCancelable.dummy)
      }
    }
  }

  /** Implementation for [[monix.reactive.Consumer.loadBalance]]. */
  private[reactive] final class LoadBalanceConsumer[-In, R]
    (parallelism: Int, consumers: Array[Consumer[In, R]])
    extends Consumer[In, List[R]] {

    require(parallelism > 0, s"parallelism = $parallelism, should be > 0")
    require(consumers.length > 0, "consumers list must not be empty")

    // NOTE: onFinish MUST BE synchronized by `self` and
    // double-checked by means of `isDone`
    def createSubscriber(onFinish: Callback[List[R]], s: Scheduler): (Subscriber[In], AssignableCancelable) = {
      // Assignable cancelable returned, can be used to cancel everything
      // since it will be assigned the stream subscription
      val mainCancelable = SingleAssignmentCancelable()

      val balanced = new Subscriber[In] { self =>
        implicit val scheduler = s

        // Trying to prevent contract violations, once this turns
        // true, then no final events are allowed to happen.
        // MUST BE synchronized by `self`.
        private[this] var isUpstreamComplete = false

        // Trying to prevent contract violations. Turns true in case
        // we already signaled a result upstream.
        // MUST BE synchronized by `self`.
        private[this] var isDownstreamDone = false

        // Stores the error that was reported upstream - basically
        // multiple subscribers can report multiple errors, but we
        // emit the first one, so in case multiple errors happen we
        // want to log them, but only if they aren't the same reference
        // MUST BE synchronized by `self`
        private[this] var reportedError: Throwable = null

        // Results accumulator - when length == parallelism,
        // that's when we need to trigger `onFinish.onSuccess`.
        // MUST BE synchronized by `self`
        private[this] val accumulator = ListBuffer.empty[R]

        /** Builds cancelables for subscribers. */
        private def newCancelableFor(out: IndexedSubscriber[In]): Cancelable =
          new Cancelable {
            private[this] var isCanceled = false
            // Forcing an asynchronous boundary, to avoid any possible
            // initialization issues (in building subscribersQueue) or
            // stack overflows and other problems
            def cancel(): Unit = scheduler.executeAsync { () =>
              // We are required to synchronize, because we need to
              // make sure that subscribersQueue is fully created before
              // triggering any cancellation!
              self.synchronized {
                // Guards the idempotency contract of cancel(); not really
                // required, because `deactivate()` should be idempotent, but
                // since we are doing an expensive synchronize, we might as well
                if (!isCanceled) {
                  isCanceled = true
                  interruptOne(out, null)
                }
              }
            }
          }

        // Asynchronous queue that serves idle subscribers waiting
        // for something to process, or that puts the stream on wait
        // until there are subscribers available
        private[this] val subscribersQueue = self.synchronized {
          var initial = Queue.empty[IndexedSubscriber[In]]
          // When the callback gets called by each subscriber, on success we
          // do nothing because for normal completion we are listing on
          // `Stop` events from onNext, but on failure we deactivate all.
          val callback = new Callback[R] {
            def onSuccess(value: R): Unit =
              accumulate(value)
            def onError(ex: Throwable): Unit =
              interruptAll(ex)
          }

          val arrLen = consumers.length
          var i = 0

          while (i < parallelism) {
            val (out, c) = consumers(i % arrLen).createSubscriber(callback, s)
            val indexed = IndexedSubscriber(i, out)
            // Every created subscriber has the opportunity to cancel the
            // main subscription if needed, cancellation thus happening globally
            c := newCancelableFor(indexed)
            initial = initial.enqueue(indexed)
            i += 1
          }

          new LoadBalanceConsumer.AsyncQueue(initial, parallelism)
        }

        def onNext(elem: In): Future[Ack] = {
          // Declares a stop event, completing the callback
          def stop(): Stop = self.synchronized {
            // Protecting against contract violations
            isUpstreamComplete = true
            Stop
          }

          // Are there subscribers available?
          val sf = subscribersQueue.poll()
          // Doing a little optimization to prevent one async boundary
          sf.value match {
            case Some(Success(subscriber)) =>
              // As a matter of protocol, if null values happen, then
              // this means that all subscribers have been deactivated and so
              // we should cancel the streaming.
              if (subscriber == null) stop() else {
                signalNext(subscriber, elem)
                Continue
              }
            case _ => sf.map {
              case null => stop()
              case subscriber =>
                signalNext(subscriber, elem)
                Continue
            }
          }
        }

        /** Triggered whenever the subscribers are finishing with onSuccess */
        private def accumulate(value: R): Unit = self.synchronized {
          if (!isDownstreamDone) {
            accumulator += value
            if (accumulator.length == parallelism) {
              isDownstreamDone = true
              onFinish.onSuccess(accumulator.toList)
              // GC relief
              accumulator.clear()
            }
          }
        }

        /** Triggered whenever we need to signal an `onError` upstream */
        private def reportErrorUpstream(ex: Throwable) = self.synchronized {
          if (isDownstreamDone) {
            // We only report errors that we haven't
            // reported to upstream by means of `onError`!
            if (reportedError != ex)
              scheduler.reportFailure(ex)
          } else {
            isDownstreamDone = true
            reportedError = ex
            onFinish.onError(ex)
            // GC relief
            accumulator.clear()
          }
        }

        /** Called whenever a subscriber stops its subscription, or
          * when an error gets thrown.
          */
        private def interruptOne(out: IndexedSubscriber[In], ex: Throwable): Unit = {
          // Deactivating the subscriber. In case all subscribers
          // have been deactivated, then we are done
          if (subscribersQueue.deactivate(out))
            interruptAll(ex)
        }

        /** When Stop or error is received, this makes sure the
          * streaming gets interrupted!
          */
        private def interruptAll(ex: Throwable): Unit = self.synchronized {
          // All the following operations are idempotent!
          isUpstreamComplete = true
          mainCancelable.cancel()
          subscribersQueue.deactivateAll()
          // Is this an error to signal?
          if (ex != null) reportErrorUpstream(ex)
        }

        /** Given a subscriber, signals the given element, then return
          * the subscriber to the queue if possible.
          */
        private def signalNext(out: IndexedSubscriber[In], elem: In): Unit = {
          // We are forcing an asynchronous boundary here, since we
          // don't want to block the main thread!
          scheduler.executeAsync { () =>
            try out.out.onNext(elem).syncOnComplete {
              case Success(ack) =>
                ack match {
                  case Continue =>
                    // We have permission to continue from this subscriber
                    // so returning it to the queue, to be reused
                    subscribersQueue.offer(out)
                  case Stop =>
                    interruptOne(out, null)
                }
              case Failure(ex) =>
                interruptAll(ex)
            } catch {
              case NonFatal(ex) =>
                interruptAll(ex)
            }
          }
        }

        def onComplete(): Unit =
          signalComplete(null)
        def onError(ex: Throwable): Unit =
          signalComplete(ex)

        private def signalComplete(ex: Throwable): Unit = {
          def loop(activeCount: Int): Future[Unit] = {
            // If we no longer have active subscribers to
            // push events into, then the loop is finished
            if (activeCount <= 0)
              Future.successful(())
            else subscribersQueue.poll().flatMap {
              // By protocol, if a null happens, then there are
              // no more active subscribers available
              case null =>
                Future.successful(())
              case subscriber =>
                try {
                  if (ex == null) subscriber.out.onComplete()
                  else subscriber.out.onError(ex)
                } catch {
                  case NonFatal(err) => s.reportFailure(err)
                }

                if (activeCount > 0) loop(activeCount-1)
                else Future.successful(())
            }
          }

          self.synchronized {
            // Protecting against contract violations.
            if (!isUpstreamComplete) {
              isUpstreamComplete = true

              // Starting the loop
              loop(subscribersQueue.activeCount).onComplete {
                case Success(()) =>
                  if (ex != null) reportErrorUpstream(ex)
                case Failure(err) =>
                  reportErrorUpstream(err)
              }
            } else if (ex != null) {
              reportErrorUpstream(ex)
            }
          }
        }
      }

      (balanced, mainCancelable)
    }
  }

  private[reactive] object LoadBalanceConsumer {
    /** Wraps a subscriber implementation into one
      * that exposes an ID.
      */
    private[reactive] final
    case class IndexedSubscriber[-In](id: Int, out: Subscriber[In])

    private final class AsyncQueue[In](
      initialQueue: Queue[IndexedSubscriber[In]], parallelism: Int) {

      private[this] val stateRef = {
        val initial: State[In] = Available(initialQueue, BitSet.empty, parallelism)
        Atomic.withPadding(initial, PaddingStrategy.LeftRight256)
      }

      def activeCount: Int =
        stateRef.get.activeCount

      @tailrec
      def offer(value: IndexedSubscriber[In]): Unit =
        stateRef.get match {
          case current @ Available(queue, canceledIDs, ac) =>
            if (ac > 0 && !canceledIDs(value.id)) {
              val update = Available(queue.enqueue(value), canceledIDs, ac)
              if (!stateRef.compareAndSet(current, update))
                offer(value)
            }

          case current @ Waiting(promise, canceledIDs, ac) =>
            if (!canceledIDs(value.id)) {
              val update = Available[In](Queue.empty, canceledIDs, ac)
              if (!stateRef.compareAndSet(current, update))
                offer(value)
              else
                promise.success(value)
            }
        }

      @tailrec
      def poll(): Future[IndexedSubscriber[In]] =
        stateRef.get match {
          case current @ Available(queue, canceledIDs, ac) =>
            if (ac <= 0)
              Future.successful(null)
            else if (queue.isEmpty) {
              val p = Promise[IndexedSubscriber[In]]()
              val update = Waiting(p, canceledIDs, ac)
              if (!stateRef.compareAndSet(current, update))
                poll()
              else
                p.future
            }
            else {
              val (ref, newQueue) = queue.dequeue
              val update = Available(newQueue, canceledIDs, ac)
              if (!stateRef.compareAndSet(current, update))
                poll()
              else
                Future.successful(ref)
            }
          case Waiting(_,_,_) =>
            Future.failed(new IllegalStateException("waiting in poll()"))
        }

      @tailrec
      def deactivateAll(): Unit =
        stateRef.get match {
          case current @ Available(_, canceledIDs, _) =>
            val update: State[In] = Available(Queue.empty, canceledIDs, 0)
            if (!stateRef.compareAndSet(current, update))
              deactivateAll()
          case current @ Waiting(promise, canceledIDs, _) =>
            val update: State[In] = Available(Queue.empty, canceledIDs, 0)
            if (!stateRef.compareAndSet(current, update))
              deactivateAll()
            else
              promise.success(null)
        }

      @tailrec
      def deactivate(ref: IndexedSubscriber[In]): Boolean =
        stateRef.get match {
          case current @ Available(queue, canceledIDs, count) =>
            if (count <= 0) true else {
              val update = if (canceledIDs(ref.id)) current else {
                val newQueue = queue.filterNot(_.id == ref.id)
                Available(newQueue, canceledIDs+ref.id, count-1)
              }

              if (update.activeCount == current.activeCount)
                false // nothing to update
              else if (!stateRef.compareAndSet(current, update))
                deactivate(ref) // retry
              else
                update.activeCount == 0
            }

          case current @ Waiting(promise, canceledIDs, count) =>
            if (canceledIDs(ref.id)) count <= 0 else {
              val update =
                if (count - 1 > 0) Waiting(promise, canceledIDs+ref.id, count-1)
                else Available[In](Queue.empty, canceledIDs+ref.id, 0)

              if (!stateRef.compareAndSet(current, update))
                deactivate(ref) // retry
              else if (update.activeCount <= 0) {
                promise.success(null)
                true
              }
              else
                false
            }
        }
    }

    private[reactive] sealed trait State[In] {
      def activeCount: Int
      def canceledIDs: Set[Int]
    }

    private[reactive] final case class Available[In](
      available: Queue[IndexedSubscriber[In]],
      canceledIDs: BitSet,
      activeCount: Int)
      extends State[In]

    private[reactive] final case class Waiting[In](
      promise: Promise[IndexedSubscriber[In]],
      canceledIDs: BitSet,
      activeCount: Int)
      extends State[In]
  }
}
