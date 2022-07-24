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

import monix.execution.exceptions.DummyException

import java.util.concurrent.CompletableFuture
import scala.util.{ Failure, Success }

class TaskLikeConversionsJava8Suite extends BaseTestSuite {

  fixture.test("convert from async CompletableFuture; on success") { implicit s =>
    val future = new CompletableFuture[Int]()
    val f = Task.from(future).runToFuture

    s.tick()
    assertEquals(f.value, None)

    future.complete(100)
    s.tick()
    assertEquals(f.value, Some(Success(100)))
  }

  fixture.test("convert from async CompletableFuture; on failure") { implicit s =>
    val future = new CompletableFuture[Int]()
    val f = Task.from(future).runToFuture

    s.tick()
    assertEquals(f.value, None)

    val dummy = DummyException("dummy")
    future.completeExceptionally(dummy)

    s.tick()
    assertEquals(f.value, Some(Failure(dummy)))
  }

  fixture.test("CompletableFuture is cancelable via task") { implicit s =>
    val future = new CompletableFuture[Int]()
    val f = Task.from(future).runToFuture

    s.tick()
    assertEquals(f.value, None)

    f.cancel()
    s.tick()
    assertEquals(f.value, None)

    // Should be already completed
    assert(!future.complete(1))
    s.tick()
    assertEquals(f.value, None)
  }
}
