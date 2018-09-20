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

import cats.effect.Resource
import monix.eval.Task.ContextSwitch
import monix.eval.internal.TaskContext
import monix.execution.annotations.{UnsafeBecauseImpure, UnsafeProtocol}
import monix.execution.exceptions.APIContractViolationException
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
  * run-loop implementation.
  *
  * Full example:
  *
  * {{{
  *   import monix.eval.{Task, TaskLocal}
  *
  *   val task: Task[Unit] = TaskLocal(0).use { local =>
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
  *   }
  *
  *   // For transporting locals over async boundaries defined by
  *   // Task, any Scheduler will do, however for transporting locals
  *   // over async boundaries managed by Future and others, you need
  *   // a `TracingScheduler` here:
  *   import monix.execution.Scheduler.Implicits.global
  *
  *   // Triggering actual execution
  *   val result = task.runAsync
  * }}}
  */
final class TaskLocal[A] private (ref: Local[A]) {
  import TaskLocal.checkPropagation

  /**
    * Returns [[monix.execution.misc.Local]] instance used in this [[TaskLocal]].
    *
    * Note that `TaskLocal.bind` will restore the original local value
    * on the thread where the `Task's` run-loop ends up so it might lead
    * to leaving local modified in other thread.
    */
  def local: Task[Local[A]] =
    Task(ref)

  /** Returns the current local value (in the `Task` context). */
  def read: Task[A] =
    checkPropagation(Task(ref.get))

  /** Updates the local value. */
  def write(value: A): Task[Unit] =
    checkPropagation(Task(ref.update(value)))

  /** Clears the local value, making it return its `default`. */
  def clear: Task[Unit] =
    checkPropagation(Task(ref.clear()))

  /** Binds the local var to a `value` for the duration of the given
    * `task` execution.
    *
    * {{{
    *   TaskLocal(0).use { local =>
    *     for {
    *       // Should yield 200 on execution, regardless of what value
    *       // we have in `local` at the time of evaluation
    *       value1 <- local.bind(100)(local.read.map(_ * 2))
    *
    *       // Should be reverted to the default
    *       value2 <- local.read
    *     } yield {
    *       println(s"$$value1, $$value2")
    *     }
    *   }
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
    bindL(Task.now(value))(task)

  /** Binds the local var to a `value` for the duration of the given
    * `task` execution, the `value` itself being lazily evaluated
    * in the [[Task]] context.
    *
    * {{{
    *   TaskLocal(0).use { local =>
    *     for {
    *       // Should yield 200 on execution, regardless of what value
    *       // we have in `local` at the time of evaluation
    *       value1 <- local.bindL(Task(100))(local.read.map(_ * 2))
    *
    *       // Should be reverted to the default
    *       value2 <- local.read
    *     } yield {
    *       println(s"$$value1, $$value2")
    *     }
    *   }
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
    local.flatMap { r =>
      val saved = r.value
      value.bracket { v =>
        r.update(v)
        task
      }(_ => restore(saved))
    }

  /** Clears the local var to the default for the duration of the
    * given `task` execution.
    *
    * {{{
    *   TaskLocal(0).use { local =>
    *     for {
    *       _ <- local.write(100)
    *       // Should yield 0 on execution, regardless of what value
    *       // we have in `local` at the time of evaluation
    *       value1 <- local.bindClear(local.read.map(_ * 2))
    *       // Should be reverted to 100
    *       value2 <- local.read
    *     } yield {
    *       println(s"$$value1, $$value2")
    *     }
    *   }
    * }}}
    *
    * @param task is the [[Task]] to wrap, having the local cleared,
    *        returning the default as the response to [[read]] queries
    *        and transported over asynchronous boundaries — on finish
    *        the local gets reset to the previous value
    */
  def bindClear[R](task: Task[R]): Task[R] =
    local.flatMap { r =>
      val saved = r.value
      r.clear()
      Task.unit.bracket(_ => task)(_ => restore(saved))
    }

  private def restore(value: Option[A]): Task[Unit] =
    Task(ref.value = value)
}

/**
  * Builders for [[TaskLocal]]
  *
  * @define resourceDesc The returned value is a Cats-Effect
  *         `Resource` because `TaskLocal` values can only be managed
  *         with an active context that ensures the underlying
  *         run-loop can manage locals (transport them over async
  *         boundaries via `Task`'s run-loop implementation). The
  *         usage of `Resource` ensures that the underlying engine
  *         switches ON the mode for working with locals, switching it
  *         OFF when it's done.
  *
  *         The Cats-Effect `Resource` data type is a pure and safe
  *         way to manage resources. See
  *         [[https://typelevel.org/cats-effect/datatypes/resource.html the documentation]].
  *
  * @define contextNote WARN: all usage happens inside the context of
  *         the given `Resource`, via `use`. If a `TaskLocal` value
  *         leaks outside of this context, its usage will start
  *         throwing `IllegalStateException` errors.
  */
