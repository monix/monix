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

package monix.eval
package internal

import cats.effect.{ConcurrentEffect, IO}
import monix.eval.Task.Options
import monix.execution.annotations.UnsafeBecauseImpure
import monix.execution.compat.BuildFrom
import monix.execution.{Callback, Cancelable, CancelableFuture, Scheduler}

import scala.annotation.unchecked.uncheckedVariance
import scala.util.{Failure, Success, Try}

private[eval] object TaskDeprecated {
  /**
    * BinCompat trait describing deprecated `Task` operations.
    */
  private[eval] trait BinCompat[+A] { self: Task[A] =>
    /**
      * DEPRECATED — subsumed by [[Task.startAndForget startAndForget]].
      *
      * Renamed to `startAndForget` to be consistent with `start` which
      * also enforces an asynchronous boundary
      */
    @deprecated("Replaced with startAndForget", since = "3.0.0")
    def forkAndForget: Task[Unit] = {
      // $COVERAGE-OFF$
      self.startAndForget
      // $COVERAGE-ON$
    }
  }

  /**
    * Extension methods describing deprecated `Task` operations.
    */
  private[eval] trait Extensions[+A] extends Any {
    def self: Task[A]

    /**
      * DEPRECATED — renamed to [[Task.runToFuture runToFuture]], otherwise
      * due to overloading we can get a pretty bad conflict with the
      * callback-driven [[Task.runAsync]].
      *
      * The naming is also nice for discovery.
      */
    @UnsafeBecauseImpure
    @deprecated("Renamed to Task.runToFuture", since = "3.0.0")
    def runAsync(implicit s: Scheduler): CancelableFuture[A] = {
      // $COVERAGE-OFF$
      self.runToFuture(s)
      // $COVERAGE-ON$
    }

    /**
      * DEPRECATED — renamed to [[Task.runToFutureOpt runAsyncOpt]],
      * otherwise due to overloading we can get a pretty bad conflict with the
      * callback-driven [[Task.runToFutureOpt]].
      *
      * The naming is also nice for discovery.
      */
    @UnsafeBecauseImpure
    @deprecated("Renamed to Task.runAsyncOpt", since = "3.0.0")
    def runAsyncOpt(implicit s: Scheduler, opts: Task.Options): CancelableFuture[A] = {
      // $COVERAGE-OFF$
      self.runToFutureOpt(s, opts)
      // $COVERAGE-ON$
    }

    /**
      * DEPRECATED — switch to [[Task.runSyncStep]] or to [[Task.runToFuture]].
      *
      * The [[Task.runToFuture runToFuture]] operation that returns
      * [[monix.execution.CancelableFuture CancelableFuture]] will
      * return already completed future values, useful for low level
      * optimizations. All this `runSyncMaybe` did was to piggyback
      * on it.
      *
      * The reason for the deprecation is to reduce the unneeded
      * "run" overloads.
      */
    @UnsafeBecauseImpure
    @deprecated("Please use `Task.runSyncStep`", since = "3.0.0")
    def runSyncMaybe(implicit s: Scheduler): Either[CancelableFuture[A], A] = {
      // $COVERAGE-OFF$
      runSyncMaybeOptPrv(s, Task.defaultOptions.withSchedulerFeatures)
      // $COVERAGE-ON$
    }

    /**
      * DEPRECATED — switch to [[Task.runSyncStepOpt]] or to
      * [[Task.runToFutureOpt(implicit* runAsync]].
      *
      * The [[Task.runToFutureOpt(implicit* runAsyncOpt]] variant that returns
      * [[monix.execution.CancelableFuture CancelableFuture]] will
      * return already completed future values, useful for low level
      * optimizations. All this `runSyncMaybeOpt` did was to piggyback
      * on it.
      *
      * The reason for the deprecation is to reduce the unneeded
      * "run" overloads.
      */
    @UnsafeBecauseImpure
    @deprecated("Please use `Task.runAsyncOpt`", since = "3.0.0")
    def runSyncMaybeOpt(implicit s: Scheduler, opts: Options): Either[CancelableFuture[A], A] = {
      // $COVERAGE-OFF$
      runSyncMaybeOptPrv(s, opts)
      // $COVERAGE-ON$
    }

    private[this] def runSyncMaybeOptPrv(implicit s: Scheduler, opts: Options): Either[CancelableFuture[A], A] = {
      // $COVERAGE-OFF$
      val future = self.runToFutureOpt(s, opts)
      future.value match {
        case Some(value) =>
          value match {
            case Success(a) => Right(a)
            case Failure(e) => throw e
          }
        case None =>
          Left(future)
      }
      // $COVERAGE-ON$
    }

    /**
      * DEPRECATED — switch to [[Task.runToFuture]] in combination
      * with [[monix.execution.Callback.fromTry Callback.fromTry]]
      * instead.
      *
      * If for example you have a `Try[A] => Unit` function, you can
      * replace usage of `runOnComplete` with:
      *
      * `task.runAsync(Callback.fromTry(f))`
      *
      * A more common usage is via Scala's `Promise`, but with
      * a `Promise` reference this construct would be even more
      * efficient:
      *
      * `task.runAsync(Callback.fromPromise(p))`
      */
    @UnsafeBecauseImpure
    @deprecated("Please use `Task.runAsync`", since = "3.0.0")
    def runOnComplete(f: Try[A] => Unit)(implicit s: Scheduler): Cancelable = {
      // $COVERAGE-OFF$
      self.runAsync(Callback.fromTry(f))(s)
      // $COVERAGE-ON$
    }

    /** DEPRECATED — use [[Task.redeem redeem]] instead.
      *
      * [[Task.redeem]] is the same operation, but with a different name and the
      * function parameters in an inverted order, to make it consistent with `fold`
      * on `Either` and others (i.e. the function for error recovery is at the left).
      */
    @deprecated("Please use `Task.redeem`", since = "3.0.0-RC2")
    def transform[R](fa: A => R, fe: Throwable => R): Task[R] = {
      // $COVERAGE-OFF$
      self.redeem(fe, fa)
      // $COVERAGE-ON$
    }

    /** DEPRECATED — use [[Task.redeemWith redeemWith]] instead.
      *
      * [[Task.redeemWith]] is the same operation, but with a different name and the
      * function parameters in an inverted order, to make it consistent with `fold`
      * on `Either` and others (i.e. the function for error recovery is at the left).
      */
    @deprecated("Please use `Task.redeemWith`", since = "3.0.0-RC2")
    def transformWith[R](fa: A => Task[R], fe: Throwable => Task[R]): Task[R] = {
      // $COVERAGE-OFF$
      self.redeemWith(fe, fa)
      // $COVERAGE-ON$
    }

    /**
      * DEPRECATED — switch to [[Task.parZip2]], which has the same behavior.
      */
    @deprecated("Switch to Task.parZip2", since = "3.0.0-RC2")
    def zip[B](that: Task[B]): Task[(A, B)] = {
      // $COVERAGE-OFF$
      Task.mapBoth(self, that)((a, b) => (a, b))
      // $COVERAGE-ON$
    }

    /**
      * DEPRECATED — switch to [[Task.parMap2]], which has the same behavior.
      */
    @deprecated("Use Task.parMap2", since = "3.0.0-RC2")
    def zipMap[B, C](that: Task[B])(f: (A, B) => C): Task[C] =
      Task.mapBoth(self, that)(f)

    /** DEPRECATED — renamed to [[Task.executeAsync executeAsync]].
      *
      * The reason for the deprecation is the repurposing of the word "fork".
      */
    @deprecated("Renamed to Task!.executeAsync", "3.0.0")
    def executeWithFork: Task[A] = {
      // $COVERAGE-OFF$
      self.executeAsync
      // $COVERAGE-ON$
    }

    /** DEPRECATED — please use [[Task.flatMap flatMap]].
      *
      * The reason for the deprecation is that this operation is
      * redundant, as it can be expressed with `flatMap`, with the
      * same effect:
      * {{{
      *   import monix.eval.Task
      *
      *   val trigger = Task(println("do it"))
      *   val task = Task(println("must be done now"))
      *   trigger.flatMap(_ => task)
      * }}}
      *
      * The syntax provided by Cats can also help:
      * {{{
      *   import cats.syntax.all._
      *
      *   trigger *> task
      * }}}
      */
    @deprecated("Please use flatMap", "3.0.0")
    def delayExecutionWith(trigger: Task[Any]): Task[A] = {
      // $COVERAGE-OFF$
      trigger.flatMap(_ => self)
      // $COVERAGE-ON$
    }

    /** DEPRECATED — please use [[Task.flatMap flatMap]].
      *
      * The reason for the deprecation is that this operation is
      * redundant, as it can be expressed with `flatMap` and `map`,
      * with the same effect:
      *
      * {{{
      *   import monix.eval.Task
      *
      *   val task = Task(5)
      *   val selector = (n: Int) => Task(n.toString)
      *   task.flatMap(a => selector(a).map(_ => a))
      * }}}
      */
    @deprecated("Please rewrite in terms of flatMap", "3.0.0")
    def delayResultBySelector[B](selector: A => Task[B]): Task[A] = {
      // $COVERAGE-OFF$
      self.flatMap(a => selector(a).map(_ => a))
      // $COVERAGE-OFF$
    }

    /**
      * DEPRECATED — since Monix 3.0 the `Task` implementation has switched
      * to auto-cancelable run-loops by default (which can still be turned off
      * in its configuration).
      *
      * For ensuring the old behavior, you can use
      * [[Task.executeWithOptions executeWithOptions]].
      */
    @deprecated("Switch to executeWithOptions(_.enableAutoCancelableRunLoops)", "3.0.0")
    def cancelable: Task[A] = {
      // $COVERAGE-OFF$
      self.executeWithOptions(_.enableAutoCancelableRunLoops)
      // $COVERAGE-ON$
    }

    /**
      * DEPRECATED — subsumed by [[Task.start start]].
      *
      * To be consistent with cats-effect 1.1.0, `start` now
      * enforces an asynchronous boundary, being exactly the same
      * as `fork` from 3.0.0-RC1
      */
    @deprecated("Replaced with start", since = "3.0.0-RC2")
    def fork: Task[Fiber[A @uncheckedVariance]] = {
      // $COVERAGE-OFF$
      self.start
      // $COVERAGE-ON$
    }

    /** DEPRECATED — replace with usage of [[Task.runSyncStep]]:
      *
      * `task.coeval <-> Coeval(task.runSyncStep)`
      */
    @deprecated("Replaced with Coeval(task.runSyncStep)", since = "3.0.0-RC2")
    def coeval(implicit s: Scheduler): Coeval[Either[CancelableFuture[A], A]] = {
      // $COVERAGE-OFF$
      Coeval.eval(runSyncMaybeOptPrv(s, Task.defaultOptions.withSchedulerFeatures))
      // $COVERAGE-ON$
    }

    /**
      * DEPRECATED — replace with usage of [[Task.to]]:
      *
      * {{{
      *   import cats.effect.IO
      *   import monix.execution.Scheduler.Implicits.global
      *   import monix.eval.Task
      *
      *   Task(1 + 1).to[IO]
      * }}}
      */
    @deprecated("Switch to task.to[IO]", since = "3.0.0-RC3")
    def toIO(implicit eff: ConcurrentEffect[Task]): IO[A] = {
      // $COVERAGE-OFF$
      TaskConversions.toIO(self)(eff)
      // $COVERAGE-ON$
    }
  }

  private[eval] abstract class Companion {
    /** DEPRECATED — renamed to [[Task.parZip2]]. */
    @deprecated("Renamed to Task.parZip2", since = "3.0.0-RC2")
    def zip2[A1, A2, R](fa1: Task[A1], fa2: Task[A2]): Task[(A1, A2)] = {
      // $COVERAGE-OFF$
      Task.parZip2(fa1, fa2)
      // $COVERAGE-ON$
    }

    /** DEPRECATED — renamed to [[Task.parZip3]]. */
    @deprecated("Renamed to Task.parZip3", since = "3.0.0-RC2")
    def zip3[A1, A2, A3](fa1: Task[A1], fa2: Task[A2], fa3: Task[A3]): Task[(A1, A2, A3)] = {
      // $COVERAGE-OFF$
      Task.parZip3(fa1, fa2, fa3)
      // $COVERAGE-ON$
    }

    /** DEPRECATED — renamed to [[Task.parZip4]]. */
    @deprecated("Renamed to Task.parZip4", since = "3.0.0-RC2")
    def zip4[A1, A2, A3, A4](fa1: Task[A1], fa2: Task[A2], fa3: Task[A3], fa4: Task[A4]): Task[(A1, A2, A3, A4)] = {
      // $COVERAGE-OFF$
      Task.parZip4(fa1, fa2, fa3, fa4)
      // $COVERAGE-ON$
    }

    /** DEPRECATED — renamed to [[Task.parZip5]]. */
    @deprecated("Renamed to Task.parZip5", since = "3.0.0-RC2")
    def zip5[A1, A2, A3, A4, A5](
      fa1: Task[A1],
      fa2: Task[A2],
      fa3: Task[A3],
      fa4: Task[A4],
      fa5: Task[A5]): Task[(A1, A2, A3, A4, A5)] = {
      // $COVERAGE-OFF$
      Task.parZip5(fa1, fa2, fa3, fa4, fa5)
      // $COVERAGE-ON$
    }

    /** DEPRECATED — renamed to [[Task.parZip6]]. */
    @deprecated("Renamed to Task.parZip6", since = "3.0.0-RC2")
    def zip6[A1, A2, A3, A4, A5, A6](
      fa1: Task[A1],
      fa2: Task[A2],
      fa3: Task[A3],
      fa4: Task[A4],
      fa5: Task[A5],
      fa6: Task[A6]): Task[(A1, A2, A3, A4, A5, A6)] = {
      // $COVERAGE-OFF$
      Task.parZip6(fa1, fa2, fa3, fa4, fa5, fa6)
      // $COVERAGE-ON$
    }

    /** DEPRECATED — please use [[Task!.executeAsync .executeAsync]].
      *
      * The reason for the deprecation is the repurposing of the word "fork".
      */
    @deprecated("Please use Task!.executeAsync", "3.0.0")
    def fork[A](fa: Task[A]): Task[A] = {
      // $COVERAGE-OFF$
      fa.executeAsync
      // $COVERAGE-ON$
    }

    /** DEPRECATED — please use [[Task.executeOn .executeOn]].
      *
      * The reason for the deprecation is the repurposing of the word "fork".
      */
    @deprecated("Please use Task!.executeOn", "3.0.0")
    def fork[A](fa: Task[A], s: Scheduler): Task[A] = {
      // $COVERAGE-OFF$
      fa.executeOn(s)
      // $COVERAGE-ON$
    }

    /**
      * DEPRECATED — please use [[Task.from]].
      */
    @deprecated("Please use Task.from", "3.0.0")
    def fromEval[A](a: cats.Eval[A]): Task[A] = {
      // $COVERAGE-OFF$
      Coeval.from(a).to[Task]
      // $COVERAGE-ON$
    }

    /**
      * DEPRECATED — please use [[Task.from]].
      */
    @deprecated("Please use Task.from", "3.0.0")
    def fromIO[A](ioa: IO[A]): Task[A] = {
      // $COVERAGE-OFF$
      Task.from(ioa)
      // $COVERAGE-ON$
    }

    /** DEPRECATED — renamed to [[Task.parSequence]]. */
    @deprecated("Use parSequence", "3.2.0")
    def gather[A, M[X] <: Iterable[X]](in: M[Task[A]])(implicit bf: BuildFrom[M[Task[A]], A, M[A]]): Task[M[A]] = {
      // $COVERAGE-OFF$
      Task.parSequence(in)
      // $COVERAGE-ON$
    }

    /** DEPRECATED — renamed to [[Task.parSequenceN]] */
    @deprecated("Use parSequenceN", "3.2.0")
    def gatherN[A](parallelism: Int)(in: Iterable[Task[A]]): Task[List[A]] = {
      // $COVERAGE-OFF$
      Task.parSequenceN(parallelism)(in)
      // $COVERAGE-ON$
    }

    /** DEPRECATED — renamed to [[Task.parSequenceUnordered]] */
    @deprecated("Use parSequenceUnordered", "3.2.0")
    def gatherUnordered[A](in: Iterable[Task[A]]): Task[List[A]] = {
      // $COVERAGE-OFF$
      Task.parSequenceUnordered(in)
      // $COVERAGE-ON$
    }

    /** DEPRECATED — renamed to [[Task.parTraverse]] */
    @deprecated("Use parTraverse", "3.2.0")
    def wander[A, B, M[X] <: Iterable[X]](in: M[A])(f: A => Task[B])(implicit
      bf: BuildFrom[M[A], B, M[B]]): Task[M[B]] = {
      // $COVERAGE-OFF$
      Task.parTraverse(in)(f)
      // $COVERAGE-ON$
    }

    /** DEPRECATED — renamed to [[Task.parTraverseN]] */
    @deprecated("Use parTraverseN", "3.2.0")
    def wanderN[A, B](parallelism: Int)(in: Iterable[A])(f: A => Task[B]): Task[List[B]] = {
      // $COVERAGE-OFF$
      Task.parTraverseN(parallelism)(in)(f)
      // $COVERAGE-ON$
    }

    /** DEPRECATED — renamed to [[Task.parTraverseUnordered]] */
    @deprecated("Use parTraverseUnordered", "3.2.0")
    def wanderUnordered[A, B, M[X] <: Iterable[X]](in: M[A])(f: A => Task[B]): Task[List[B]] = {
      // $COVERAGE-OFF$
      Task.parTraverseUnordered(in)(f)
      // $COVERAGE-ON$
    }
  }
}
