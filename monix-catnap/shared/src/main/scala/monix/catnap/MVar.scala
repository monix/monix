/*
 * Copyright (c) 2014-2019 by The Monix Project Developers.
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

package monix.catnap

import cats.effect.concurrent.{MVar => CatsMVar}
import cats.effect.{Async, Concurrent, ContextShift}
import monix.catnap.internal.AsyncUtils
import monix.execution.atomic.PaddingStrategy
import monix.execution.atomic.PaddingStrategy.NoPadding
import monix.execution.internal.GenericVar
import monix.execution.internal.GenericVar.Id

/** A mutable location, that is either empty or contains
  * a value of type `A`.
  *
  * It has the following fundamental atomic operations:
  *
  *  - [[put]] which fills the var if empty, or blocks
  *    (asynchronously) until the var is empty again
  *  - [[tryPut]] which fills the var if empty. returns true if successful
  *  - [[take]] which empties the var if full, returning the contained
  *    value, or blocks (asynchronously) otherwise until there is
  *    a value to pull
  *  - [[tryTake]] empties if full, returns None if empty.
  *  - [[read]] which reads the current value without touching it,
  *    assuming there is one, or otherwise it waits until a value
  *    is made available via `put`
  *  - [[tryRead]] returns `Some(a)` if full, without modifying the var,
  *    or else returns `None`
  *  - [[isEmpty]] returns true if currently empty
  *
  * The `MVar` is appropriate for building synchronization
  * primitives and performing simple inter-thread communications.
  * If it helps, it's similar with a `BlockingQueue(capacity = 1)`,
  * except that it is pure and that doesn't block any threads, all
  * waiting being done asynchronously.
  *
  * Given its asynchronous, non-blocking nature, it can be used on
  * top of Javascript as well.
  *
  * N.B. this is a reimplementation of the interface exposed in Cats-Effect, see:
  * [[https://typelevel.org/cats-effect/concurrency/mvar.html cats.effect.concurrent.MVar]]
  *
  * Inspired by
  * [[https://hackage.haskell.org/package/base/docs/Control-Concurrent-MVar.html Control.Concurrent.MVar]]
  * from Haskell.
  */

final class MVar[F[_], A] private (underlying: MVar.Impl[F, A]) extends CatsMVar[F, A] {

  /** Returns `true` if the var is empty, `false` if full. */
  def isEmpty: F[Boolean] =
    underlying.isEmpty

  /**
    * Fills the `MVar` if it is empty, or blocks (asynchronously)
    * if the `MVar` is full, until the given value is next in
    * line to be consumed on [[take]].
    *
    * This operation is atomic.
    *
    * @return a task that on evaluation will complete when the
    *         `put` operation succeeds in filling the `MVar`,
    *         with the given value being next in line to
    *         be consumed
    */
  def put(a: A): F[Unit] =
    underlying.put(a)

  /**
    * Fill the `MVar` if we can do it without blocking,
    *
    * @return whether or not the put succeeded
    */
  def tryPut(a: A): F[Boolean] =
    underlying.tryPut(a)

  /**
    * Empties the `MVar` if full, returning the contained value,
    * or blocks (asynchronously) until a value is available.
    *
    * This operation is atomic.
    *
    * @return a task that on evaluation will be completed after
    *         a value was retrieved
    */
  def take: F[A] =
    underlying.take

  /** Empty the `MVar` if full
    *
    * @return an Option holding the current value, None means it was empty
    */
  def tryTake: F[Option[A]] =
    underlying.tryTake

  /** Tries reading the current value, or blocks (asynchronously)
    * until there is a value available.
    *
    * This operation is atomic.
    *
    * @return a task that on evaluation will be completed after
    *         a value has been read
    */
  def read: F[A] =
    underlying.read

  /** Tries reading the current value, returning `Some(a)` if the var
    * is full, but without modifying the var in any way. Or `None`
    * if the var is empty.
    */
  def tryRead: F[Option[A]] =
    underlying.tryRead
}

object MVar {
  /**
    * Builds an [[MVar]] value for `F` data types that are either
    * `Concurrent` or `Async`.
    *
    * Due to `Concurrent`'s capabilities, the yielded values by [[MVar.take]]
    * and [[MVar.put]] are cancelable. For `Async` however this isn't
    * guaranteed, although the implementation does rely on `bracket`,
    * so it might be.
    *
    * This builder uses the
    * [[https://typelevel.org/cats/guidelines.html#partially-applied-type-params Partially-Applied Type]]
    * technique.
    *
    * For creating an empty `MVar`:
    *
    * `MVar[IO].empty[Int]() <-> MVar.empty[IO, Int]()`
    *
    * For creating an `MVar` with an initial value:
    *
    * `MVar[IO].of("hello") <-> MVar.of[IO, String]("hello")`
    *
    * @see [[of]] and [[empty]]
    */
  def apply[F[_]](implicit F: Concurrent[F] OrElse Async[F]): ApplyBuilders[F] =
    new ApplyBuilders[F](F)

