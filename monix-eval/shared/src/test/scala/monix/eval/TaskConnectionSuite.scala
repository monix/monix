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

import monix.eval.internal.TaskConnection
import monix.execution.cancelables.Cancelable
import monix.execution.cancelables.BooleanCancelable
import monix.execution.exceptions.{ CompositeException, DummyException }
import monix.execution.internal.Platform

object TaskConnectionSuite extends BaseTestSuite {
  test("initial push") { implicit s =>
    var effect = 0
    val initial = Task { effect += 1 }

    val c = TaskConnection()
    c.push(initial)

    assert(!c.isCanceled, "!c.isCanceled")
    c.cancel.runAsyncAndForget
    assert(c.isCanceled, "c.isCanceled")

    s.tick()
    assertEquals(effect, 1)
  }

  test("cancels Task after being canceled") { implicit s =>
    var effect = 0
    val initial = Task { effect += 1 }

    val c = TaskConnection()
    c.cancel.runAsyncAndForget; s.tick()
    assert(c.isCanceled, "c.isCanceled")

    c.push(initial)
    s.tick()
    assertEquals(effect, 1)
  }

  test("cancels Cancelable after being canceled") { implicit s =>
    var effect = 0
    val initial = Cancelable { () =>
      effect += 1
    }

    val c = TaskConnection()
    c.cancel.runAsyncAndForget; s.tick()
    assert(c.isCanceled, "c.isCanceled")

    c.push(initial)
    s.tick()
    assertEquals(effect, 1)
  }

  test("cancels CancelableF after being canceled") { implicit s =>
    val initial = TaskConnection()

    val c = TaskConnection()
    c.cancel.runAsyncAndForget; s.tick()
    assert(c.isCanceled, "c.isCanceled")

    c.push(initial)
    s.tick()
    assert(initial.isCanceled, "initial.isCanceled")
  }

  test("push two, pop one") { implicit s =>
    var effect = 0
    val initial1 = Task { effect += 1 }
    val initial2 = Task { effect += 2 }

    val c = TaskConnection()
    c.push(initial1)
    c.push(initial2)
    c.pop()

    c.cancel.runAsyncAndForget
    s.tick()

    assert(c.isCanceled, "c.isCanceled")
    assertEquals(effect, 1)
  }

  test("cancel the second time is a no-op") { implicit s =>
    var effect = 0
    val c = TaskConnection()
    c.push(Task { effect += 1 })

    c.cancel.runAsyncAndForget
    assertEquals(effect, 1)
    c.cancel.runAsyncAndForget
    assertEquals(effect, 1)
  }

  test("push two, pop two") { implicit s =>
    var effect = 0
    val initial1 = Task { effect += 1 }
    val initial2 = Task { effect += 2 }

    val c = TaskConnection()
    c.push(initial1)
    c.push(initial2)
    assertEquals(c.pop(), initial2)
    assertEquals(c.pop(), initial1)

    c.cancel.runAsyncAndForget
    s.tick()
    assertEquals(effect, 0)
  }

  test("push(Cancelable)") { implicit s =>
    val c = TaskConnection()
    val bc = BooleanCancelable()
    c.push(bc)

    assert(!c.isCanceled, "!c.isCanceled")
    c.cancel.runAsyncAndForget
    s.tick()

    assert(c.isCanceled, "c.isCanceled")
    assert(bc.isCanceled, "bc.isCanceled")
  }

  test("push(Cancelable) then pop") { implicit s =>
    val c = TaskConnection()
    val bc = BooleanCancelable()

    c.push(bc)
    val ref = c.pop()

    assert(!c.isCanceled, "!c.isCanceled")
    assert(!bc.isCanceled, "!bc.isCanceled")

    c.cancel.runAsyncAndForget; s.tick()
    assert(c.isCanceled, "c.isCanceled")
    assert(!bc.isCanceled, "!bc.isCanceled")

    ref.runAsyncAndForget; s.tick()
    assert(bc.isCanceled, "bc.isCanceled")
  }

  test("push(CancelToken)") { implicit s =>
    val c = TaskConnection()
    val bc = TaskConnection()
    c.push(bc)

    assert(!c.isCanceled, "!c.isCanceled")
    c.cancel.runAsyncAndForget
    s.tick()

    assert(c.isCanceled, "c.isCanceled")
    assert(bc.isCanceled, "bc.isCanceled")
  }

