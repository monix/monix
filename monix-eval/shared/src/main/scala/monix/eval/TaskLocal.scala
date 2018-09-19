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
import monix.execution.annotations.UnsafeBecauseImpure
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
  *   val task: Task[Unit] =
  *     for {
  *       local <- TaskLocal(0)
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
  *   // Triggering actual execution
  *   val result = task.runAsync
  * }}}
  */
final class TaskLocal[A] private (
  ref: Local[A],
  ctx: TaskLocal.Context,
  guard: () => Unit) {

  /** Returns [[monix.execution.misc.Local]] instance used in this [[TaskLocal]].
    *
    * Note that `TaskLocal.bind` will restore the original local value
    * on the thread where the `Task's` run-loop ends up so it might lead
    * to leaving local modified in other thread.
    */
  def local: Task[Local[A]] =
    Task.eval { guard(); ref }

  /** Returns the current local value (in the `Task` context). */
  def read: Task[A] =
    Task.eval { guard(); ref.get }

  /** Updates the local value. */
  def write(value: A): Task[Unit] =
    Task.eval { guard(); ref.update(value) }

  /** Clears the local value, making it return its `default`. */
  def clear: Task[Unit] =
    Task.eval { guard(); ref.clear() }

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
    bindL(Task.now(value))(task)

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
    local.flatMap { r =>
      val saved = r.value
      r.clear()
      Task.unit.bracket(_ => task)(_ => restore(saved))
    }

  private def restore(value: Option[A]): Task[Unit] =
    Task.eval { guard(); ref.value = value }
}

