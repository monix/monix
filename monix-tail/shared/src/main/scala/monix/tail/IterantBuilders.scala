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

package monix.tail

import cats.Applicative
import cats.effect.Sync
import monix.eval.{Coeval, Task}
import monix.tail.batches.{Batch, BatchCursor}
import monix.tail.internal.{IterantIntervalAtFixedRate, IterantIntervalWithFixedDelay}

import scala.collection.immutable.LinearSeq
import scala.concurrent.duration.{Duration, FiniteDuration}
import scala.reflect.ClassTag
import scala.util.Try

class IterantBuilders[F[_]] {
  /** Given a list of elements build a stream out of it. */
  def of[A](elems: A*)(implicit F: Applicative[F]): Iterant[F,A] =
    Iterant.fromSeq(elems)(F)

  /** Aliased builder, see documentation for [[Iterant.now]]. */
  def now[A](a: A): Iterant[F,A] =
    Iterant.now(a)

  /** Aliased builder, see documentation for [[Iterant.pure]]. */
  def pure[A](a: A): Iterant[F,A] =
    Iterant.pure(a)

  /** Aliased builder, see documentation for [[Iterant.eval]]. */
  def eval[A](a: => A)(implicit F: Sync[F]): Iterant[F,A] =
    Iterant.eval(a)(F)

  /** Aliased builder, see documentation for [[Iterant.liftF]]. */
  def liftF[A](a: F[A])(implicit F: Applicative[F]): Iterant[F, A] =
    Iterant.liftF(a)

  /** Aliased builder, see documentation for [[Iterant.nextS]]. */
  def nextS[A](item: A, rest: F[Iterant[F, A]], stop: F[Unit]): Iterant[F, A] =
    Iterant.nextS(item, rest, stop)

  /** Aliased builder, see documentation for [[Iterant.nextCursorS]]. */
  def nextCursorS[A](cursor: BatchCursor[A], rest: F[Iterant[F, A]], stop: F[Unit]): Iterant[F, A] =
    Iterant.nextCursorS(cursor, rest, stop)

  /** Aliased builder, see documentation for [[Iterant.nextBatchS]]. */
  def nextBatchS[A](batch: Batch[A], rest: F[Iterant[F, A]], stop: F[Unit]): Iterant[F, A] =
    Iterant.nextBatchS(batch, rest, stop)

  /** Aliased builder, see documentation for [[Iterant.suspendS]]. */
  def suspendS[A](rest: F[Iterant[F, A]], stop: F[Unit]): Iterant[F, A] =
    Iterant.suspendS(rest, stop)

  /** Aliased builder, see documentation for [[Iterant.lastS]]. */
  def lastS[A](item: A): Iterant[F, A] =
    Iterant.lastS(item)

  /** Aliased builder, see documentation for [[Iterant.haltS]]. */
  def haltS[A](e: Option[Throwable]): Iterant[F, A] =
    Iterant.haltS(e)

  /** Aliased builder, see documentation for [[Iterant.suspend[F[_],A](fa* Iterant.suspend]]. */
  def suspend[A](fa: => Iterant[F, A])(implicit F: Sync[F]): Iterant[F, A] =
    Iterant.suspend(fa)(F)

  /** Aliased builder, see documentation for [[Iterant.defer]]. */
  def defer[A](fa: => Iterant[F, A])(implicit F: Sync[F]): Iterant[F, A] =
    Iterant.defer(fa)(F)

  /** Aliased builder, see documentation for [[Iterant.suspend[F[_],A](rest* Iterant.suspend]]. */
  def suspend[A](rest: F[Iterant[F, A]])(implicit F: Applicative[F]): Iterant[F, A] =
    Iterant.suspend(rest)(F)

  /** Aliased builder, see documentation for [[Iterant.empty]]. */
  def empty[A]: Iterant[F, A] =
    Iterant.empty

  /** Aliased builder, see documentation for [[Iterant.raiseError]]. */
  def raiseError[A](ex: Throwable): Iterant[F, A] =
    Iterant.raiseError(ex)

  /** Aliased builder, see documentation for [[Iterant.tailRecM]]. */
  def tailRecM[A, B](a: A)(f: A => Iterant[F, Either[A, B]])(implicit F: Sync[F]): Iterant[F, B] =
    Iterant.tailRecM(a)(f)(F)

  /** Aliased builder, see documentation for [[Iterant.fromArray]]. */
  def fromArray[A : ClassTag](xs: Array[A])(implicit F: Applicative[F]): Iterant[F, A] =
    Iterant.fromArray(xs)

  /** Aliased builder, see documentation for [[Iterant.fromList]]. */
  def fromList[A](xs: LinearSeq[A])(implicit F: Applicative[F]): Iterant[F, A] =
    Iterant.fromList(xs)(F)

  /** Aliased builder, see documentation for [[Iterant.fromIndexedSeq]]. */
  def fromIndexedSeq[A](xs: IndexedSeq[A])(implicit F: Applicative[F]): Iterant[F, A] =
    Iterant.fromIndexedSeq(xs)(F)

  /** Aliased builder, see documentation for [[Iterant.fromSeq]]. */
  def fromSeq[A](xs: Seq[A])(implicit F: Applicative[F]): Iterant[F, A] =
    Iterant.fromSeq(xs)(F)

