/*
 * Copyright (c) 2014-2016 by its authors. Some rights reserved.
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

package monix.types.instances

import monix.async.{Task, AsyncIterable}
import monix.async.AsyncIterable.{Wait, Next}
import monix.types.{MonadCons, Nonstrict}

trait AsyncIterableInstances {
  implicit val asyncIterableInstances: Nonstrict[AsyncIterable] with MonadCons[AsyncIterable] =
    new Nonstrict[AsyncIterable] with MonadCons[AsyncIterable] {
      override def memoize[A](fa: AsyncIterable[A]): AsyncIterable[A] =
        fa.memoize
      override def now[A](a: A): AsyncIterable[A] =
        AsyncIterable.now(a)
      override def concatMap[A, B](fa: AsyncIterable[A])(f: (A) => AsyncIterable[B]): AsyncIterable[B] =
        fa.flatMap(f)
      override def cons[A](head: A, tail: AsyncIterable[A]): AsyncIterable[A] =
        Next(head, Task.now(tail))

      override def foldWhileF[A, S](fa: AsyncIterable[A], seed: S)(f: (S, A) => (Boolean, S)): AsyncIterable[S] =
        Wait(fa.foldWhileA(seed)(f).map(AsyncIterable.now))
      override def empty[A]: AsyncIterable[A] =
        AsyncIterable.empty[A]
    }
}

object asyncIterable extends AsyncIterableInstances
