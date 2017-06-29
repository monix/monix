/*
 * Copyright (c) 2014-2017 by The Monix Project Developers.
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
package instances

import cats.Eval
import scala.util.Try
import _root_.cats.CoflatMap
import _root_.cats.effect.Async

/** Specification for Cats type classes, to be implemented by
  * types that can execute asynchronous computations (e.g. [[Task]]).
  */
trait CatsAsyncInstances[F[_]] extends Async[F] with CoflatMap[F]

object CatsAsyncInstances {
  /** Cats type class instances for [[Task]]. */
  class ForTask extends CatsAsyncInstances[Task] {
    override def pure[A](a: A): Task[A] = Task.now(a)
    override def delay[A](thunk: => A): Task[A] = Task.eval(thunk)
    override def suspend[A](fa: => Task[A]): Task[A] = Task.defer(fa)
    // override val unit: Task[Unit] = Task.now(())

    override def flatMap[A, B](fa: Task[A])(f: (A) => Task[B]): Task[B] =
      fa.flatMap(f)
    override def flatten[A](ffa: Task[Task[A]]): Task[A] =
      ffa.flatten
    override def tailRecM[A, B](a: A)(f: (A) => Task[Either[A, B]]): Task[B] =
      Task.tailRecM(a)(f)
    override def coflatMap[A, B](fa: Task[A])(f: (Task[A]) => B): Task[B] =
      Task.eval(f(fa))
    override def coflatten[A](fa: Task[A]): Task[Task[A]] =
      Task.now(fa)
    override def ap[A, B](ff: Task[(A) => B])(fa: Task[A]): Task[B] =
      for (f <- ff; a <- fa) yield f(a)
    override def map2[A, B, Z](fa: Task[A], fb: Task[B])(f: (A, B) => Z): Task[Z] =
      for (a <- fa; b <- fb) yield f(a, b)
    override def product[A, B](fa: Task[A], fb: Task[B]): Task[(A, B)] =
      for (a <- fa; b <- fb) yield (a, b)
    override def map[A, B](fa: Task[A])(f: (A) => B): Task[B] =
      fa.map(f)
    override def raiseError[A](e: Throwable): Task[A] =
      Task.raiseError(e)
    override def handleError[A](fa: Task[A])(f: (Throwable) => A): Task[A] =
      fa.onErrorHandle(f)
    override def handleErrorWith[A](fa: Task[A])(f: (Throwable) => Task[A]): Task[A] =
      fa.onErrorHandleWith(f)
    override def recover[A](fa: Task[A])(pf: PartialFunction[Throwable, A]): Task[A] =
      fa.onErrorRecover(pf)
    override def recoverWith[A](fa: Task[A])(pf: PartialFunction[Throwable, Task[A]]): Task[A] =
      fa.onErrorRecoverWith(pf)
    override def attempt[A](fa: Task[A]): Task[Either[Throwable, A]] =
      fa.attempt
    override def catchNonFatal[A](a: => A)(implicit ev: <:<[Throwable, Throwable]): Task[A] =
      Task.eval(a)
    override def catchNonFatalEval[A](a: Eval[A])(implicit ev: <:<[Throwable, Throwable]): Task[A] =
      Task.eval(a.value)
    override def fromTry[A](t: Try[A])(implicit ev: <:<[Throwable, Throwable]): Task[A] =
      Task.fromTry(t)
    override def async[A](k: ((Either[Throwable, A]) => Unit) => Unit): Task[A] =
      Task.unsafeCreate { (_, cb) => k(r => cb(r)) }
  }

  /** Reusable reference of [[ForTask]]. */
  object ForTask extends ForTask

  /** Cats type class instances for [[Task Tasks]] that have
    * non-deterministic effects in their applicative.
    */
  class ForParallelTask extends ForTask {
    override def ap[A, B](ff: Task[(A) => B])(fa: Task[A]): Task[B] =
      Task.mapBoth(ff, fa)(_(_))
    override def map2[A, B, Z](fa: Task[A], fb: Task[B])(f: (A, B) => Z): Task[Z] =
      Task.mapBoth(fa, fb)(f)
    override def product[A, B](fa: Task[A], fb: Task[B]): Task[(A, B)] =
      Task.mapBoth(fa, fb)((_, _))
  }

  /** Reusable reference of [[ForParallelTask]]. */
  object ForParallelTask extends ForParallelTask
}