  /**
    * Builds an [[MVar]] instance with an `initial` value.
    */
  def of[F[_], A](initial: A, ps: PaddingStrategy = NoPadding)(
    implicit F: Concurrent[F] OrElse Async[F],
    cs: ContextShift[F]): F[MVar[F, A]] = {

    F.fold(
      implicit F => F.delay(new MVar(new ConcurrentImpl(Some(initial), ps))),
      implicit F => F.delay(new MVar(new AsyncImpl(Some(initial), ps)))
    )
  }

  /**
    * Builds an empty [[MVar]] instance.
    */
  def empty[F[_], A](
    ps: PaddingStrategy = NoPadding)(implicit F: Concurrent[F] OrElse Async[F], cs: ContextShift[F]): F[MVar[F, A]] = {

    F.fold(
      implicit F => F.delay(new MVar(new ConcurrentImpl(None, ps))),
      implicit F => F.delay(new MVar(new AsyncImpl(None, ps)))
    )
  }

  /**
    * Returned by the [[apply]] builder.
    */
  final class ApplyBuilders[F[_]](val F: Concurrent[F] OrElse Async[F]) extends AnyVal {
    /**
      * Builds an `MVar` with an initial value.
      *
      * @see documentation for [[MVar.of]]
      */
    def of[A](a: A, ps: PaddingStrategy = NoPadding)(implicit cs: ContextShift[F]): F[MVar[F, A]] =
      MVar.of(a, ps)(F, cs)

    /**
      * Builds an empty `MVar`.
      *
      * @see documentation for [[MVar.empty]]
      */
    def empty[A](ps: PaddingStrategy = NoPadding)(implicit cs: ContextShift[F]): F[MVar[F, A]] =
      MVar.empty(ps)(F, cs)
  }

  private trait Impl[F[_], A] { self: GenericVar[A, F[Unit]] =>
    implicit def F: Async[F]
    implicit def cs: ContextShift[F]

    protected def create[T](k: (Either[Throwable, T] => Unit) => F[Unit]): F[T]

    final def isEmpty: F[Boolean] =
      F.delay(unsafeIsEmpty())
    final def tryPut(a: A): F[Boolean] =
      F.delay(unsafeTryPut(a))
    final def tryTake: F[Option[A]] =
      F.delay(unsafeTryTake())
    final def tryRead: F[Option[A]] =
      F.delay(unsafeTryRead())

    final def put(a: A): F[Unit] =
      F.suspend {
        if (unsafeTryPut(a))
          F.unit
        else
          F.flatMap(AsyncUtils.cancelable[F, Unit](unsafePut(a, _)))(bindFork)
      }

    final def take: F[A] =
      F.suspend[A] {
        unsafeTryTake() match {
          case Some(a) => F.pure(a)
          case None =>
            F.flatMap(AsyncUtils.cancelable[F, A](unsafeTake))(bindForkA.asInstanceOf[A => F[A]])
        }
      }

    final def read: F[A] =
      AsyncUtils.cancelable { cb =>
        unsafeRead(cb)
      }

    private[this] val bindFork: (Unit => F[Unit]) = {
      val shift = cs.shift
      _ => shift
    }

    private[this] val bindForkA: (Any => F[Any]) = {
      val shift = cs.shift
      x => F.map(shift)(_ => x)
    }
  }

  private final class AsyncImpl[F[_], A](initial: Option[A], ps: PaddingStrategy)(
    implicit val F: Async[F],
    val cs: ContextShift[F])
    extends GenericVar[A, F[Unit]](initial, ps) with Impl[F, A] {

    protected def create[T](k: (Either[Throwable, T] => Unit) => F[Unit]): F[T] =
      AsyncUtils.cancelable(k)
    override protected def makeCancelable(f: Id => Unit, id: Id): F[Unit] =
      F.delay(f(id))
    override protected def emptyCancelable: F[Unit] =
      F.unit
  }

  private final class ConcurrentImpl[F[_], A](initial: Option[A], ps: PaddingStrategy)(
    implicit val F: Concurrent[F],
    val cs: ContextShift[F])
    extends GenericVar[A, F[Unit]](initial, ps) with Impl[F, A] {

    protected def create[T](k: (Either[Throwable, T] => Unit) => F[Unit]): F[T] =
      F.cancelable(k)
    override protected def makeCancelable(f: Id => Unit, id: Id): F[Unit] =
      F.delay(f(id))
    override protected def emptyCancelable: F[Unit] =
      F.unit
  }
}
