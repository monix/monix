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

package monix.iterant

import monix.eval.{Coeval, Task}
import monix.types.{Applicative, Monad}
import scala.collection.immutable.LinearSeq
import scala.reflect.ClassTag

trait IterantBuilders[F[_], Self[+A] <: Iterant[F,A]] extends SharedDocs {
  /** Given a list of elements build a stream out of it. */
  def apply[A : ClassTag](elems: A*)(implicit F: Applicative[F]): Self[A]

  /** $builderNow */
  def now[A](a: A)(implicit F: Applicative[F]): Self[A]

  /** Alias for [[now]]. */
  def pure[A](a: A)(implicit F: Applicative[F]): Self[A]

  /** $builderEval */
  def eval[A](a: => A)(implicit F: Applicative[F]): Self[A]

  /** $nextSDesc
    *
    * @param item $headParamDesc
    * @param rest $restParamDesc
    * @param stop $stopParamDesc
    */
  def nextS[A](item: A, rest: F[Self[A]], stop: F[Unit]): Self[A]

  /** $nextSeqSDesc
    *
    * @param items $cursorParamDesc
    * @param rest $restParamDesc
    * @param stop $stopParamDesc
    */
  def nextSeqS[A](items: Cursor[A], rest: F[Self[A]], stop: F[Unit]): Self[A]

  /** $suspendSDesc
    *
    * @param rest $restParamDesc
    * @param stop $stopParamDesc
    */
  def suspendS[A](rest: F[Self[A]], stop: F[Unit]): Self[A]

  /** $lastSDesc
    *
    * @param item $lastParamDesc
    */
  def lastS[A](item: A): Self[A]

  /** $haltSDesc
    *
    * @param ex $exParamDesc
    */
  def haltS[A](ex: Option[Throwable]): Self[A]

  /** $builderSuspendByName
    *
    * @param fa $suspendByNameParam
    */
  def suspend[A](fa: => Self[A])(implicit F: Applicative[F]): Self[A]

  /** Alias for [[suspend[A](fa* suspend]].
    *
    * $builderSuspendByName
    *
    * @param fa $suspendByNameParam
    */
  def defer[A](fa: => Self[A])(implicit F: Applicative[F]): Self[A]

  /** $builderSuspendByF
    *
    * @param rest $restParamDesc
    */
  def suspend[A](rest: F[Self[A]])(implicit F: Applicative[F]): Self[A]

  /** $builderEmpty */
  def empty[A]: Self[A]

  /** $builderRaiseError */
  def raiseError[A](ex: Throwable): Self[A]

  /** $builderTailRecM */
  def tailRecM[A, B](a: A)(f: A => Self[Either[A, B]])(implicit F: Monad[F]): Self[B]

  /** $builderFromArray */
  def fromArray[A](xs: Array[A])(implicit F: Applicative[F]): Self[A]

  /** $builderFromList */
  def fromList[A](xs: LinearSeq[A])(implicit F: Applicative[F]): Self[A]

  /** $builderFromIndexedSeq */
  def fromIndexedSeq[A](xs: IndexedSeq[A])(implicit F: Applicative[F]): Self[A]

  /** $builderFromSeq */
  def fromSeq[A](xs: Seq[A])(implicit F: Applicative[F]): Self[A]

  /** $builderFromIterable */
  def fromIterable[A](xs: Iterable[A])(implicit F: Applicative[F]): Self[A]

  /** $builderFromIterator */
  def fromIterator[A](xs: Iterator[A])(implicit F: Applicative[F]): Self[A]

  /** $builderRange
    *
    * @param from $rangeFromParam
    * @param until $rangeUntilParam
    * @param step $rangeStepParam
    * @return $rangeReturnDesc
    */
  def range(from: Int, until: Int, step: Int = 1)(implicit F: Applicative[F]): Self[Int]
}

object IterantBuilders extends IterantBuildersInstances {
  /** For building [[AsyncStream$ AsyncStream]] instances. */
  implicit object FromTask extends From[Task] {
    type Self[+A] = AsyncStream[A]
    type Builders = AsyncStream.type
    def instance: Builders = AsyncStream
  }

  /** For building [[LazyStream$ LazyStream]] instances. */
  implicit object FromCoeval extends From[Coeval] {
    type Self[+A] = LazyStream[A]
    type Builders = LazyStream.type
    def instance: Builders = LazyStream
  }
}

