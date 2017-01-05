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

package monix.interact

import monix.eval.Coeval

/** A `CoevalStream` represents a [[monix.eval.Coeval Coeval]]-based
  * [[Iterant]], that has potentially lazy behavior.
  *
  * A `CoevalStream` has the following characteristics:
  *
  *  1. it can be infinite
  *  2. it can be lazy
  *
  * It's very similar to other lazy types in Scala's standard
  * library, like `Iterator`, however the execution model is more
  * flexible, as it is controlled by [[monix.eval.Coeval Coeval]].
  * This means that:
  *
  *  1. you can have the equivalent of an `Iterable` if the
  *     `Coeval` tails are built with
  *     [[monix.eval.Coeval Coeval.eval]]
  *  2. you can have the equivalent of a Scala `Stream`, caching
  *     elements as the stream is getting traversed, if the
  *     `Coeval` tails are built with
  *     [[monix.eval.Coeval Coeval.evalOnce]]
  *  3. it can be completely strict and thus equivalent with
  *     `List`, if the tails are built with
  *     [[monix.eval.Coeval Coeval.now]]
  *
  * The implementation is practically wrapping the generic
  * [[Iterant]], materialized with the [[monix.eval.Coeval Coeval]]
  * type.
  */
final case class CoevalStream[+A](stream: Iterant[Coeval,A])
  extends IterantLike[A,Coeval,CoevalStream]() {

  protected def transform[B](f: (Iterant[Coeval, A]) => Iterant[Coeval, B]): CoevalStream[B] =
    CoevalStream(f(stream))
}

object CoevalStream extends IterantLikeBuilders[Coeval, CoevalStream] {
  /** Wraps a [[Iterant]] into a [[monix.eval.Coeval CoevalStream]]. */
  def fromStream[A](stream: Iterant[Coeval, A]): CoevalStream[A] =
    CoevalStream(stream)
}
