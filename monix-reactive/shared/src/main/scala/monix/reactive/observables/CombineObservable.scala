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

package monix.reactive.observables

import cats.Applicative
import monix.reactive.Observable

/** A `CombineObservable` is an observable that wraps a regular
  * [[Observable]] and provide [[cats.Applicative]] instance
  * which uses [[Observable.combineLatest]] to combine elements.
  */
final class CombineObservable[A](val value: Observable[A]) extends AnyVal

object CombineObservable {
  implicit def combineObservableApplicative: Applicative[CombineObservable] = new Applicative[CombineObservable] {
    def pure[A](x: A): CombineObservable[A] = new CombineObservable(Observable.now(x))

    def ap[A, B](ff: CombineObservable[(A) => B])(fa: CombineObservable[A]) = new CombineObservable(
      ff.value.combineLatestMap(fa.value)((f, a) => f(a))
    )

    override def map[A, B](fa: CombineObservable[A])(f: A => B): CombineObservable[B] =
      new CombineObservable(fa.value.map(f))

    override def product[A, B](fa: CombineObservable[A], fb: CombineObservable[B]): CombineObservable[(A, B)] =
      new CombineObservable(fa.value.combineLatest(fb.value))
  }
}