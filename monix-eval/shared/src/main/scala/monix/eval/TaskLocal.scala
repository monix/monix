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

import monix.execution.misc.Local

/** A `TaskLocal` is like a
  * [[monix.execution.misc.ThreadLocal ThreadLocal]]
  * that is pure and with a flexible scope, being processed in the
  * context of the [[Task]] data type.
  *
  * This data type wraps [[monix.execution.misc.Local]].
  *
  * Just like a `ThreadLocal`, usage of a `TaskLocal` is safe,
  * the state of all current locals being transported over
  * async boundaries (aka when threads get forked) by the `Task`
  * run-loop implementation, but only when the `Task` reference
  * gets executed with [[Task.Options.localContextPropagation]]
  * set to `true`.
  *
  * One way to achieve this is with [[Task.executeWithOptions]],
  * a single call is sufficient just before `runAsync`:
  *
  * {{{
  *   task.executeWithOptions(_.enableLocalContextPropagation)
  *     // triggers the actual execution
  *     .runAsync
  * }}}
  *
  * Another possibility is to use
  * [[Task.runAsyncOpt(implicit* .runAsyncOpt]] instead of `runAsync`
  * and specify the set of options implicitly:
  *
  * {{{
  *   implicit val opts = Task.defaultOptions.enableLocalContextPropagation
  *
  *   // Options passed implicitly
  *   val f = task.runAsyncOpt
  * }}}
  *
  * Full example:
  *
  * {{{
  *   import monix.eval.{Task, TaskLocal}
  *
  *   val local = TaskLocal(0)
  *
  *   val task: Task[Unit] =
  *     for {
  *       value1 <- local.read // value1 == 0
  *       _ <- local.write(100)
  *       value2 <- local.read // value2 == 100
  *       value3 <- local.bind(200)(local.read.map(_ * 2)) // value3 == 200 * 2
  *       value4 <- local.read // value4 == 100
  *       _ <- local.clear
  *       value5 <- local.read // value5 == 0
  *     } yield {
  *       // Should print 0, 100, 400, 100, 0
  *       println("value1: " + value1)
  *       println("value2: " + value2)
  *       println("value3: " + value3)
  *       println("value4: " + value4)
  *       println("value5: " + value5)
  *     }
  *
  *   // For transporting locals over async boundaries defined by
  *   // Task, any Scheduler will do, however for transporting locals
  *   // over async boundaries managed by Future and others, you need
  *   // a `TracingScheduler` here:
  *   import monix.execution.Scheduler.Implicits.global
  *
  *   // Needs enabling the "localContextPropagation" option
  *   // just before execution
  *   implicit val opts = Task.defaultOptions.enableLocalContextPropagation
  *
  *   // Triggering actual execution
  *   val f = task.runAsyncOpt
  * }}}
  */
final class TaskLocal[A] private (default: => A) {
  private[this] val ref = new Local(default)

  /** Returns [[monix.execution.misc.Local]] instance used in this [[TaskLocal]].
    *
    * Note that `TaskLocal.bind` will restore the original local value
    * on the thread where the `Task's` run-loop ends up so it might lead
    * to leaving local modified in other thread.
    */
  def local: Task[Local[A]] =
    Task.eval(ref)

  /** Returns the current local value (in the `Task` context). */
  def read: Task[A] =
    Task.eval(ref.get)

  /** Updates the local value. */
  def write(value: A): Task[Unit] =
    Task.eval(ref.update(value))

  /** Clears the local value, making it return its `default`. */
  def clear: Task[Unit] =
    Task.eval(ref.clear())

