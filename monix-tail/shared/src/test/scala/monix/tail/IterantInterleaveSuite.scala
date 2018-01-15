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

package monix.tail

import cats.effect.Sync
import monix.eval.Coeval
import monix.execution.internal.Platform
import org.scalacheck.Test
import org.scalacheck.Test.Parameters

object IterantInterleaveSuite extends BaseTestSuite {
  override lazy val checkConfig: Parameters = {
    if (Platform.isJVM)
      Test.Parameters.default.withMaxSize(256)
    else
      Test.Parameters.default.withMaxSize(32)
  }

  private def naiveImp[F[_], A, B >: A](lh: Iterant[F, A], rh: Iterant[F, B])
                                       (implicit F: Sync[F]): Iterant[F, B] =
    lh.zip(rh).flatMap { case (a, b) => Iterant[F].pure(a) ++ Iterant[F].pure(b) }

  test("naiveImp smoke test") { implicit s =>
    assertEquals(naiveImp(
      Iterant[Coeval].of(11, 12),
      Iterant[Coeval].of(21, 22, 23)).toListL.value,
      List(11, 21, 12, 22))
    assertEquals(naiveImp(
      Iterant[Coeval].of(11, 12),
      Iterant[Coeval].of(21)).toListL.value,
      List(11, 21))
  }

  // TODO equivalence test with naive imp

  // TODO various scenarios (stop on error, etc.)

}
