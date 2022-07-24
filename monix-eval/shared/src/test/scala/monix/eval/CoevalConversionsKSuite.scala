/*
 * Copyright (c) 2014-2022 Monix Contributors.
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

import cats.effect.{ Resource, SyncIO }

class CoevalConversionsKSuite extends BaseTestSuite {
  test("Coeval.liftFrom[F]") {
    val res = Coeval.liftFrom[SyncIO].apply(SyncIO(1 + 1))
    assertEquals(res.value(), 2)
  }

  test("Coeval.liftFrom[F] as param to mapK") {
    val res = Resource.eval(SyncIO(1 + 1)).mapK(Coeval.liftFrom[SyncIO])
    assertEquals(res.use(Coeval.pure).value(), 2)
  }

  test("Coeval.liftTo[SyncIO]") {
    val eval = Coeval.liftTo[SyncIO].apply(Coeval(1 + 1))
    assertEquals(eval.unsafeRunSync(), 2)
  }

  test("Coeval.liftTo[SyncIO] as a param to mapK") {
    val res = Resource.eval(Coeval(1 + 1)).mapK(Coeval.liftTo[SyncIO])
    assertEquals(res.use(SyncIO.pure).unsafeRunSync(), 2)
  }

  test("Coeval.liftToSync[SyncIO]") {
    val eval = Coeval.liftToSync[SyncIO].apply(Coeval(1 + 1))
    assertEquals(eval.unsafeRunSync(), 2)
  }

  test("Coeval.liftToSync[SyncIO] as a param to mapK") {
    val res = Resource.eval(Coeval(1 + 1)).mapK(Coeval.liftToSync[SyncIO])
    assertEquals(res.use(SyncIO.pure).unsafeRunSync(), 2)
  }
}
