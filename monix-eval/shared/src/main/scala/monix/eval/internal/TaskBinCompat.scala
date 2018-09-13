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

package monix.eval
package internal

import monix.execution.{Cancelable, CancelableFuture, Scheduler}
import scala.annotation.unchecked.uncheckedVariance

private[eval] abstract class TaskBinCompat[+A] { self: Task[A] =>
  /** Deprecated — use [[redeem]] instead.
    *
    * [[Task.redeem]] is the same operation, but with a different name and the
    * function parameters in an inverted order, to make it consistent with `fold`
    * on `Either` and others (i.e. the function for error recovery is at the left).
    */
  @deprecated("Please use `Task.redeem`", since = "3.0.0-RC2")
  final def transform[R](fa: A => R, fe: Throwable => R): Task[R] = {
    // $COVERAGE-OFF$
    redeem(fe, fa)
    // $COVERAGE-ON$
  }

  /** Deprecated — use [[redeemWith]] instead.
    *
    * [[Task.redeemWith]] is the same operation, but with a different name and the
    * function parameters in an inverted order, to make it consistent with `fold`
    * on `Either` and others (i.e. the function for error recovery is at the left).
    */
  @deprecated("Please use `Task.redeemWith`", since = "3.0.0-RC2")
  final def transformWith[R](fa: A => Task[R], fe: Throwable => Task[R]): Task[R] = {
    // $COVERAGE-OFF$
    redeemWith(fe, fa)
    // $COVERAGE-ON$
  }

  /**
    * Deprecated — switch to [[Task.parZip2]], which has the same behavior.
    */
  @deprecated("Switch to Task.parZip2", since="3.0.0-RC2")
  final def zip[B](that: Task[B]): Task[(A, B)] = {
    // $COVERAGE-OFF$
    Task.mapBoth(this, that)((a,b) => (a,b))
    // $COVERAGE-ON$
  }

  /**
    * Deprecated — switch to [[Task.parMap2]], which has the same behavior.
    */
  @deprecated("Use Task.parMap2", since="3.0.0-RC2")
  final def zipMap[B,C](that: Task[B])(f: (A,B) => C): Task[C] =
    Task.mapBoth(this, that)(f)

  /** DEPRECATED - renamed to [[Task.executeAsync executeAsync]].
    *
    * The reason for the deprecation is the repurposing of the word "fork".
    */
  @deprecated("Renamed to Task!.executeAsync", "3.0.0")
  def executeWithFork: Task[A] = {
    // $COVERAGE-OFF$
    self.executeAsync
    // $COVERAGE-ON$
  }

  /** DEPRECATED - please use [[Task.flatMap flatMap]].
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

  /** DEPRECATED - please use [[Task.flatMap flatMap]].
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
    * DEPRECATED - subsumed by [[start]].
    *
    * To be consistent with cats-effect 1.0.0, `start` now
    * enforces an asynchronous boundary, being exactly the same
    * as `fork` from 3.0.0-RC1
    */
  @deprecated("Replaced with start", since="3.0.0-RC2")
  final def fork: Task[Fiber[A @uncheckedVariance]] = {
    // $COVERAGE-OFF$
    this.start
    // $COVERAGE-ON$
  }

  /** DEPRECATED - replace with usage of [[Task.runSyncMaybe]]:
    *
    * ```scala
    *   task.coeval <-> Coeval(task.runSyncMaybe)
    * ```
    */
  @deprecated("Replaced with start", since="3.0.0-RC2")
  final def coeval(implicit s: Scheduler): Coeval[Either[CancelableFuture[A], A]] = {
    // $COVERAGE-OFF$
    Coeval.eval(runSyncMaybe(s))
    // $COVERAGE-ON$
  }
}

private[eval] abstract class TaskBinCompatCompanion {

  /** DEPRECATED — please switch to [[Task.cancelable0[A](register* Task.cancelable0]].
    *
    * The reason for the deprecation is that the `Task.async` builder
    * is now aligned to the meaning of `cats.effect.Async` and thus
    * must yield tasks that are not cancelable.
    */
  @deprecated("Renamed to Task.cancelable0", since="3.0.0-RC2")
  private[internal] def async[A](register: (Scheduler, Callback[A]) => Cancelable): Task[A] = {
    // $COVERAGE-OFF$
    Task.cancelable0(register)
    // $COVERAGE-ON$
  }

  /** DEPRECATED — kept around as a package private in order to preserve
    * binary backwards compatibility.
    */
  @deprecated("Changed signature", since="3.0.0-RC2")
  private[internal] def create[A](register: (Scheduler, Callback[A]) => Cancelable): Task[A] = {
    // $COVERAGE-OFF$
    TaskCreate.cancelable0(register)
    // $COVERAGE-ON$
  }

  /** Deprecated — renamed to [[Task.parZip2]]. */
  @deprecated("Renamed to Task.parZip2", since = "3.0.0-RC2")
  def zip2[A1,A2,R](fa1: Task[A1], fa2: Task[A2]): Task[(A1,A2)] = {
    // $COVERAGE-OFF$
    Task.parZip2(fa1, fa2)
    // $COVERAGE-ON$
  }

  /** Deprecated — renamed to [[Task.parZip3]]. */
  @deprecated("Renamed to Task.parZip3", since = "3.0.0-RC2")
  def zip3[A1,A2,A3](fa1: Task[A1], fa2: Task[A2], fa3: Task[A3]): Task[(A1,A2,A3)] = {
    // $COVERAGE-OFF$
    Task.parZip3(fa1, fa2, fa3)
    // $COVERAGE-ON$
  }

  /** Deprecated — renamed to [[Task.parZip4]]. */
  @deprecated("Renamed to Task.parZip4", since = "3.0.0-RC2")
  def zip4[A1,A2,A3,A4](fa1: Task[A1], fa2: Task[A2], fa3: Task[A3], fa4: Task[A4]): Task[(A1,A2,A3,A4)] = {
    // $COVERAGE-OFF$
    Task.parZip4(fa1, fa2, fa3, fa4)
    // $COVERAGE-ON$
  }

  /** Deprecated — renamed to [[Task.parZip5]]. */
  @deprecated("Renamed to Task.parZip5", since = "3.0.0-RC2")
  def zip5[A1,A2,A3,A4,A5](fa1: Task[A1], fa2: Task[A2], fa3: Task[A3], fa4: Task[A4], fa5: Task[A5]): Task[(A1,A2,A3,A4,A5)] = {
    // $COVERAGE-OFF$
    Task.parZip5(fa1, fa2, fa3, fa4, fa5)
    // $COVERAGE-ON$
  }

  /** Deprecated — renamed to [[Task.parZip6]]. */
  @deprecated("Renamed to Task.parZip6", since = "3.0.0-RC2")
  def zip6[A1,A2,A3,A4,A5,A6](fa1: Task[A1], fa2: Task[A2], fa3: Task[A3], fa4: Task[A4], fa5: Task[A5], fa6: Task[A6]): Task[(A1,A2,A3,A4,A5,A6)] = {
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
}
