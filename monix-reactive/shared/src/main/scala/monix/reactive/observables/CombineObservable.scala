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

import cats.Apply
import monix.execution.internal.Newtype1
import monix.reactive.Observable

/** Newtype encoding for an [[Observable]] datatype that has a [[cats.Apply]]
  * instance which uses [[Observable.combineLatest]] to combine elements
  * needed for implementing [[cats.NonEmptyParallel]]
  */
object CombineObservable extends Newtype1[Observable] {

  implicit val combineObservableApplicative: Apply[CombineObservable.Type] = new Apply[CombineObservable.Type] {
    import CombineObservable.{apply => wrap}

    override def ap[A, B](ff: CombineObservable.Type[(A) => B])(fa: CombineObservable.Type[A]) =
      wrap(unwrap(ff).combineLatestMap(unwrap(fa))((f, a) => f(a)))

    override def map[A, B](fa: CombineObservable.Type[A])(f: A => B): CombineObservable.Type[B] =
      wrap(unwrap(fa).map(f))

    override def map2[A, B, C](fa: CombineObservable.Type[A], fb: CombineObservable.Type[B])
      (f: (A, B) => C): CombineObservable.Type[C] =
      wrap(unwrap(fa).combineLatestMap(unwrap(fb))(f))

    override def product[A, B](fa: CombineObservable.Type[A], fb: CombineObservable.Type[B]): CombineObservable.Type[(A, B)] =
      wrap(unwrap(fa).combineLatest(unwrap(fb)))
  }
}