private[iterant] trait IterantBuildersInstances {
  /** Type-class for quickly finding a suitable type and [[IterantBuilders]]
    * implementation for a given `F[_]` monadic context.
    *
    * Implementations provided by Monix:
    *
    *  - [[AsyncStream$ AsyncStream]]
    *  - [[LazyStream$ LazyStream]]
    */
  trait From[F[_]] {
    type Self[+A] <: Iterant[F,A]
    type Builders <: IterantBuilders[F, Self]
    def instance: Builders
  }

  /** For building generic [[Iterant]] instances. */
  implicit def fromAny[F[_]]: FromAny[F] =
    genericFromAny.asInstanceOf[FromAny[F]]

  /** For building generic [[Iterant]] instances. */
  final class FromAny[F[_]] extends From[F] {
    type Self[+A] = Iterant[F,A]
    type Builders = Generic[F]

    def instance: Generic[F] =
      genericBuildersInstance.asInstanceOf[Generic[F]]
  }

  /** For building generic [[Iterant]] instances.
    *
    * @see [[fromAny]] and [[IterantBuilders]].
    */
  final class Generic[F[_]] extends IterantBuilders[F, ({type λ[+α] = Iterant[F, α]})#λ] {
    //-- BOILERPLATE that does nothing more than to forward to the standard Iterant builders!
    override def apply[A : ClassTag](elems: A*)(implicit F: Applicative[F]): Iterant[F,A] =
      Iterant.fromArray(elems.toArray)
    override def now[A](a: A)(implicit F: Applicative[F]): Iterant[F,A] =
      Iterant.now(a)
    override def pure[A](a: A)(implicit F: Applicative[F]): Iterant[F, A] =
      Iterant.pure(a)
    override def defer[A](fa: => Iterant[F, A])(implicit F: Applicative[F]): Iterant[F, A] =
      Iterant.defer(fa)
    override def eval[A](a: => A)(implicit F: Applicative[F]): Iterant[F,A] =
      Iterant.eval(a)
    override def nextS[A](item: A, rest: F[Iterant[F,A]], stop: F[Unit]): Iterant[F,A] =
      Iterant.nextS(item, rest, stop)
    override def nextSeqS[A](items: Cursor[A], rest: F[Iterant[F,A]], stop: F[Unit]): Iterant[F,A] =
      Iterant.nextSeqS(items, rest, stop)
    override def suspendS[A](rest: F[Iterant[F,A]], stop: F[Unit]): Iterant[F,A] =
      Iterant.suspendS(rest, stop)
    override def lastS[A](item: A): Iterant[F,A] =
      Iterant.lastS(item)
    override def haltS[A](ex: Option[Throwable]): Iterant[F,A] =
      Iterant.haltS(ex)
    override def suspend[A](fa: => Iterant[F,A])(implicit F: Applicative[F]): Iterant[F,A] =
      Iterant.suspend(fa)
    override def suspend[A](rest: F[Iterant[F,A]])(implicit F: Applicative[F]): Iterant[F,A] =
      Iterant.suspend(rest)
    override def empty[A]: Iterant[F,A] =
      Iterant.empty
    override def raiseError[A](ex: Throwable): Iterant[F,A] =
      Iterant.raiseError(ex)
    override def tailRecM[A, B](a: A)(f: (A) => Iterant[F,Either[A, B]])(implicit F: Monad[F]): Iterant[F,B] =
      Iterant.tailRecM(a)(f)
    override def fromArray[A](xs: Array[A])(implicit F: Applicative[F]): Iterant[F,A] =
      Iterant.fromArray(xs)
    override def fromList[A](xs: LinearSeq[A])(implicit F: Applicative[F]): Iterant[F,A] =
      Iterant.fromList(xs)
    override def fromIndexedSeq[A](xs: IndexedSeq[A])(implicit F: Applicative[F]): Iterant[F,A] =
      Iterant.fromIndexedSeq(xs)
    override def fromSeq[A](xs: Seq[A])(implicit F: Applicative[F]): Iterant[F,A] =
      Iterant.fromSeq(xs)
    override def fromIterable[A](xs: Iterable[A])(implicit F: Applicative[F]): Iterant[F,A] =
      Iterant.fromIterable(xs)
    override def fromIterator[A](xs: Iterator[A])(implicit F: Applicative[F]): Iterant[F,A] =
      Iterant.fromIterator(xs)
    override def range(from: Int, until: Int, step: Int)(implicit F: Applicative[F]): Iterant[F,Int] =
      Iterant.range(from, until, step)
  }

  private val genericFromAny: FromAny[Task] =
    new FromAny[Task]
  private val genericBuildersInstance: Generic[Task] =
    new Generic[Task]
}