  test("push(CancelToken) then pop") { implicit s =>
    val c = TaskConnection()
    val bc = TaskConnection()

    c.push(bc)
    val ref = c.pop()

    assert(!c.isCanceled, "!c.isCanceled")
    assert(!bc.isCanceled, "!bc.isCanceled")
    assertEquals(bc.cancel, ref)

    c.cancel.runAsyncAndForget; s.tick()
    assert(c.isCanceled, "c.isCanceled")
    assert(!bc.isCanceled, "!bc.isCanceled")

    ref.runAsyncAndForget; s.tick()
    assert(bc.isCanceled, "bc.isCanceled")
  }

  test("pop when self is empty") { _ =>
    val sc = TaskConnection()
    assertEquals(sc.pop(), Task.unit)
  }

  test("pop when self is canceled") { implicit s =>
    val sc = TaskConnection()
    sc.cancel.runAsyncAndForget
    s.tick()
    assertEquals(sc.pop(), Task.unit)
  }

  test("cancel mixture") { implicit s =>
    val count = 100
    var effect = 0
    val cancelables = (0 until count).map(_ => BooleanCancelable())
    val connections1 = (0 until count).map(_ => TaskConnection())
    val connections2 = (0 until count).map(_ => TaskConnection())
    val tasks = (0 until count).map(_ => Task { effect += 1 })

    val sc = TaskConnection()
    sc.pushConnections(connections1: _*)
    for (bc <- cancelables) sc.push(bc)
    for (tk <- tasks) sc.push(tk)
    for (cn <- connections2) sc.push(cn)

    s.tick()
    for (r <- cancelables) assert(!r.isCanceled, "r.isCanceled")
    for (r <- connections1) assert(!r.isCanceled, "r.isCanceled")
    for (r <- connections2) assert(!r.isCanceled, "r.isCanceled")
    assertEquals(effect, 0)

    sc.cancel.runAsyncAndForget; s.tick()
    for (c   <- cancelables) assert(c.isCanceled, "r.isCanceled")
    for (cn1 <- connections1) assert(cn1.isCanceled, "cn1.isCanceled")
    for (cn2 <- connections2) assert(cn2.isCanceled, "cn2.isCanceled")
    assertEquals(effect, 100)
  }

  test("cancel mixture after being cancelled") { implicit s =>
    val count = 100
    var effect = 0
    val cancelables = (0 until count).map(_ => BooleanCancelable())
    val connections1 = (0 until count).map(_ => TaskConnection())
    val connections2 = (0 until count).map(_ => TaskConnection())
    val tasks = (0 until count).map(_ => Task { effect += 1 })

    val sc = TaskConnection()
    sc.cancel.runAsyncAndForget; s.tick()

    for (r <- cancelables) assert(!r.isCanceled, "r.isCanceled")
    for (r <- connections1) assert(!r.isCanceled, "r.isCanceled")
    for (r <- connections2) assert(!r.isCanceled, "r.isCanceled")
    assertEquals(effect, 0)

    sc.pushConnections(connections1: _*)
    for (bc <- cancelables) sc.push(bc)
    for (tk <- tasks) sc.push(tk)
    for (cn <- connections2) sc.push(cn)
    s.tick()

    for (c   <- cancelables) assert(c.isCanceled, "r.isCanceled")
    for (cn1 <- connections1) assert(cn1.isCanceled, "cn1.isCanceled")
    for (cn2 <- connections2) assert(cn2.isCanceled, "cn2.isCanceled")
    assertEquals(effect, 100)
  }

  test("tryReactivate") { implicit s =>
    val ref = TaskConnection()
    val c1 = BooleanCancelable()
    ref.push(c1)

    assert(!ref.tryReactivate(), "!ref.tryReactivate()")
    assert(!c1.isCanceled, "!c1.isCanceled")
    ref.cancel.runAsyncAndForget; s.tick()

    assert(c1.isCanceled, "c1.isCanceled")
    assert(ref.isCanceled, "ref.isCanceled")
    assert(ref.tryReactivate(), "ref.tryReactivate()")

    val c2 = BooleanCancelable()
    ref.push(c2)
    assert(!ref.isCanceled, "!ref.isCanceled")
    ref.cancel.runAsyncAndForget; s.tick()
    assert(c2.isCanceled, "c2.isCanceled")
    assert(ref.isCanceled, "ref.isCanceled")

    assert(TaskConnection.uncancelable.tryReactivate())
  }

  test("toCancelable") { implicit s =>
    val ref = TaskConnection()
    val cancelRef = ref.toCancelable

    val c1 = BooleanCancelable()
    ref.push(c1)

    assert(!c1.isCanceled, "!c1.isCanceled")
    cancelRef.cancel()
    assert(ref.isCanceled, "ref.isCanceled")
    assert(c1.isCanceled, "c1.isCanceled")
  }

