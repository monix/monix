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

package monix.laws

import cats.laws.{IsEq, MonadFilterLaws}
import monix.types.Streamable
import scala.language.higherKinds

trait StreamableLaws[F[_]] extends MonadConsLaws[F] with MonadFilterLaws[F]
  with RecoverableLaws[F, Throwable] with EvaluableLaws[F] {

  implicit def F: Streamable[F]

  def streamableEndWithConsistentWithFollowWith[A](fa: F[A], seq: List[A]): IsEq[F[A]] =
    F.endWith(fa, seq) <-> F.followWith(fa, F.fromList(seq))

  def streamableStartWithConsistentWithFollowWith[A](fa: F[A], seq: List[A]): IsEq[F[A]] =
    F.startWith(fa, seq) <-> F.followWith(F.fromList(seq), fa)

  def streamableRepeatIsConsistentWithFromList[A](list: List[A], times: Int): IsEq[F[A]] = {
    val factor = math.abs(times % 4) + 1
    val expectedList = (0 until factor).foldLeft(List.empty[A])((acc,_) => acc ++ list)
    val expectedF = F.fromList(expectedList)
    F.take(F.repeat(F.fromList(list)), expectedList.length) <-> expectedF
  }
}

object StreamableLaws {
  def apply[F[_] : Streamable]: StreamableLaws[F] =
    new StreamableLaws[F] { def F: Streamable[F] = implicitly[Streamable[F]] }
}

