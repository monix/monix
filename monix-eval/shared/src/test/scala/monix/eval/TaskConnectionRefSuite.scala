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

import monix.catnap.CancelableF
import monix.catnap.cancelables.BooleanCancelableF
import monix.execution.cancelables.BooleanCancelable
import monix.eval.internal.TaskConnectionRef
import monix.execution.ExecutionModel.SynchronousExecution

object TaskConnectionRefSuite extends BaseTestSuite {
  test("assign and cancel a Cancelable") { implicit s =>
    var effect = 0
    val cr = TaskConnectionRef()
    val b = BooleanCancelable { () =>
      effect += 1
    }

    cr := b
    assert(!b.isCanceled, "!b.isCanceled")

    cr.cancel.runAsyncAndForget; s.tick()
    assert(b.isCanceled, "b.isCanceled")
    assert(effect == 1)

    cr.cancel.runAsyncAndForget; s.tick()
    assert(effect == 1)
  }

  test("assign and cancel a CancelableF") { implicit s =>
    var effect = 0
    val cr = TaskConnectionRef()
    val b = CancelableF.wrap(Task { effect += 1 })

    cr := b
    assertEquals(effect, 0)

    cr.cancel.runAsyncAndForget; s.tick()
    assert(effect == 1)
  }

  test("assign and cancel a CancelToken[Task]") { implicit s =>
    var effect = 0
    val cr = TaskConnectionRef()
    val b = Task { effect += 1 }

    cr := b
    assertEquals(effect, 0)

    cr.cancel.runAsyncAndForget; s.tick()
    assertEquals(effect, 1)

    cr.cancel.runAsyncAndForget; s.tick()
    assertEquals(effect, 1)
  }

  test("cancel a Cancelable on single assignment") { implicit s =>
    val cr = TaskConnectionRef()
    cr.cancel.runAsyncAndForget; s.tick()

    var effect = 0
    val b = BooleanCancelable { () =>
      effect += 1
    }
    cr := b

    assert(b.isCanceled)
    assertEquals(effect, 1)

    cr.cancel.runAsyncAndForget; s.tick()
    assertEquals(effect, 1)

    val b2 = BooleanCancelable { () =>
      effect += 1
    }
    intercept[IllegalStateException] { cr := b2; () }
    assertEquals(effect, 2)
  }

  test("cancel a CancelableF on single assignment") { scheduler =>
    implicit val s = scheduler.withExecutionModel(SynchronousExecution)

    val cr = TaskConnectionRef()
    cr.cancel.runAsyncAndForget; s.tick()

    var effect = 0
    val b = BooleanCancelableF(Task { effect += 1 }).runToFuture.value.get.get
    cr := b

    assert(b.isCanceled.runToFuture.value.get.get)
    assertEquals(effect, 1)

    cr.cancel.runAsyncAndForget; s.tick()
    assertEquals(effect, 1)

    val b2 = BooleanCancelableF(Task { effect += 1 }).runToFuture.value.get.get
    intercept[IllegalStateException] { cr := b2; () }
    assertEquals(effect, 2)
  }

  test("cancel a Task on single assignment") { implicit s =>
    val cr = TaskConnectionRef()
    cr.cancel.runAsyncAndForget; s.tick()

    var effect = 0
    val b = Task { effect += 1 }

    cr := b; s.tick()
    assertEquals(effect, 1)

    cr.cancel.runAsyncAndForget; s.tick()
    assertEquals(effect, 1)

    intercept[IllegalStateException] {
      cr := b
      ()
    }
    assertEquals(effect, 2)
  }
}