  /** Binds the local var to a `value` for the duration of the given
    * `task` execution.
    *
    * {{{
    *   // Should yield 200 on execution, regardless of what value
    *   // we have in `local` at the time of evaluation
    *   val task: Task[Int] =
    *     for {
    *       local <- TaskLocal(0)
    *       value <- local.bind(100)(local.read.map(_ * 2))
    *     } yield value
    * }}}
    *
    * @see [[bindL]] for the version with a lazy `value`.
    *
    * @param value is the value to be set in this local var when the
    *        task evaluation is triggered (aka lazily)
    *
    * @param task is the [[Task]] to wrap, having the given `value`
    *        as the response to [[read]] queries and transported
    *        over asynchronous boundaries — on finish the local gets
    *        reset to the previous value
    */
  def bind[R](value: A)(task: Task[R]): Task[R] =
    Task.suspend {
      val saved = ref.value
      ref.update(value)
      task.doOnFinish(_ => restore(saved))
    }

  /** Binds the local var to a `value` for the duration of the given
    * `task` execution, the `value` itself being lazily evaluated
    * in the [[Task]] context.
    *
    * {{{
    *   // Should yield 200 on execution, regardless of what value
    *   // we have in `local` at the time of evaluation
    *   val task: Task[Int] =
    *     for {
    *       local <- TaskLocal(0)
    *       value <- local.bindL(Task.eval(100))(local.read.map(_ * 2))
    *     } yield value
    * }}}
    *
    * @see [[bind]] for the version with a strict `value`.
    *
    * @param value is the value to be set in this local var when the
    *        task evaluation is triggered (aka lazily)
    *
    * @param task is the [[Task]] to wrap, having the given `value`
    *        as the response to [[read]] queries and transported
    *        over asynchronous boundaries — on finish the local gets
    *        reset to the previous value
    */
  def bindL[R](value: Task[A])(task: Task[R]): Task[R] =
    value.flatMap { value =>
      val saved = ref.value
      ref.update(value)
      task.doOnFinish(_ => restore(saved))
    }

  /** Clears the local var to the default for the duration of the
    * given `task` execution.
    *
    * {{{
    *   // Should yield 0 on execution, regardless of what value
    *   // we have in `local` at the time of evaluation
    *   val task: Task[Int] =
    *     for {
    *       local <- TaskLocal(0)
    *       value <- local.bindClear(local.read.map(_ * 2))
    *     } yield value
    * }}}
    *
    * @param task is the [[Task]] to wrap, having the local cleared,
    *        returning the default as the response to [[read]] queries
    *        and transported over asynchronous boundaries — on finish
    *        the local gets reset to the previous value
    */
  def bindClear[R](task: Task[R]): Task[R] =
    Task.suspend {
      val saved = ref.value
      ref.clear()
      task.doOnFinish(_ => restore(saved))
    }

  private def restore(value: Option[A]): Task[Unit] =
    Task.eval(ref.value = value)
}

/** Builders for [[TaskLocal]]
  *
  * @define refTransparent [[Task]] returned by this operation
  *         produces a new [[TaskLocal]] each time it is evaluated.
  *         To share a state between multiple consumers, pass
  *         [[TaskLocal]] as a parameter or use [[Task.memoize]]
  */
object TaskLocal {
  /** Builds a [[TaskLocal]] reference with the given default.
    *
    * $refTransparent
    *
    * @param default is a value that gets returned in case the
    *        local was never updated (with [[TaskLocal.write write]])
    *        or in case it was cleared (with [[TaskLocal.clear]])
    */
  def apply[A](default: A): Task[TaskLocal[A]] =
    Task.eval(new TaskLocal(default))

  /** Builds a [[TaskLocal]] reference with the given `default`,
    * being lazily evaluated, using [[Coeval]] to manage evaluation.
    *
    * Yes, side effects in the `default` are allowed, [[Coeval]]
    * being a data type that's safe for side effects.
    *
    * $refTransparent
    *
    * @param default is a value that gets returned in case the
    *        local was never updated (with [[TaskLocal.write write]])
    *        or in case it was cleared (with [[TaskLocal.clear]]),
    *        lazily evaluated and managed by [[Coeval]]
    */
  def lazyDefault[A](default: Coeval[A]): Task[TaskLocal[A]] =
    Task.eval(new TaskLocal[A](default.value))
}
