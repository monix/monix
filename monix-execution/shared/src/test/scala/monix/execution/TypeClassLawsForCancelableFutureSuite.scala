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

package monix.execution

import cats.{MonadError, Eval}
import cats.laws.discipline.{CoflatMapTests, MonadErrorTests}
import monix.execution.exceptions.DummyException
import monix.execution.schedulers.TestScheduler
import scala.util.{Failure, Success}

object TypeClassLawsForCancelableFutureSuite extends BaseLawsSuite {
  checkAllAsync("CoflatMap[CancelableFuture]") { implicit ec =>
    CoflatMapTests[CancelableFuture].coflatMap[Int,Int,Int]
  }

  checkAllAsync("MonadError[CancelableFuture, Throwable]") { implicit ec =>
    MonadErrorTests[CancelableFuture, Throwable].monadError[Int,Int,Int]
  }

  test("adaptError") {
    implicit val ec = TestScheduler()
    val F = MonadError[CancelableFuture, Throwable]

    val fa1 = F.catchNonFatal(1); ec.tick()
    assertEquals(fa1.value, Some(Success(1)))

    val dummy = DummyException("dummy")
    val fa2 = F.catchNonFatal(throw dummy); ec.tick()
    assertEquals(fa2.value, Some(Failure(dummy)))
  }

  test("adaptErrorEval") {
    implicit val ec = TestScheduler()
    val F = MonadError[CancelableFuture, Throwable]

    val fa1 = F.catchNonFatalEval(Eval.always(1)); ec.tick()
    assertEquals(fa1.value, Some(Success(1)))

    val dummy = DummyException("dummy")
    val fa2 = F.catchNonFatalEval(Eval.always(throw dummy)); ec.tick()
    assertEquals(fa2.value, Some(Failure(dummy)))
  }
}