  /** Aliased builder, see documentation for [[Iterant.fromIterable]]. */
  def fromIterable[A](xs: Iterable[A])(implicit F: Applicative[F]): Iterant[F, A] =
    Iterant.fromIterable(xs)(F)

  /** Aliased builder, see documentation for [[Iterant.fromIterator]]. */
  def fromIterator[A](xs: Iterator[A])(implicit F: Applicative[F]): Iterant[F, A] =
    Iterant.fromIterator(xs)(F)

  /** Aliased builder, see documentation for [[Iterant.range]]. */
  def range(from: Int, until: Int, step: Int = 1)(implicit F: Applicative[F]): Iterant[F, Int] =
    Iterant.range(from, until, step)(F)
}

object IterantBuilders {
  /** Type-class for quickly finding a suitable type and [[IterantBuilders]]
    * implementation for a given `F[_]` monadic context.
    */
  trait From[F[_]] {
    type Builders <: IterantBuilders[F]
    def instance: Builders
  }

  object From extends LowPriority {
    /** Implicit [[From]] instance for building [[Iterant]]
      * instances powered by [[monix.eval.Task Task]].
      */
    implicit val task: FromTask.type = FromTask

    /** Implicit [[From]] instance for building [[Iterant]]
      * instances powered by [[monix.eval.Coeval Coeval]].
      */
    implicit val coeval: FromCoeval.type = FromCoeval
  }

  private[tail] class LowPriority {
    /** For building generic [[Iterant]] instances. */
    implicit def fromAny[F[_]]: FromAny[F] =
      genericFromAny.asInstanceOf[FromAny[F]]
  }

  /** For building [[Iterant]] instances powered by
    * [[monix.eval.Task Task]].
    */
  object FromTask extends From[Task] {
    type Builders = IterantOfTask.type
    def instance: Builders = IterantOfTask
  }

  /** For building [[Iterant]] instances powered by
    * [[monix.eval.Coeval Coeval]].
    */
  object FromCoeval extends From[Coeval] {
    type Builders = IterantOfCoeval.type
    def instance: Builders = IterantOfCoeval
  }

  /** For building generic [[Iterant]] instances. */
  final class FromAny[F[_]] extends From[F] {
    type Builders = IterantBuilders[F]

    def instance: Builders =
      genericBuildersInstance.asInstanceOf[IterantBuilders[F]]
  }

  // Relying on type-erasure to build a generic instance.
  // Try here is being ignored.
  private val genericFromAny: FromAny[Try] =
    new FromAny[Try]

  // Relying on type-erasure to build a generic instance.
  // Try here is being ignored.
  private final val genericBuildersInstance: IterantBuilders[Try] =
    new IterantBuilders[Try]
}

/** Defines builders for [[Iterant]] instances powered by
  * [[monix.eval.Coeval Coeval]].
  */
object IterantOfCoeval extends IterantBuilders[Coeval]

/** Defines builders for [[Iterant]] instances powered by
  * [[monix.eval.Task Task]].
  *
  * @define intervalAtFixedRateDesc Creates an iterant that
  *         emits auto-incremented natural numbers (longs).
  *         at a fixed rate, as given by the specified `period`.
  *         The amount of time it takes to process an incoming
  *         value gets subtracted from provided `period`, thus
  *         created iterant tries to emit events spaced by the
  *         given time interval, regardless of how long further
  *         processing takes
  *
  * @define intervalWithFixedDelayDesc Creates an iterant that
  *         emits auto-incremented natural numbers (longs) spaced
  *         by a given time interval. Starts from 0 with no delay,
  *         after which it emits incremented numbers spaced by the
  *         `period` of time. The given `period` of time acts as a
  *         fixed delay between successive events.
  */
object IterantOfTask extends IterantBuilders[Task] {
  /** $intervalAtFixedRateDesc
    *
    * @param period period between 2 successive emitted values
    */
  def intervalAtFixedRate(period: FiniteDuration): Iterant[Task, Long] =
    IterantIntervalAtFixedRate(Duration.Zero, period)

  /** $intervalAtFixedRateDesc
    *
    * This version of the `intervalAtFixedRate` allows specifying an
    * `initialDelay` before first value is emitted
    *
    * @param initialDelay initial delay before emitting the first value
    * @param period period between 2 successive emitted values
    */
  def intervalAtFixedRate(initialDelay: FiniteDuration, period: FiniteDuration): Iterant[Task, Long] =
    IterantIntervalAtFixedRate(initialDelay, period)

  /** $intervalWithFixedDelayDesc
    *
    * Without having an initial delay specified, this overload
    * will immediately emit the first item, without any delays.
    *
    * @param delay the time to wait between 2 successive events
    */
  def intervalWithFixedDelay(delay: FiniteDuration): Iterant[Task, Long] =
    IterantIntervalWithFixedDelay(Duration.Zero, delay)

  /** $intervalWithFixedDelayDesc
    *
    * @param initialDelay is the delay to wait before emitting the first event
    * @param delay the time to wait between 2 successive events
    */
  def intervalWithFixedDelay(initialDelay: FiniteDuration, delay: FiniteDuration): Iterant[Task, Long] =
    IterantIntervalWithFixedDelay(initialDelay, delay)
}