object TaskLocal {
  /**
    * Alias for [[create]].
    */
  def apply[A](default: A): Resource[Task, TaskLocal[A]] =
    create(default)

  /**
    * Builds a [[TaskLocal]] value with the given strictly evaluated `default`.
    *
    * $resourceDesc
    *
    * Example:
    *
    * {{{
    *   import monix.eval.{Task, TaskLocal}
    *
    *   // Resource requires `use`, but its signature is like `flatMap`
    *   TaskLocal.create(10).use { local =>
    *     for {
    *       value1 <- local.read // 10
    *       _      <- local.write(20)
    *       _      <- Task.shift
    *       value2 <- local.read // 20
    *     } yield {
    *       value2
    *     }
    *   }
    * }}}
    *
    * $contextNote
    *
    * @param default is a value that gets returned in case the
    *        local was never updated (with [[TaskLocal.write write]])
    *        or in case it was cleared (with [[TaskLocal.clear]])
    */
  def create[A](default: A): Resource[Task, TaskLocal[A]] =
    createResource(Task(Unsafe.create(default)))

  /** Builds a [[TaskLocal]] reference with the given `default`,
    * being lazily evaluated, using [[Coeval]] to manage evaluation
    * (or compatible data types, see [[CoevalLike]]).
    *
    * Side effects in `default` are allowed, [[Coeval]] being a data type
    * that's safe for side effects.
    *
    * $resourceDesc
    *
    * $contextNote
    *
    * @param default is a value that gets returned in case the
    *        local was never updated (with [[TaskLocal.write write]])
    *        or in case it was cleared (with [[TaskLocal.clear]]),
    *        lazily evaluated and managed by [[Coeval]] (via [[CoevalLike]])
    */
  def createF[F[_], A](default: F[A])(implicit F: CoevalLike[F]): Resource[Task, TaskLocal[A]] =
    createResource(Task(Unsafe.createF(default)))

  /**
    * Wraps an unsafe [[monix.execution.misc.Local]] into a [[TaskLocal]].
    *
    * $resourceDesc
    *
    * $contextNote
    *
    * @param local is a [[monix.execution.misc.Local Local]] value evaluated
    *        lazily with any effect data type that is [[TaskLike]]
    */
  def wrap[F[_], A](local: F[Local[A]])(implicit F: TaskLike[F]): Resource[Task, TaskLocal[A]] =
    createResource(F.toTask(local).map(Unsafe.wrap))

  /**
    * Unsafe builders and helpers for dealing with [[TaskLocal]].
    *
    * A `TaskLocal` value should be considered a "resource" with a scope
    * in which it can be used, because internally locals need support
    * from Task's run-loop, support that needs to be turned ON and OFF.
    * Which is why for normal usage users are encouraged to use the
    * normal builders making use of Cats-Effect's `Resource`.
    *
    * But if you know what you're doing [[TaskLocal.Unsafe]] exposes
    * unsafe helpers and builders for dealing with `TaskLocal`, for when
    * you know what you're doing.
    *
    * The unsafe builders like [[Unsafe.create]], [[Unsafe.createF]] and
    * [[Unsafe.wrap]] are unsafe not only due to the protocol, but also
    * because they break referential transparency. So use with care.
    *
    * @define refTransparency WARNING (referential transparency): When creating
    *         a `TaskLocal` an underlying variable gets allocated and this in
    *         itself breaks referential transparency. This builder is "unsafe"
    *         because it breaks referential transparency.
    *
    *         Only use if you know what you're doing, but use the "safe"
    *         builders if you want to ensure you're doing functional programming,
    *         with all the benefits that brings.
    *
    * @define unsafeProtocol WARNING (protocol): The protocol for using this
    *         is considered unsafe because a proper locals context is not enforced.
    *
    *         Normally the underlying support for usage of `TaskLocal` needs to
    *         be turned ON and OFF in the `Task` implementation and usage of
    *         `Resource` values in the API helps with doing static type checks
    *         and to ensure that usage is correct. But in instances
    *         in which the user knows what he's doing, we can side step that
    *         and simply use locals in combination with [[Unsafe.scopedTask]]
    *         or [[Unsafe.scopedResource]].
    */
  @UnsafeProtocol object Unsafe {
    /**
      * Ensures that the underlying support for `TaskLocal` is enabled when
      * evaluating the given `task`.
      *
      * Example:
      *
      * {{{
      *   {
      *     TaskLocal.Unsafe.scopedTask {
      *       for {
      *         local <- Task(TaskLocal.Unsafe.create(0))
      *         _     <- local.write(10)
      *         _     <- Task.shift
      *         value <- local.read
      *       } yield value
      *     }
      *   }
      * }}}
      *
      * In this example all operations done on `TaskLocal` are enclosed in
      * a `scopedTask` call, so this ensures that the underlying support
      * for locals is enabled when evaluating the given task.
      *
      * $unsafeProtocol
      */
    def scopedTask[A](task: Task[A]): Task[A] =
      ContextSwitch(task, enablePropagation, disablePropagation)

