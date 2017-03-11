/*
 * Copyright (c) 2014-2017 by its authors. Some rights reserved.
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

import monix.eval.{Coeval, Task}
import monix.types.{Applicative, Monad}

import scala.collection.immutable.LinearSeq
import scala.util.Try

class IterantBuilders[F[_]] extends SharedDocs {
  /** Given a list of elements build a stream out of it. */
  def apply[A](elems: A*)(implicit F: Applicative[F]): Iterant[F,A] =
    Iterant.fromSeq(elems)(F)

  /** $builderNow */
  def now[A](a: A): Iterant[F,A] =
    Iterant.now(a)

  /** Alias for [[now]]. */
  def pure[A](a: A): Iterant[F,A] =
    Iterant.pure(a)

  /** $builderEval */
  def eval[A](a: => A)(implicit F: Applicative[F]): Iterant[F,A] =
    Iterant.eval(a)(F)

  /** $nextSDesc
    *
    * @param item $headParamDesc
    * @param rest $restParamDesc
    * @param stop $stopParamDesc
    */
  def nextS[A](item: A, rest: F[Iterant[F, A]], stop: F[Unit]): Iterant[F, A] =
    Iterant.nextS(item, rest, stop)

  /** $nextSeqSDesc
    *
    * @param items $cursorParamDesc
    * @param rest $restParamDesc
    * @param stop $stopParamDesc
    */
  def nextSeqS[A](items: Iterator[A], rest: F[Iterant[F, A]], stop: F[Unit]): Iterant[F, A] =
    Iterant.nextSeqS(items, rest, stop)

  /** $nextGenSDesc
    *
    * @param items $generatorParamDesc
    * @param rest $restParamDesc
    * @param stop $stopParamDesc
    */
  def nextGenS[A](items: Iterable[A], rest: F[Iterant[F, A]], stop: F[Unit]): Iterant[F, A] =
    Iterant.nextGenS(items, rest, stop)

  /** $suspendSDesc
    *
    * @param rest $restParamDesc
    * @param stop $stopParamDesc
    */
  def suspendS[A](rest: F[Iterant[F, A]], stop: F[Unit]): Iterant[F, A] =
    Iterant.suspendS(rest, stop)

  /** $lastSDesc
    *
    * @param item $lastParamDesc
    */
  def lastS[A](item: A): Iterant[F, A] =
    Iterant.lastS(item)

  /** $haltSDesc
    *
    * @param ex $exParamDesc
    */
  def haltS[A](ex: Option[Throwable]): Iterant[F, A] =
    Iterant.haltS(ex)

  /** $builderSuspendByName
    *
    * @param fa $suspendByNameParam
    */
  def suspend[A](fa: => Iterant[F, A])(implicit F: Applicative[F]): Iterant[F, A] =
    Iterant.suspend(fa)(F)

  /** Alias for [[suspend[A](fa* suspend]].
    *
    * $builderSuspendByName
    *
    * @param fa $suspendByNameParam
    */
  def defer[A](fa: => Iterant[F, A])(implicit F: Applicative[F]): Iterant[F, A] =
    Iterant.defer(fa)(F)

  /** $builderSuspendByF
    *
    * @param rest $restParamDesc
    */
  def suspend[A](rest: F[Iterant[F, A]])(implicit F: Applicative[F]): Iterant[F, A] =
    Iterant.suspend(rest)(F)

  /** $builderEmpty */
  def empty[A]: Iterant[F, A] =
    Iterant.empty

  /** $builderRaiseError */
  def raiseError[A](ex: Throwable): Iterant[F, A] =
    Iterant.raiseError(ex)

  /** $builderTailRecM */
  def tailRecM[A, B](a: A)(f: A => Iterant[F, Either[A, B]])(implicit F: Monad[F]): Iterant[F, B] =
    Iterant.tailRecM(a)(f)(F)

  /** $builderFromArray */
  def fromArray[A](xs: Array[A])(implicit F: Applicative[F]): Iterant[F, A] =
    Iterant.fromArray(xs)(F)

  /** $builderFromList */
  def fromList[A](xs: LinearSeq[A])(implicit F: Applicative[F]): Iterant[F, A] =
    Iterant.fromList(xs)(F)

  /** $builderFromIndexedSeq */
  def fromIndexedSeq[A](xs: IndexedSeq[A])(implicit F: Applicative[F]): Iterant[F, A] =
    Iterant.fromIndexedSeq(xs)(F)

  /** $builderFromSeq */
  def fromSeq[A](xs: Seq[A])(implicit F: Applicative[F]): Iterant[F, A] =
    Iterant.fromSeq(xs)(F)

  /** $builderFromIterable */
  def fromIterable[A](xs: Iterable[A])(implicit F: Applicative[F]): Iterant[F, A] =
    Iterant.fromIterable(xs)(F)

  /** $builderFromIterator */
  def fromIterator[A](xs: Iterator[A])(implicit F: Applicative[F]): Iterant[F, A] =
    Iterant.fromIterator(xs)(F)

  /** $builderRange
    *
    * @param from $rangeFromParam
    * @param until $rangeUntilParam
    * @param step $rangeStepParam
    * @return $rangeReturnDesc
    */
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
    type Builders = IterantTask.type
    def instance: Builders = IterantTask
  }

  /** For building [[Iterant]] instances powered by
    * [[monix.eval.Coeval Coeval]].
    */
  object FromCoeval extends From[Coeval] {
    type Builders = IterantCoeval.type
    def instance: Builders = IterantCoeval
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
  * [[monix.eval.Coeval Coeval]]
  */
object IterantCoeval extends IterantBuilders[Coeval]

/** Defines builders for [[Iterant]] instances powered by
  * [[monix.eval.Task Task]]
  */
object IterantTask extends IterantBuilders[Task]

