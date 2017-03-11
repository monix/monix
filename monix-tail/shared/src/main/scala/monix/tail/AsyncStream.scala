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

import monix.eval.Task
import monix.types.{Applicative, Monad}
import scala.collection.immutable.LinearSeq
import scala.reflect.ClassTag

/** The `AsyncStream[+A]` type is a [[AsyncStream type alias]] for
  * `Iterant[Task,A]`, thus using [[monix.eval.Task Task]] for
  * controlling the evaluation.
  *
  * A `Task`-powered [[monix.tail.Iterant Iterant]] is capable
  * of both lazy and asynchronous evaluation.
  *
  * @see The [[monix.tail.Iterant Iterant]] type and
  *      [[monix.tail.LazyStream$ LazyStream]] for synchronous/lazy
  *      evaluation.
  */
object AsyncStream extends IterantBuilders[Task, AsyncStream] {
  //-- BOILERPLATE that does nothing more than to forward to the standard Iterant builders!
  override def apply[A : ClassTag](elems: A*)(implicit F: Applicative[Task]): AsyncStream[A] =
    Iterant.fromArray(elems.toArray)
  override def now[A](a: A)(implicit F: Applicative[Task]): AsyncStream[A] =
    Iterant.now(a)
  override def pure[A](a: A)(implicit F: Applicative[Task]): AsyncStream[A] =
    Iterant.pure(a)
  override def eval[A](a: => A)(implicit F: Applicative[Task]): AsyncStream[A] =
    Iterant.eval(a)
  override def nextS[A](item: A, rest: Task[AsyncStream[A]], stop: Task[Unit]): AsyncStream[A] =
    Iterant.nextS(item, rest, stop)
  override def nextSeqS[A](items: Iterator[A], rest: Task[AsyncStream[A]], stop: Task[Unit]): AsyncStream[A] =
    Iterant.nextSeqS(items, rest, stop)
  override def nextGenS[A](items: Iterable[A], rest: Task[AsyncStream[A]], stop: Task[Unit]): AsyncStream[A] =
    Iterant.nextGenS(items, rest, stop)
  override def suspendS[A](rest: Task[AsyncStream[A]], stop: Task[Unit]): AsyncStream[A] =
    Iterant.suspendS(rest, stop)
  override def lastS[A](item: A): AsyncStream[A] =
    Iterant.lastS(item)
  override def haltS[A](ex: Option[Throwable]): AsyncStream[A] =
    Iterant.haltS(ex)
  override def suspend[A](fa: => AsyncStream[A])(implicit F: Applicative[Task]): AsyncStream[A] =
    Iterant.suspend(fa)
  override def defer[A](fa: => AsyncStream[A])(implicit F: Applicative[Task]): AsyncStream[A] =
    Iterant.defer(fa)
  override def suspend[A](rest: Task[AsyncStream[A]])(implicit F: Applicative[Task]): AsyncStream[A] =
    Iterant.suspend(rest)
  override def empty[A]: AsyncStream[A] =
    Iterant.empty
  override def raiseError[A](ex: Throwable): AsyncStream[A] =
    Iterant.raiseError(ex)
  override def tailRecM[A, B](a: A)(f: (A) => AsyncStream[Either[A, B]])(implicit F: Monad[Task]): AsyncStream[B] =
    Iterant.tailRecM(a)(f)
  override def fromArray[A](xs: Array[A])(implicit F: Applicative[Task]): AsyncStream[A] =
    Iterant.fromArray(xs)
  override def fromList[A](xs: LinearSeq[A])(implicit F: Applicative[Task]): AsyncStream[A] =
    Iterant.fromList(xs)
  override def fromIndexedSeq[A](xs: IndexedSeq[A])(implicit F: Applicative[Task]): AsyncStream[A] =
    Iterant.fromIndexedSeq(xs)
  override def fromSeq[A](xs: Seq[A])(implicit F: Applicative[Task]): AsyncStream[A] =
    Iterant.fromSeq(xs)
  override def fromIterable[A](xs: Iterable[A])(implicit F: Applicative[Task]): AsyncStream[A] =
    Iterant.fromIterable(xs)
  override def fromIterator[A](xs: Iterator[A])(implicit F: Applicative[Task]): AsyncStream[A] =
    Iterant.fromIterator(xs)
  override def range(from: Int, until: Int, step: Int)(implicit F: Applicative[Task]): AsyncStream[Int] =
    Iterant.range(from, until, step)
}

