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

package monix.types.instances

import cats.Eval
import monix.async.Task
import monix.types.Async
import scala.concurrent.duration.FiniteDuration

object task extends TaskInstances

trait TaskInstances {
  /** Type-class instances for [[monix.async.Task]]. */
  implicit val taskInstances: Async[Task] =
    new Async[Task] {
      override def pure[A](x: A): Task[A] = Task.now(x)
      override def pureEval[A](x: Eval[A]): Task[A] = Task.evalAlways(x.value)
      override def delayedEval[A](delay: FiniteDuration, a: Eval[A]): Task[A] =
        Task.evalAlways(a.value).delayExecution(delay)

      override def now[A](a: A): Task[A] = Task.now(a)
      override def evalAlways[A](a: => A): Task[A] = Task.evalAlways(a)
      override def evalOnce[A](a: => A): Task[A] = Task.evalOnce(a)
      override def memoize[A](fa: Task[A]): Task[A] = fa.memoize

      override def firstStartedOf[A](seq: Seq[Task[A]]): Task[A] =
        Task.firstCompletedOf(seq)
      override def delayExecution[A](fa: Task[A], timespan: FiniteDuration): Task[A] =
        fa.delayExecution(timespan)
      override def delayExecutionWith[A, B](fa: Task[A], trigger: Task[B]): Task[A] =
        fa.delayExecutionWith(trigger)
      override def delayResult[A](fa: Task[A], timespan: FiniteDuration): Task[A] =
        fa.delayResult(timespan)
      override def delayResultBySelector[A, B](fa: Task[A])(selector: (A) => Task[B]): Task[A] =
        fa.delayResultBySelector(selector)

      override def onErrorHandleWith[A](fa: Task[A])(f: Throwable => Task[A]): Task[A] =
        fa.onErrorHandleWith(f)
      override def onErrorHandle[A](fa: Task[A])(f: Throwable => A): Task[A] =
        fa.onErrorHandle(f)
      override def onErrorFallbackTo[A](fa: Task[A], other: Eval[Task[A]]): Task[A] =
        fa.onErrorFallbackTo(other.value)
      override def onErrorRetry[A](fa: Task[A], maxRetries: Long): Task[A] =
        fa.onErrorRetry(maxRetries)
      override def onErrorRetryIf[A](fa: Task[A])(p: (Throwable) => Boolean): Task[A] =
        fa.onErrorRetryIf(p)
      override def failed[A](fa: Task[A]): Task[Throwable] =
        fa.failed
      override def onErrorRecoverWith[A](fa: Task[A])(pf: PartialFunction[Throwable, Task[A]]): Task[A] =
        fa.onErrorRecoverWith(pf)
      override def onErrorRecover[A](fa: Task[A])(pf: PartialFunction[Throwable, A]): Task[A] =
        fa.onErrorRecover(pf)

      override def zipList[A](sources: Seq[Task[A]]): Task[Seq[A]] =
        Task.sequence(sources)
      override def zipWith2[A1, A2, R](fa1: Task[A1], fa2: Task[A2])(f: (A1, A2) => R): Task[R] =
        Task.map2(fa1, fa2)(f)
      override def flatMap[A, B](fa: Task[A])(f: (A) => Task[B]): Task[B] =
        fa.flatMap(f)
      override def raiseError[A](e: Throwable): Task[A] =
        Task.error(e)
      override def map[A, B](fa: Task[A])(f: (A) => B): Task[B] =
        fa.map(f)
      override def flatten[A](ffa: Task[Task[A]]): Task[A] =
        ffa.flatten

      override def timeout[A](fa: Task[A], timespan: FiniteDuration): Task[A] =
        fa.timeout(timespan)
      override def timeoutTo[A](fa: Task[A], timespan: FiniteDuration, backup: Eval[Task[A]]): Task[A] =
        fa.timeoutTo(timespan, backup.value)
    }
}