/**
  * Builders for [[TaskLocal]]
  *
  * @define resourceDesc The returned value is a Cats-Effect `Resource`
  *         because `TaskLocal` values can only be managed via an active
  *         [[TaskLocal.Context]] that ensures the underlying run-loop can
  *         manage locals (transport them over async boundaries via
  *         `Task`'s run-loop implementation). The usage of `Resource`
  *         ensures that the underlying engine switches ON the mode
  *         for working with locals, switching it off when it's done.
  *
  *         The Cats-Effect `Resource` data type is a pure and safe
  *         way to manage resources. See
  *         [[https://typelevel.org/cats-effect/datatypes/resource.html the documentation]].
  *
  * @define contextNote NOTE: when using more than one `TaskLocal` value it
  *     might be better to use the builders exposed by [[TaskLocal.context]]
  *     instead.
  *
  *     WARN: all usage happens inside the context of the given `Resource`,
  *     via `use`. If a `TaskLocal` value leaks outside of this context,
  *     its usage will start throwing `IllegalStateException` errors.
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
    * @see [[Context.create]]
    *
    * @param default is a value that gets returned in case the
    *        local was never updated (with [[TaskLocal.write write]])
    *        or in case it was cleared (with [[TaskLocal.clear]])
    */
  def create[A](default: A): Resource[Task, TaskLocal[A]] =
    context.flatMap(locals => Resource.pure(locals.unsafe(default)))

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
    * @see [[Context.createF]]
    *
    * @param default is a value that gets returned in case the
    *        local was never updated (with [[TaskLocal.write write]])
    *        or in case it was cleared (with [[TaskLocal.clear]]),
    *        lazily evaluated and managed by [[Coeval]] (via [[CoevalLike]])
    */
  def createF[F[_], A](default: F[A])(implicit F: CoevalLike[F]): Resource[Task, TaskLocal[A]] =
    context.flatMap(locals => Resource.pure(locals.unsafeF(default)))

  /**
    * Wraps an unsafe [[monix.execution.misc.Local]] into a [[TaskLocal]].
    *
    * $resourceDesc
    *
    * $contextNote
    *
    * @see [[Context.wrap]]
    *
    * @param local is a [[monix.execution.misc.Local Local]] value evaluated
    *        lazily with any effect data type that is [[TaskLike]]
    */
  def wrap[F[_], A](local: F[Local[A]])(implicit F: TaskLike[F]): Resource[Task, TaskLocal[A]] =
    context.flatMap(locals => Resource.liftF(locals.wrap(local)))

  /**
    * Creates a new `TaskLocal` context, managed via Cats-Effect's `Resource`.
    *
    * $resourceDesc
    *
    * Example:
    *
    * {{{
    *   import monix.eval.{Task, TaskLocal}
    *
    *   // Here `use` will open a new `Context`
    *   TaskLocal.context.use { locals =>
    *     for {
    *       // The builder is invoked via the locals context:
    *       local <- locals.create(0)
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
    * }}}
    *
    * WARNING: if locals leak the context of the provided `Resource`, usage
    * is illegal and they start throwing `IllegalStateException`.
    *
    * As example this will throw an exception:
    *
    * {{{
    *   // BROKEN sample!
    *   for {
    *     // This leaks the local reference
    *     local <- TaskLocal.context.use(_.create(0))
    *     // Will throw error because no longer in a locals context!
    *     _     <- local.get
    *   } yield ()
    * }}}
    */
  def context: Resource[Task, Context] =
    resourceRef

  /**
    * The [[TaskLocal.Context]] is a resource that can be used for creating
    * and using locals.
    *
    * In usage of [[Task]] the runtime needs to cooperate with `TaskLocal`
    * values, to safely transport them over asynchronous boundaries. In order
    * to turn ON this support, usage of locals needs to be within the scope
    * of a locals context, i.e. with an active [[TaskLocal.Context]].
    *
    * Therefore a `TaskLocal.Context` is a resource that can be used to
    * create `TaskLocal` values and:
    *
    *   1. once created it activates `Task`'s underlying support for locals
    *   1. once closed it deactivates that support and locals, should they leak
    *      outside the context of their `Resource` will start throwing exceptions
    *
    * @see [[TaskLocal.context]] for creating such a context.
    *
    * @define refTransparency When creating a `TaskLocal` an underlying
    *         variable gets allocated and this in itself breaks referential
    *         transparency. This builder is "unsafe" because it breaks
    *         referential transparency.
    *
    *         Only use if you know what you're doing, but use the "safe"
    *         builders if you want to ensure you're doing functional programming,
    *         with all the benefits that brings.
    */
  final class Context private[TaskLocal] (isActive: Coeval[Boolean]) {
    /** Builds a [[TaskLocal]] reference with the given default in the
      * current context.
      *
      * @param default is a value that gets returned in case the
      *        local was never updated (with [[TaskLocal.write write]])
      *        or in case it was cleared (with [[TaskLocal.clear]])
      */
    def create[A](default: A): Task[TaskLocal[A]] =
      Task.eval(unsafe(default))

    /** Builds a [[TaskLocal]] reference with the given `default`,
      * being lazily evaluated, using [[Coeval]] to manage evaluation
      * (or compatible data types, see [[CoevalLike]]).
      *
      * Side effects in `default` are allowed, [[Coeval]] being a data type
      * that's safe for side effects.
      *
      * @param default is a value that gets returned in case the
      *        local was never updated (with [[TaskLocal.write write]])
      *        or in case it was cleared (with [[TaskLocal.clear]]),
      *        lazily evaluated and managed by [[Coeval]] (via [[CoevalLike]])
      */
    def createF[F[_], A](default: F[A])(implicit F: CoevalLike[F]): Task[TaskLocal[A]] =
      Task.eval(unsafeF(default))

    /** Wraps an unsafe [[monix.execution.misc.Local]] into a [[TaskLocal]].
      *
      * @param local is a [[monix.execution.misc.Local Local]] value evaluated
      *        lazily with any effect data type that is [[TaskLike]]
      */
    def wrap[F[_], A](local: F[Local[A]])(implicit F: TaskLike[F]): Task[TaskLocal[A]] =
      F.toTask(local).map(unsafeWrap)

    /** Unsafe builder for [[TaskLocal]] that initializes the value with
      * the given strictly evaluated `default`.
      *
      * $refTransparency
      *
      * @see [[create]] for the safe version
      */
    @UnsafeBecauseImpure
    def unsafe[A](default: A): TaskLocal[A] = {
      if (isActive())
        new TaskLocal(new Local[A](default), this, guard)
      else
        reject()
    }

    /** Unsafe builder for [[TaskLocal]] that initializes the value with
      * the given lazily evaluated `default`.
      *
      * $refTransparency
      *
      * @see [[createF]] for the safe version
      */
    @UnsafeBecauseImpure
    def unsafeF[F[_], A](default: F[A])(implicit F: CoevalLike[F]): TaskLocal[A] = {
      if (isActive()) {
        val fa = F.toCoeval(default)
        new TaskLocal[A](new Local(fa.value()), this, guard)
      } else {
        reject()
      }
    }

    /** Unsafe builder for [[TaskLocal]] that wraps the given
      * [[monix.execution.misc.Local]].
      *
      * $refTransparency
      *
      * @see [[wrap]] for the safe version
      */
    @UnsafeBecauseImpure
    def unsafeWrap[A](local: Local[A]): TaskLocal[A] = {
      if (isActive())
        new TaskLocal[A](local, this, guard)
      else
        reject()
    }

    private[this] val guard: () => Unit =
      () => { if (!isActive()) reject() }
  }

  private def reject(): Nothing = {
    throw new IllegalStateException("TaskLocal.Context is no longer available")
  }

  private[this] def disablePropagation(cancel: Task[Unit]): Task[Unit] =
    ContextSwitch(
      cancel,
      ctx => ctx,
      (_, _, _, current) => {
        val opts = current.options.copy(localContextPropagation = false)
        current.withOptions(opts)
      })

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

  private[this] val resourceRef = Resource[Task, Context] {
    readOption.flatMap { hasLocalPropagation =>
      var isActive = true
      val ctx = new Context(Coeval(isActive))
      val cancel = Task { isActive = false }

      if (hasLocalPropagation)
        Task.now((ctx, cancel))
      else
        ContextSwitch(Task((ctx, disablePropagation(cancel))), enablePropagation, null)
    }
  }
}
