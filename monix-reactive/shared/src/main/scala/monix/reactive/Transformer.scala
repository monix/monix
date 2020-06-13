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

import monix.reactive.Transformer.Transformer

abstract class TransformerBuilder[A, B, C ,D, E](first: Option[TransformerBuilder[D, E, Any, D, E]]) extends Transformer[A, B] {


 /* def a(observable: Observable[A]) = {
    new Transformer(observable)
  }*/

  var nextT: Option[TransformerBuilder[B, C, _, D, E]] = None

  val firstT: TransformerBuilder[_ >: A with D, _ >: B with E, _, D, E] = if(first.isEmpty) this else first.get

  def chain(observable: Observable[A]): Observable[Any] = {
    nextT match {
      //case Some(nextTransformation) => nextTransformation.apply(this.apply)
      case Some(nextTransformation) => nextTransformation.chain(this.apply(observable))
      case None => this.apply(observable)
    }
  }

  def map(f: B => C): TransformerMap[B, C, _, D, E] = {
    val mapTransformer = new TransformerMap[B, C, Any, D, E](f, first)
    nextT = Some(mapTransformer)
    mapTransformer
  }

/*  def build(observable: Observable[A]) = {
    firstT.chainTransformation()
//    transformationChain.fold(observable)((state: Observable[_], next: Transformer[_, _]) => this.chainTransformation(next))
  }*/

}

class TransformerMap[A, B, C, D, E](f: A => B, first: Option[TransformerBuilder[D, E, Any, D, E]]) extends TransformerBuilder[A, B, C, D, E](first) {

  override def apply(v1: Observable[A]): Observable[B] = {
    v1.map(f)
  }

}



object Transformer {
  type Transformer[A, B] = Observable[A] => Observable[B]

 // def apply[A](observable: Observable[A]): Transformer[A, A] = new Transformer(observable)
  def map[A, B](f: A => B) = new TransformerMap[A, B, A, B, Any](f, None)

}