    /** Ensures that the underlying support for `TaskLocal` is enabled when
      * evaluating the given resource.
      *
      * Example:
      *
      * {{{
      *   import cats.implicits._
      *   import cats.effect.Resource
      *
      *   TaskLocal.Unsafe.scopedResource(
      *     for {
      *       local1 <- Resource.liftF(Task(TaskLocal.Unsafe.create(0)))
      *       local2 <- Resource.liftF(Task(TaskLocal.Unsafe.create(0)))
      *     } yield (local1, local2)
      *   )
      * }}}
      *
      * In this example the created `Resource` value will ensure that the
      * underlying support for locals is enabled for the entire lifecycle
      * of the created resource.
      *
      * This helps with building and exporting `TaskLocal` values or
      * resources making use of `TaskLocal` values.
      *
      * $unsafeProtocol
      */
    def scopedResource[A](res: Resource[Task, A]): Resource[Task, A] =
      createResource(Task.unit).flatMap(_ => res)

    /** Unsafe builder for [[TaskLocal]] that initializes the value with
      * the given strictly evaluated `default`.
      *
      * $unsafeProtocol
      *
      * $refTransparency
      *
      * @see [[TaskLocal.create]] for the safe version
      */
    @UnsafeProtocol
    @UnsafeBecauseImpure
    def create[A](default: A): TaskLocal[A] = {
      new TaskLocal(new Local[A](default))
    }

    /** Unsafe builder for [[TaskLocal]] that initializes the value with
      * the given lazily evaluated `default`.
      *
      * $unsafeProtocol
      *
      * $refTransparency
      *
      * @see [[TaskLocal.createF]] for the safe version
      */
    @UnsafeProtocol
    @UnsafeBecauseImpure
    def createF[F[_], A](default: F[A])(implicit F: CoevalLike[F]): TaskLocal[A] = {
      val fa = F.toCoeval(default)
      new TaskLocal[A](new Local(fa.value()))
    }

    /** Unsafe builder for [[TaskLocal]] that wraps the given
      * [[monix.execution.misc.Local]].
      *
      * $unsafeProtocol
      *
      * $refTransparency
      *
      * @see [[TaskLocal.wrap]] for the safe version
      */
    @UnsafeProtocol
    @UnsafeBecauseImpure
    def wrap[A](local: Local[A]): TaskLocal[A] = {
      new TaskLocal[A](local)
    }
  }

  private[this] val disablePropagation: (Any, Throwable, TaskContext, TaskContext) => TaskContext =
    (_, _, old, current) => {
      val opts = current.options.copy(localContextPropagation = old.options.localContextPropagation)
      current.withOptions(opts)
    }

  private[this] val disablePropagationTask: Task[Unit] =
    ContextSwitch(
      Task.unit,
      ctx => ctx,
      disablePropagation)

  private[this] val enablePropagation: TaskContext => TaskContext =
    ctx => {
      if (!ctx.options.localContextPropagation)
        ctx.withOptions(ctx.options.enableLocalContextPropagation)
      else
        ctx
    }

  private[this] val readOption: Task[Boolean] =
    Task.Async(
      (ctx, cb) => cb.onSuccess(ctx.options.localContextPropagation),
      trampolineBefore = false,
      trampolineAfter = true)

  private def createResource[A](tla: Task[A]) = Resource[Task, A] {
    readOption.flatMap { hasLocalPropagation =>
      if (hasLocalPropagation)
        tla.map(a => (a, Task.unit))
      else
        ContextSwitch(tla.map((_, disablePropagationTask)), enablePropagation, null)
    }
  }

  private def checkPropagation[A](fa: Task[A]): Task[A] =
    ContextSwitch(fa, checkPropagationRef, null)

  private[this] val checkPropagationRef: TaskContext => TaskContext =
    ctx => {
      if (!ctx.options.localContextPropagation) {
        throw new APIContractViolationException(
          "Support for TaskLocal usage isn't active! Used Resource must have leaked, " +
          "or improper usage of TaskLocal.Unsafe functions. " +
          "See documentation at: https://monix.io/api/current/monix/eval/TaskLocal.html")
      }
      ctx
    }
}