  test("uncancelable ref is shared") { _ =>
    val t = TaskConnection.uncancelable
    assertEquals(t, TaskConnection.uncancelable)
  }

  test("uncancelable ops") { implicit s =>
    val t = TaskConnection.uncancelable
    assert(!t.isCanceled, "!t.isCanceled")

    var effect = 0
    val tk = Task { effect += 1 }
    val bc = BooleanCancelable()
    val c2 = TaskConnection()
    val c3 = TaskConnection()
    val c4 = TaskConnection()

    t.push(tk)
    t.push(bc)
    t.push(c2)
    t.pushConnections(c3, c4)

    assertEquals(t.pop(), Task.unit)
    t.push(bc)

    t.cancel.runAsyncAndForget; s.tick()
    t.cancel.runAsyncAndForget; s.tick()

    assert(!t.isCanceled, "!t.isCanceled")
    assert(!bc.isCanceled, "!bc.isCanceled")
    assert(!c2.isCanceled, "!c2.isCanceled")
    assert(!c3.isCanceled, "!c3.isCanceled")
    assert(!c4.isCanceled, "!c4.isCanceled")
    assertEquals(effect, 0)
    assertEquals(t.pop(), Task.unit)
    assert(t.tryReactivate(), "t.tryReactivate()")

    assertEquals(t.toCancelable, Cancelable.empty)
  }

  test("throwing error in Task on cancel all") { implicit s =>
    val dummy = DummyException("dummy")
    val task = Task.raiseError(dummy)

    val c = TaskConnection()
    c.push(task)
    c.cancel.runAsyncAndForget; s.tick()

    assertEquals(s.state.lastReportedError, dummy)
  }

  test("throwing multiple errors in Tasks on cancel all") { implicit s =>
    val dummy1 = DummyException("dummy1")
    val task1 = Task.raiseError(dummy1)
    val dummy2 = DummyException("dummy2")
    val task2 = Task.raiseError(dummy2)

    val c = TaskConnection()
    c.push(task1)
    c.push(task2)
    c.cancel.runAsyncAndForget; s.tick()

    if (Platform.isJVM) {
      assertEquals(s.state.lastReportedError, dummy2)
      assertEquals(dummy2.getSuppressed.toList, List(dummy1))
    } else {
      s.state.lastReportedError match {
        case CompositeException(errors) =>
          assertEquals(errors, List(dummy2, dummy1))
        case _ =>
          fail(s"Unexpected: ${s.state.lastReportedError}")
      }
    }
  }

  test("throwing error in Task after cancel") { implicit s =>
    val c = TaskConnection()
    c.cancel.runAsyncAndForget; s.tick()

    val dummy = DummyException("dummy")
    val task = Task.raiseError(dummy)
    c.push(task); s.tick()

    assertEquals(s.state.lastReportedError, dummy)
  }

  test("throwing error in Cancelable on cancel all") { implicit s =>
    val dummy = DummyException("dummy")
    val task = Cancelable(() => throw dummy)

    val c = TaskConnection()
    c.push(task)
    c.cancel.runAsyncAndForget; s.tick()

    assertEquals(s.state.lastReportedError, dummy)
  }

  test("throwing multiple errors in Cancelables on cancel all") { implicit s =>
    val dummy1 = DummyException("dummy1")
    val task1 = Cancelable(() => throw dummy1)
    val dummy2 = DummyException("dummy2")
    val task2 = Cancelable(() => throw dummy2)

    val c = TaskConnection()
    c.push(task1)
    c.push(task2)
    c.cancel.runAsyncAndForget; s.tick()

    if (Platform.isJVM) {
      assertEquals(s.state.lastReportedError, dummy2)
      assertEquals(dummy2.getSuppressed.toList, List(dummy1))
    } else {
      s.state.lastReportedError match {
        case CompositeException(errors) =>
          assertEquals(errors, List(dummy2, dummy1))
        case _ =>
          fail(s"Unexpected: ${s.state.lastReportedError}")
      }
    }
  }

  test("throwing error in Cancelable after cancel") { implicit s =>
    val c = TaskConnection()
    c.cancel.runAsyncAndForget; s.tick()

    val dummy = DummyException("dummy")
    val task = Cancelable(() => throw dummy)
    c.push(task); s.tick()

    assertEquals(s.state.lastReportedError, dummy)
  }
}
