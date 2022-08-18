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

package monix.reactive.internal.deprecated

import cats.effect.{ Effect, ExitCase }
import cats.{ Monoid, Order }
import monix.eval.{ Task, TaskLike }
import monix.execution.Ack
import monix.reactive.Observable
import monix.reactive.internal.operators.DoOnTerminateOperator

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

private[reactive] trait ObservableDeprecatedMethods[+A] extends Any {
  def self: Observable[A]

  /** DEPRECATED - renamed to [[Observable.executeAsync executeAsync]].
    *
    * The reason for the deprecation is the repurposing of the word "fork"
    * in [[monix.eval.Task Task]].
    */
  @deprecated("Renamed to Observable!.executeAsync", "3.0.0")
  def executeWithFork: Observable[A] = {
    // $COVERAGE-OFF$
    self.executeAsync
    // $COVERAGE-ON$
  }

  /** DEPRECATED - renamed to [[Observable.delayExecution delayExecution]].
    *
    * The reason for the deprecation is making the name more consistent
    * with [[monix.eval.Task Task]].
    */
  @deprecated("Renamed to Observable!.delayExecution", "3.0.0")
  def delaySubscription(timespan: FiniteDuration): Observable[A] =
    self.delayExecution(timespan)

  /** DEPRECATED - renamed to [[Observable.delayExecutionWith delayExecutionWith]].
    *
    * The reason for the deprecation is making the name more consistent
    * with [[Observable.delayExecution delayExecution]].
    */
  @deprecated("Renamed to Observable!.delayExecutionWith", "3.0.0")
  def delaySubscriptionWith(trigger: Observable[Any]): Observable[A] =
    self.delayExecutionWith(trigger)

  /** DEPRECATED — signature changed, please see:
    *
    *  - [[Observable.doOnEarlyStop doOnEarlyStop]]
    *  - [[Observable.doOnEarlyStopF doOnEarlyStopF]]
    *
    *  NOTE you can still get the same behavior via `doOnEarlyStopF`,
    *  because `Function0` implements `cats.Comonad` and `Task`
    *  conversions from `Comonad` are allowed, although frankly in
    *  this case `doOnEarlyStop`:
    *
    *  {{{
    *    import monix.reactive._
    *
    *    // This is possible, but it's better to work with
    *    // pure functions, so use Task or IO ;-)
    *    Observable.range(0, 1000).take(10).doOnEarlyStopF {
    *      // Via the magic of `TaskLike`, we are allowed to use `Function0`
    *      () => println("Stopped!")
    *    }
    *  }}}
    */
  @deprecated("Signature changed to usage of Task", "3.0.0")
  def doOnEarlyStop(cb: () => Unit): Observable[A] = {
    // $COVERAGE-OFF$
    self.doOnEarlyStop(Task(cb()))
    // $COVERAGE-ON$
  }

  /**
    * DEPRECATED — renamed to [[Observable.doOnEarlyStopF doOnEarlyStopF]].
    */
  @deprecated("Renamed to doOnEarlyStopF", "3.0.0")
  def doOnEarlyStopEval[F[_]](effect: F[Unit])(implicit F: TaskLike[F]): Observable[A] = {
    // $COVERAGE-OFF$
    self.doOnEarlyStopF(effect)
    // $COVERAGE-ON$
  }

  /**
    * DEPRECATED — renamed to [[Observable.doOnEarlyStop doOnEarlyStop]].
    */
  @deprecated("Renamed to doOnEarlyStopF", "3.0.0")
  def doOnEarlyStopTask(task: Task[Unit]): Observable[A] = {
    // $COVERAGE-OFF$
    self.doOnEarlyStop(task)
    // $COVERAGE-ON$
  }

  /**
    * DEPRECATED - you can switch to:
    *
    *  - [[Observable.doOnSubscriptionCancel doOnSubscriptionCancel]]
    *  - [[Observable.doOnSubscriptionCancelF doOnSubscriptionCancelF]]
    *
    * NOTE that you can still use side effectful functions with
    * `doOnSubscriptionCancelF`, via the magic of [[monix.eval.TaskLike]],
    * but it's no longer recommended:
    *
    * {{{
    *   import monix.reactive._
    *
    *   Observable.range(0, Int.MaxValue)
    *     .doOnEarlyStopF(() => println("Cancelled!"))
    * }}}
    */
  @deprecated("Signature changed, switch to doOnSubscriptionCancelF", "3.0.0")
  def doOnSubscriptionCancel(cb: () => Unit): Observable[A] = {
    // $COVERAGE-OFF$
    self.doOnSubscriptionCancelF(cb)
    // $COVERAGE-ON$
  }

  /** DEPRECATED - signature changed to usage of `Task`. You can switch to:
    *
    *  - [[Observable.doOnComplete doOnComplete]]
    *  - [[Observable.doOnCompleteF doOnCompleteF]]
    *
    * NOTE that you can still use side effectful functions with
    * `doOnCompleteF`, via the magic of [[monix.eval.TaskLike]], but it's no longer
    * recommended:
    *
    * {{{
    *    import monix.reactive._
    *
    *   // Needed for the Comonad[Function0] instance
    *   Observable.range(0, 100)
    *     .doOnCompleteF(() => println("Completed!"))
    * }}}
    */
  @deprecated("Signature changed, switch to doOnCompleteF", "3.0.0")
  def doOnComplete(cb: () => Unit): Observable[A] = {
    // $COVERAGE-OFF$
    self.doOnCompleteF(cb)
    // $COVERAGE-OFF$
  }

  /**
    * DEPRECATED — renamed to [[Observable.doOnCompleteF doOnCompleteF]].
    */
  @deprecated("Renamed to doOnCompleteF", "3.0.0")
  def doOnCompleteEval[F[_]](effect: F[Unit])(implicit F: TaskLike[F]): Observable[A] = {
    // $COVERAGE-OFF$
    self.doOnCompleteF(effect)
    // $COVERAGE-ON$
  }

  /**
    * DEPRECATED — renamed to [[Observable.doOnComplete doOnComplete]].
    */
  @deprecated("Renamed to doOnComplete", "3.0.0")
  def doOnCompleteTask(task: Task[Unit]): Observable[A] = {
    // $COVERAGE-OFF$
    self.doOnComplete(task)
    // $COVERAGE-ON$
  }

  /**
    * DEPRECATED — Signature changed, see [[Observable.doOnError doOnError]].
    */
  @deprecated("Signature changed to use Task", "3.0.0")
  def doOnError(cb: Throwable => Unit): Observable[A] = {
    // $COVERAGE-OFF$
    self.doOnError(e => Task(cb(e)))
    // $COVERAGE-ON$
  }

  /**
    * DEPRECATED — renamed to [[Observable.doOnErrorF doOnErrorF]].
    */
  @deprecated("Renamed to doOnErrorF", "3.0.0")
  def doOnErrorEval[F[_]](cb: Throwable => F[Unit])(implicit F: TaskLike[F]): Observable[A] = {
    // $COVERAGE-OFF$
    self.doOnErrorF(cb)
    // $COVERAGE-ON$
  }

  /**
    * DEPRECATED — renamed to [[Observable.doOnError doOnError]].
    */
  @deprecated("Renamed to doOnError", "3.0.0")
  def doOnErrorTask(cb: Throwable => Task[Unit]): Observable[A] = {
    // $COVERAGE-OFF$
    self.doOnError(cb)
    // $COVERAGE-ON$
  }

  /** DEPRECATED — switch to:
    *
    *  - [[Observable.guaranteeCase guaranteeCase]]
    *  - [[Observable.guaranteeCaseF guaranteeCaseF]]
    */
  @deprecated("Switch to guaranteeCase", "3.0.0")
  def doOnTerminate(cb: Option[Throwable] => Unit): Observable[A] = {
    // $COVERAGE-OFF$
    self.guaranteeCase {
      case ExitCase.Error(e) => Task(cb(Some(e)))
      case _ => Task(cb(None))
    }
    // $COVERAGE-ON$
  }

  /** DEPRECATED — a much better version of `doOnTerminateEval`
    * is [[Observable.guaranteeCaseF guaranteeCaseF]].
    *
    * Example:
    * {{{
    *   import cats.effect.{ExitCase, IO}
    *   import monix.reactive._
    *
    *   Observable.range(0, 1000).guaranteeCaseF {
    *     case ExitCase.Error(e) =>
    *       IO(println(s"Error raised: $$e"))
    *     case ExitCase.Completed =>
    *       IO(println("Stream completed normally"))
    *     case ExitCase.Canceled =>
    *       IO(println("Stream was cancelled"))
    *   }
    * }}}
    */
  @deprecated("Switch to guaranteeCaseF", "3.0.0")
  def doOnTerminateEval[F[_]](cb: Option[Throwable] => F[Unit])(implicit F: TaskLike[F]): Observable[A] = {
    // $COVERAGE-OFF$
    self.guaranteeCaseF {
      case ExitCase.Error(e) => cb(Some(e))
      case _ => cb(None)
    }
    // $COVERAGE-ON$
  }

  /** DEPRECATED — a much better version of `doOnTerminateTask`
    * is [[Observable.guaranteeCase guaranteeCase]].
    *
    * Example:
    * {{{
    *   import cats.effect.ExitCase
    *   import monix.eval._
    *   import monix.reactive._
    *
    *   Observable.range(0, 1000).guaranteeCase {
    *     case ExitCase.Error(e) =>
    *       Task(println(s"Error raised: $$e"))
    *     case ExitCase.Completed =>
    *       Task(println("Stream completed normally"))
    *     case ExitCase.Canceled =>
    *       Task(println("Stream was cancelled"))
    *   }
    * }}}
    */
  @deprecated("Switch to guaranteeCase", "3.0.0")
  def doOnTerminateTask(cb: Option[Throwable] => Task[Unit]): Observable[A] = {
    // $COVERAGE-OFF$
    self.guaranteeCase {
      case ExitCase.Error(e) => cb(Some(e))
      case _ => cb(None)
    }
    // $COVERAGE-ON$
  }

  /** DEPRECATED — this function has no direct replacement.
    *
    * Switching to [[Observable.guaranteeCase]] is recommended.
    */
  @deprecated("Switch to guaranteeCase", "3.0.0")
  def doAfterTerminate(cb: Option[Throwable] => Unit): Observable[A] = {
    // $COVERAGE-OFF$
    self.liftByOperator(new DoOnTerminateOperator[A](e => Task(cb(e)), happensBefore = false))
    // $COVERAGE-ON$
  }

  /** DEPRECATED — this function has no direct replacement.
    *
    * Switching to [[Observable.guaranteeCaseF]] is recommended.
    */
  @deprecated("Switch to guaranteeCaseF", "3.0.0")
  def doAfterTerminateEval[F[_]](cb: Option[Throwable] => F[Unit])(implicit F: TaskLike[F]): Observable[A] = {
    // $COVERAGE-OFF$
    self.liftByOperator(new DoOnTerminateOperator[A](e => F(cb(e)), happensBefore = false))
    // $COVERAGE-ON$
  }

  /** DEPRECATED — this function has no direct replacement.
    *
    * Switching to [[Observable.guaranteeCase]] is recommended.
    */
  @deprecated("Switch to guaranteeCase", "3.0.0")
  def doAfterTerminateTask(cb: Option[Throwable] => Task[Unit]): Observable[A] = {
    // $COVERAGE-OFF$
    self.liftByOperator(new DoOnTerminateOperator[A](cb, happensBefore = false))
    // $COVERAGE-ON$
  }

  /** DEPRECATED — signature for function changed to use
    * [[monix.eval.Task]].
    *
    * {{{
    *   import monix.eval._
    *   import monix.reactive._
    *
    *   Observable.range(0, 100).doOnNext { a =>
    *     Task(println(s"Next: $$a"))
    *   }
    * }}}
    */
  @deprecated("Signature changed to use Task", "3.0.0")
  def doOnNext(cb: A => Unit): Observable[A] = {
    // $COVERAGE-OFF$
    self.doOnNext(a => Task(cb(a)))
    // $COVERAGE-ON$
  }

  /**
    * DEPRECATED — renamed to [[Observable.doOnNextF]].
    */
  @deprecated("Renamed to doOnNextF", "3.0.0")
  def doOnNextEval[F[_]](cb: A => F[Unit])(implicit F: TaskLike[F]): Observable[A] = {
    // $COVERAGE-OFF$
    self.doOnNextF(cb)
    // $COVERAGE-ON$
  }

  /**
    * DEPRECATED — renamed to [[Observable.doOnNext]].
    */
  @deprecated("Renamed to doOnNext", "3.0.0")
  def doOnNextTask(cb: A => Task[Unit]): Observable[A] = {
    // $COVERAGE-OFF$
    self.doOnNext(cb)
    // $COVERAGE-ON$
  }

  /**
    * DEPRECATED — signature changed to use [[monix.eval.Task]].
    */
  @deprecated("Signature changed to use Task", "3.0.0")
  def doOnNextAck(cb: (A, Ack) => Unit): Observable[A] = {
    // $COVERAGE-OFF$
    self.doOnNextAck((a, ack) => Task(cb(a, ack)))
    // $COVERAGE-ON$
  }

  /**
    * DEPRECATED — renamed to [[Observable.doOnNextAckF]].
    */
  @deprecated("Renamed to doOnNextAckF", "3.0.0")
  def doOnNextAckEval[F[_]](cb: (A, Ack) => F[Unit])(implicit F: TaskLike[F]): Observable[A] = {
    // $COVERAGE-OFF$
    self.doOnNextAckF(cb)
    // $COVERAGE-ON$
  }

  /**
    * DEPRECATED — renamed to [[Observable.doOnNextAck]].
    */
  @deprecated("Renamed to doOnNextAck", "3.0.0")
  def doOnNextAckTask(cb: (A, Ack) => Task[Unit]): Observable[A] = {
    // $COVERAGE-OFF$
    self.doOnNextAck(cb)
    // $COVERAGE-ON$
  }

  /**
    * DEPRECATED — signature changed to use [[monix.eval.Task]]
    */
  @deprecated("Signature changed to use Task", "3.0.0")
  def doOnStart(cb: A => Unit): Observable[A] = {
    // $COVERAGE-OFF$
    self.doOnStart(a => Task(cb(a)))
    // $COVERAGE-ON$
  }

  /**
    * DEPRECATED — renamed to [[Observable.doOnStart]]
    */
  @deprecated("Renamed to doOnStart", "3.0.0")
  def doOnStartTask(cb: A => Task[Unit]): Observable[A] = {
    // $COVERAGE-OFF$
    self.doOnStart(cb)
    // $COVERAGE-ON$
  }

  /**
    * DEPRECATED — renamed to [[Observable.doOnStartF]]
    */
  @deprecated("Renamed to doOnStartF", "3.0.0")
  def doOnStartEval[F[_]](cb: A => F[Unit])(implicit F: Effect[F]): Observable[A] = {
    // $COVERAGE-OFF$
    self.doOnStartF(cb)
    // $COVERAGE-ON$
  }

  /** DEPRECATED — signature changed to use [[monix.eval.Task Task]].
    *
    * Switch to:
    *
    *   - [[Observable.doOnSubscribe doOnSubscribe]]
    *   - [[Observable.doOnSubscribeF doOnSubscribeF]]
    *
    * Note that via the magic of [[monix.eval.TaskLike]] which supports `Function0`
    * conversions, you can still use side effectful callbacks in
    * [[Observable.doOnSubscribeF doOnSubscribeF]], but it isn't
    * recommended:
    *
    * {{{
    *   import monix.reactive._
    *
    *   Observable.range(0, 10).doOnSubscribeF { () =>
    *     println("Look ma! Side-effectful callbacks!")
    *   }
    * }}}
    *
    * The switch to `Task` is to encourage referential transparency,
    * so use it.
    */
  @deprecated("Switch to doOnStart or doOnStartF", "3.0.0")
  def doOnSubscribe(cb: () => Unit): Observable[A] = {
    // $COVERAGE-OFF$
    self.doOnSubscribe(Task(cb()))
    // $COVERAGE-ON$
  }

  /** DEPRECATED — signature changed to use [[monix.eval.Task Task]].
    *
    * Switch to:
    *
    *   - [[Observable.doAfterSubscribe doAfterSubscribe]]
    *   - [[Observable.doAfterSubscribeF doAfterSubscribeF]]
    *
    * Note that via the magic of [[monix.eval.TaskLike]] which supports `Function0`
    * conversions, you can still use side effectful callbacks in
    * [[Observable.doAfterSubscribeF doAfterSubscribeF]], but it isn't
    * recommended:
    *
    * {{{
    *   import monix.reactive._
    *
    *   Observable.range(0, 10).doAfterSubscribeF { () =>
    *     println("Look ma! Side-effectful callbacks!")
    *   }
    * }}}
    *
    * The switch to `Task` is to encourage referential transparency,
    * so use it.
    */
  @deprecated("Switch to doAfterSubscribe or doAfterSubscribeF", "3.0.0")
  def doAfterSubscribe(cb: () => Unit): Observable[A] = {
    // $COVERAGE-OFF$
    self.doAfterSubscribe(Task(cb()))
    // $COVERAGE-ON$
  }

  /**
    * DEPRECATED — renamed to [[Observable.mapEval]].
    */
  @deprecated("Renamed to mapEval", "3.0.0")
  def mapTask[B](f: A => Task[B]): Observable[B] = {
    // $COVERAGE-OFF$
    self.mapEval(f)
    // $COVERAGE-ON$
  }

  /** DEPRECATED — switch to [[Observable.mapEvalF]], which
    * is the generic version that supports usage with `Future`
    * via the magic of [[monix.eval.TaskLike]].
    *
    * The replacement is direct, a simple rename:
    * {{{
    *   import scala.concurrent._
    *   import scala.concurrent.duration._
    *   import monix.execution.FutureUtils.extensions._
    *   import monix.reactive._
    *
    *   Observable.range(0, 100).mapEvalF { a =>
    *     import monix.execution.Scheduler.Implicits.global
    *     Future.delayedResult(1.second)(a)
    *   }
    * }}}
    */
  @deprecated("Switch to mapEvalF", "3.0.0")
  def mapFuture[B](f: A => Future[B]): Observable[B] = {
    // $COVERAGE-OFF$
    self.mapEvalF(f)
    // $COVERAGE-ON$
  }

  /** DEPRECATED — renamed to [[Observable.forall]] in an effort to unify
    * the naming conventions with `Iterant`, Scala's standard library
    * and Cats-Effect.
    *
    * The `F` suffix now represents working with higher-kinded types,
    * e.g. `F[_]`, with restrictions like [[ObservableLike]],
    * [[monix.eval.TaskLike TaskLike]], `cats.effect.Sync`, etc.
    */
  @deprecated("Renamed to forall (no camel case in forall, no F suffix)", "3.0.0")
  def forAllF(p: A => Boolean): Observable[Boolean] = {
    // $COVERAGE-OFF$
    self.forall(p)
    // $COVERAGE-ON$
  }

  /** DEPRECATED — renamed to [[Observable.forallL]] in an effort to unify
    * the naming conventions with `Iterant` and Scala's standard library.
    */
  @deprecated("Renamed to forallL (no camel case in forall)", "3.0.0")
  def forAllL(p: A => Boolean): Task[Boolean] = {
    // $COVERAGE-OFF$
    self.forallL(p)
    // $COVERAGE-ON$
  }

  /** DEPRECATED — renamed to [[Observable.forall]] in an effort to unify
    * the naming conventions with `Iterant`, Scala's standard library
    * and Cats-Effect.
    *
    * The `F` suffix now represents working with higher-kinded types,
    * e.g. `F[_]`, with restrictions like [[ObservableLike]],
    * [[monix.eval.TaskLike TaskLike]], `cats.effect.Sync`, etc.
    */
  @deprecated("Renamed to exists (no F suffix)", "3.0.0")
  def existsF(p: A => Boolean): Observable[Boolean] = {
    // $COVERAGE-OFF$
    self.exists(p)
    // $COVERAGE-ON$
  }

  /** DEPRECATED — renamed to [[Observable.forall]] in an effort to unify
    * the naming conventions with `Iterant`, Scala's standard library
    * and Cats-Effect.
    *
    * The `F` suffix now represents working with higher-kinded types,
    * e.g. `F[_]`, with restrictions like [[ObservableLike]],
    * [[monix.eval.TaskLike TaskLike]], `cats.effect.Sync`, etc.
    */
  @deprecated("Renamed to last (no F suffix)", "3.0.0")
  def lastF: Observable[A] = {
    // $COVERAGE-OFF$
    self.last
    // $COVERAGE-ON$
  }

  /** DEPRECATED — renamed to [[Observable.scanEval]].
    *
    * Renaming was done for naming consistency. Functions that use
    * [[monix.eval.Task Task]] parameters in `Observable` no longer
    * have a `Task` suffix.
    */
  @deprecated("Renamed to scanEval", "3.0.0")
  def scanTask[S](seed: Task[S])(op: (S, A) => Task[S]): Observable[S] = {
    // $COVERAGE-OFF$
    self.scanEval(seed)(op)
    // $COVERAGE-ON$
  }

  /** DEPRECATED — renamed to [[Observable.scanEval0]].
    *
    * Renaming was done for naming consistency. Functions that use
    * [[monix.eval.Task Task]] parameters in `Observable` no longer
    * have a `Task` suffix.
    */
  @deprecated("Renamed to scanEval0", "3.0.0")
  def scanTask0[S](seed: Task[S])(op: (S, A) => Task[S]): Observable[S] = {
    // $COVERAGE-OFF$
    self.scanEval0(seed)(op)
    // $COVERAGE-ON$
  }

  /** DEPRECATED — renamed to [[Observable.count]] in an effort to unify
    * the naming conventions with `Iterant`, Scala's standard library
    * and Cats-Effect.
    *
    * The `F` suffix now represents working with higher-kinded types,
    * e.g. `F[_]`, with restrictions like [[ObservableLike]],
    * [[monix.eval.TaskLike TaskLike]], `cats.effect.Sync`, etc.
    */
  @deprecated("Renamed to count (no F suffix)", "3.0.0")
  def countF: Observable[Long] = {
    // $COVERAGE-OFF$
    self.count
    // $COVERAGE-ON$
  }

  /** DEPRECATED — renamed to [[Observable.find]] in an effort to unify
    * the naming conventions with `Iterant`, Scala's standard library
    * and Cats-Effect.
    *
    * The `F` suffix now represents working with higher-kinded types,
    * e.g. `F[_]`, with restrictions like [[ObservableLike]],
    * [[monix.eval.TaskLike TaskLike]], `cats.effect.Sync`, etc.
    */
  @deprecated("Renamed to find (no F suffix)", "3.0.0")
  def findF(p: A => Boolean): Observable[A] = {
    // $COVERAGE-OFF$
    self.find(p)
    // $COVERAGE-ON$
  }

  /** DEPRECATED — renamed to [[Observable.fold]] in an effort to unify
    * the naming conventions with `Iterant`, Scala's standard library
    * and Cats-Effect.
    *
    * The `F` suffix now represents working with higher-kinded types,
    * e.g. `F[_]`, with restrictions like [[ObservableLike]],
    * [[monix.eval.TaskLike TaskLike]], `cats.effect.Sync`, etc.
    */
  @deprecated("Renamed to find (no F suffix)", "3.0.0")
  def foldF[AA >: A](implicit A: Monoid[AA]): Observable[AA] = {
    // $COVERAGE-OFF$
    self.fold(A)
    // $COVERAGE-ON$
  }

  /** DEPRECATED — renamed to [[Observable.foldWhileLeft]] in an effort
    * to unify the naming conventions with `Iterant`, Scala's standard
    * library and Cats-Effect.
    *
    * The `F` suffix now represents working with higher-kinded types,
    * e.g. `F[_]`, with restrictions like [[ObservableLike]],
    * [[monix.eval.TaskLike TaskLike]], `cats.effect.Sync`, etc.
    */
  @deprecated("Renamed to foldWhileLeft (no F suffix)", "3.0.0")
  def foldWhileLeftF[S](seed: => S)(op: (S, A) => Either[S, S]): Observable[S] = {
    // $COVERAGE-OFF$
    self.foldWhileLeft(seed)(op)
    // $COVERAGE-ON$
  }

  /** DEPRECATED — renamed to [[Observable.head]] in an effort to unify
    * the naming conventions with `Iterant`, Scala's standard library
    * and Cats-Effect.
    *
    * The `F` suffix now represents working with higher-kinded types,
    * e.g. `F[_]`, with restrictions like [[ObservableLike]],
    * [[monix.eval.TaskLike TaskLike]], `cats.effect.Sync`, etc.
    */
  @deprecated("Renamed to head (no F suffix)", "3.0.0")
  def headF: Observable[A] = {
    // $COVERAGE-OFF$
    self.head
    // $COVERAGE-ON$
  }

  /** DEPRECATED — renamed to [[Observable.foldLeft]] in an effort to unify
    * the naming conventions with `Iterant`, Scala's standard library
    * and Cats-Effect.
    *
    * The `F` suffix now represents working with higher-kinded types,
    * e.g. `F[_]`, with restrictions like [[ObservableLike]],
    * [[monix.eval.TaskLike TaskLike]], `cats.effect.Sync`, etc.
    */
  @deprecated("Renamed to foldLeft (no F suffix)", "3.0.0")
  def foldLeftF[R](seed: => R)(op: (R, A) => R): Observable[R] = {
    // $COVERAGE-OFF$
    self.foldLeft(seed)(op)
    // $COVERAGE-ON$
  }

  /** DEPRECATED — renamed to [[Observable.isEmpty]] in an effort to unify
    * the naming conventions with `Iterant`, Scala's standard library
    * and Cats-Effect.
    *
    * The `F` suffix now represents working with higher-kinded types,
    * e.g. `F[_]`, with restrictions like [[ObservableLike]],
    * [[monix.eval.TaskLike TaskLike]], `cats.effect.Sync`, etc.
    */
  @deprecated("Renamed to isEmpty (no F suffix)", "3.0.0")
  def isEmptyF: Observable[Boolean] = {
    // $COVERAGE-OFF$
    self.isEmpty
    // $COVERAGE-ON$
  }

  /** DEPRECATED — renamed to [[Observable.max]] in an effort to unify
    * the naming conventions with `Iterant`, Scala's standard library
    * and Cats-Effect.
    *
    * The `F` suffix now represents working with higher-kinded types,
    * e.g. `F[_]`, with restrictions like [[ObservableLike]],
    * [[monix.eval.TaskLike TaskLike]], `cats.effect.Sync`, etc.
    */
  @deprecated("Renamed to max (no F suffix)", "3.0.0")
  def maxF[AA >: A](implicit A: Order[AA]): Observable[AA] = {
    // $COVERAGE-OFF$
    self.max(A)
    // $COVERAGE-ON$
  }

  /** DEPRECATED — renamed to [[Observable.maxBy]] in an effort to unify
    * the naming conventions with `Iterant`, Scala's standard library
    * and Cats-Effect.
    *
    * The `F` suffix now represents working with higher-kinded types,
    * e.g. `F[_]`, with restrictions like [[ObservableLike]],
    * [[monix.eval.TaskLike TaskLike]], `cats.effect.Sync`, etc.
    */
  @deprecated("Renamed to maxBy (no F suffix)", "3.0.0")
  def maxByF[K](key: A => K)(implicit K: Order[K]): Observable[A] = {
    // $COVERAGE-OFF$
    self.maxBy(key)
    // $COVERAGE-ON$
  }

  /** DEPRECATED — renamed to [[Observable.min]] in an effort to unify
    * the naming conventions with `Iterant`, Scala's standard library
    * and Cats-Effect.
    *
    * The `F` suffix now represents working with higher-kinded types,
    * e.g. `F[_]`, with restrictions like [[ObservableLike]],
    * [[monix.eval.TaskLike TaskLike]], `cats.effect.Sync`, etc.
    */
  @deprecated("Renamed to min (no F suffix)", "3.0.0")
  def minF[AA >: A](implicit A: Order[AA]): Observable[AA] = {
    // $COVERAGE-OFF$
    self.min(A)
    // $COVERAGE-ON$
  }

  /** DEPRECATED — renamed to [[Observable.minBy]] in an effort to unify
    * the naming conventions with `Iterant`, Scala's standard library
    * and Cats-Effect.
    *
    * The `F` suffix now represents working with higher-kinded types,
    * e.g. `F[_]`, with restrictions like [[ObservableLike]],
    * [[monix.eval.TaskLike TaskLike]], `cats.effect.Sync`, etc.
    */
  @deprecated("Renamed to minBy (no F suffix)", "3.0.0")
  def minByF[K](key: A => K)(implicit K: Order[K]): Observable[A] = {
    // $COVERAGE-OFF$
    self.minBy(key)
    // $COVERAGE-ON$
  }

  /** DEPRECATED — renamed to [[Observable.nonEmpty]] in an effort to unify
    * the naming conventions with `Iterant`, Scala's standard library
    * and Cats-Effect.
    *
    * The `F` suffix now represents working with higher-kinded types,
    * e.g. `F[_]`, with restrictions like [[ObservableLike]],
    * [[monix.eval.TaskLike TaskLike]], `cats.effect.Sync`, etc.
    */
  @deprecated("Renamed to nonEmpty (no F suffix)", "3.0.0")
  def nonEmptyF: Observable[Boolean] = {
    // $COVERAGE-OFF$
    self.nonEmpty
    // $COVERAGE-ON$
  }

  /** DEPRECATED — renamed to [[Observable.sum]] in an effort to unify
    * the naming conventions with `Iterant`, Scala's standard library
    * and Cats-Effect.
    *
    * The `F` suffix now represents working with higher-kinded types,
    * e.g. `F[_]`, with restrictions like [[ObservableLike]],
    * [[monix.eval.TaskLike TaskLike]], `cats.effect.Sync`, etc.
    */
  @deprecated("Renamed to sum (no F suffix)", "3.0.0")
  def sumF[AA >: A](implicit A: Numeric[AA]): Observable[AA] = {
    // $COVERAGE-OFF$
    self.sum(A)
    // $COVERAGE-ON$
  }

  /** DEPRECATED — renamed to [[Observable.firstOrElse]] in an effort to unify
    * the naming conventions with `Iterant`, Scala's standard library
    * and Cats-Effect.
    *
    * The `F` suffix now represents working with higher-kinded types,
    * e.g. `F[_]`, with restrictions like [[ObservableLike]],
    * [[monix.eval.TaskLike TaskLike]], `cats.effect.Sync`, etc.
    */
  @deprecated("Renamed to firstOrElse (no F suffix)", "3.0.0")
  def firstOrElseF[B >: A](default: => B): Observable[B] = {
    // $COVERAGE-OFF$
    self.firstOrElse(default)
    // $COVERAGE-ON$
  }

  /** DEPRECATED — renamed to [[Observable.headOrElse]] in an effort to unify
    * the naming conventions with `Iterant`, Scala's standard library
    * and Cats-Effect.
    *
    * The `F` suffix now represents working with higher-kinded types,
    * e.g. `F[_]`, with restrictions like [[ObservableLike]],
    * [[monix.eval.TaskLike TaskLike]], `cats.effect.Sync`, etc.
    */
  @deprecated("Renamed to headOrElse (no F suffix)", "3.0.0")
  def headOrElseF[B >: A](default: => B): Observable[B] = {
    // $COVERAGE-OFF$
    self.headOrElse(default)
    // $COVERAGE-ON$
  }
}
