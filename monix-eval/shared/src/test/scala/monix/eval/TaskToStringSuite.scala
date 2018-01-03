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

package monix.eval

import minitest.SimpleTestSuite
import monix.execution.Cancelable
import monix.execution.exceptions.DummyException

object TaskToStringSuite extends SimpleTestSuite {
  def assertContains[A](ref: Task[A], startStr: String): Unit = {
    val str = ref.toString
    assert(str.startsWith(startStr), s""""$str".startsWith("$startStr")""")
  }

  test("Task.Now") {
    val ref = Task.now(1)
    assertContains(ref, "Task.Now")
  }

  test("Task.Error") {
    val ref = Task.raiseError(DummyException("dummy"))
    assertContains(ref, "Task.Error")
  }

  test("Task.Eval") {
    val ref = Task.eval("hello")
    assertContains(ref, "Task.Eval")
  }

  test("Task.Async") {
    val ref = Task.create[Int]((_,cb) => { cb.onSuccess(1); Cancelable.empty })
    assertContains(ref, "Task.Async")
  }

  test("Task.FlatMap") {
    val ref = Task.now(1).flatMap(Task.now)
    assertContains(ref, "Task.FlatMap")
  }

  test("Task.Suspend") {
    val ref = Task.defer(Task.now(1))
    assertContains(ref, "Task.Suspend")
  }

  test("Task.MemoizeSuspend") {
    val ref = Task(1).memoize
    assertContains(ref, "Task.MemoizeSuspend")
  }

  test("Task.Map") {
    val ref = Task.now(1).map(_ + 1)
    assertContains(ref, "Task.Map")
  }
}
