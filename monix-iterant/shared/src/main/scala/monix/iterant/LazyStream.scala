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

import monix.eval.Coeval
import monix.types.{Applicative, Monad}
import scala.collection.immutable.LinearSeq
import scala.reflect.ClassTag

/** The `LazyStream[+A]` type is a type alias
  * for an `Iterant[Coeval,A]`, thus using [[monix.eval.Coeval Coeval]]
  * for controlling the evaluation.
  *
  * A `Coeval`-powered [[monix.iterant.Iterant Iterant]] is capable
  * of synchronous/lazy evaluation, being similar in spirit
  * with Scala's own `Stream`, or with the `Iterable` type.
  *
  * @see The [[monix.iterant.Iterant Iterant]] type
  *      and [[monix.iterant.AsyncStream$ AsyncStream]] for
  *      synchronous/lazy evaluation.
  */
object LazyStream extends IterantBuilders[Coeval,LazyStream] {
  //-- BOILERPLATE that does nothing more than to forward to the standard Iterant builders!
  override def apply[A : ClassTag](elems: A*)(implicit F: Applicative[Coeval]): LazyStream[A] =
    Iterant.fromArray(elems.toArray)
  override def now[A](a: A)(implicit F: Applicative[Coeval]): LazyStream[A] =
    Iterant.now(a)
  override def pure[A](a: A)(implicit F: Applicative[Coeval]): LazyStream[A] =
    Iterant.pure(a)
  override def eval[A](a: => A)(implicit F: Applicative[Coeval]): LazyStream[A] =
    Iterant.eval(a)
  override def nextS[A](item: A, rest: Coeval[LazyStream[A]], stop: Coeval[Unit]): LazyStream[A] =
    Iterant.nextS(item, rest, stop)
  override def nextSeqS[A](items: Cursor[A], rest: Coeval[LazyStream[A]], stop: Coeval[Unit]): LazyStream[A] =
    Iterant.nextSeqS(items, rest, stop)
  override def suspendS[A](rest: Coeval[LazyStream[A]], stop: Coeval[Unit]): LazyStream[A] =
    Iterant.suspendS(rest, stop)
  override def lastS[A](item: A): LazyStream[A] =
    Iterant.lastS(item)
  override def haltS[A](ex: Option[Throwable]): LazyStream[A] =
    Iterant.haltS(ex)
  override def defer[A](fa: => LazyStream[A])(implicit F: Applicative[Coeval]): LazyStream[A] =
    Iterant.defer(fa)
  override def suspend[A](fa: => LazyStream[A])(implicit F: Applicative[Coeval]): LazyStream[A] =
    Iterant.suspend(fa)
  override def suspend[A](rest: Coeval[LazyStream[A]])(implicit F: Applicative[Coeval]): LazyStream[A] =
    Iterant.suspend(rest)
  override def empty[A]: LazyStream[A] =
    Iterant.empty
  override def raiseError[A](ex: Throwable): LazyStream[A] =
    Iterant.raiseError(ex)
  override def tailRecM[A, B](a: A)(f: (A) => LazyStream[Either[A, B]])(implicit F: Monad[Coeval]): LazyStream[B] =
    Iterant.tailRecM(a)(f)
  override def fromArray[A](xs: Array[A])(implicit F: Applicative[Coeval]): LazyStream[A] =
    Iterant.fromArray(xs)
  override def fromList[A](xs: LinearSeq[A])(implicit F: Applicative[Coeval]): LazyStream[A] =
    Iterant.fromList(xs)
  override def fromIndexedSeq[A](xs: IndexedSeq[A])(implicit F: Applicative[Coeval]): LazyStream[A] =
    Iterant.fromIndexedSeq(xs)
  override def fromSeq[A](xs: Seq[A])(implicit F: Applicative[Coeval]): LazyStream[A] =
    Iterant.fromSeq(xs)
  override def fromIterable[A](xs: Iterable[A])(implicit F: Applicative[Coeval]): LazyStream[A] =
    Iterant.fromIterable(xs)
  override def fromIterator[A](xs: Iterator[A])(implicit F: Applicative[Coeval]): LazyStream[A] =
    Iterant.fromIterator(xs)
  override def range(from: Int, until: Int, step: Int)(implicit F: Applicative[Coeval]): LazyStream[Int] =
    Iterant.range(from, until, step)
}
