/*
 * Copyright (c) 2014-2020 by The Monix Project Developers.
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

package monix.reactive

import monix.reactive.Observable.Transformation
import monix.reactive.internal.transformer.{BufferTumblingTransformer, ChainableT, FlatMapTransformer, MapTransformer, TransformerIdentity}

abstract class Transformer[A, B, I](previous: ChainableT[_, A, I]) extends Transformation[A, B] with ChainableT[A, B, I] {

  def chainPrevious(observable: Observable[I]): Observable[B] =
    this.apply(previous.chainPrevious(observable))

  def chain(observable: Observable[I]): Observable[B] =
    this.apply(previous.chainPrevious(observable))

  def map[C](f: B => C): Transformer[B, C, I] =
    new MapTransformer[B, C, I](f, this)

  def flatMap[C](f: B => Observable[C]): Transformer[B, C, I] =
    new FlatMapTransformer[B, C, I](f, this)

  def bufferTumbling(n: Int): Transformer[B, Seq[B], I] =
    new BufferTumblingTransformer[B, I](n, this)

}

object Transformer extends Transformer[Any, Any, Any] (new TransformerIdentity {}) with TransformerIdentity








