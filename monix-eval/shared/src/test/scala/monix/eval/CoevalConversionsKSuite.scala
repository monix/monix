/*
 * Copyright (c) 2014-2019 by The Monix Project Developers.
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

import cats.effect.{Resource, SyncIO}
import minitest.SimpleTestSuite

object CoevalConversionsKSuite extends SimpleTestSuite {
  test("Coeval.fromK[F]") {
    val res = Coeval.fromK[SyncIO].apply(SyncIO(1 + 1))
    assertEquals(res.value(), 2)
  }

  test("Coeval.fromK[F] as param to mapK") {
    val res = Resource.liftF(SyncIO(1 + 1)).mapK(Coeval.fromK[SyncIO])
    assertEquals(res.use(Coeval.pure).value(), 2)
  }

  test("Coeval.toK[SyncIO]") {
    val eval = Coeval.toK[SyncIO].apply(Coeval(1 + 1))
    assertEquals(eval.unsafeRunSync(), 2)
  }

  test("Coeval.toK[SyncIO] as a param to mapK") {
    val res = Resource.liftF(Coeval(1 + 1)).mapK(Coeval.toK[SyncIO])
    assertEquals(res.use(SyncIO.pure).unsafeRunSync(), 2)
  }

  test("Coeval.toSyncK[SyncIO]") {
    val eval = Coeval.toSyncK[SyncIO].apply(Coeval(1 + 1))
    assertEquals(eval.unsafeRunSync(), 2)
  }

  test("Coeval.toSyncK[SyncIO] as a param to mapK") {
    val res = Resource.liftF(Coeval(1 + 1)).mapK(Coeval.toSyncK[SyncIO])
    assertEquals(res.use(SyncIO.pure).unsafeRunSync(), 2)
  }
}
