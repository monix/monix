/*
 * Copyright (c) 2014-2017 by its authors. Some rights reserved.
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
  test("Task.Now") {
    val task = Task.now(1)
    assert(task.toString.startsWith("Task.Now"), "task.toString.startsWith(\"Task.Now\")")
  }

  test("Task.Error") {
    val task = Task.raiseError(DummyException("dummy"))
    assert(task.toString.startsWith("Task.Error"), "task.toString.startsWith(\"Task.Error\")")
  }

  test("Task.Eval") {
    val task = Task.eval("hello")
    assert(task.toString.startsWith("Task.Eval"), "task.toString.startsWith(\"Task.Eval\")")
  }

  test("Task.Async") {
    val task = Task.create[Int]((_,cb) => { cb.onSuccess(1); Cancelable.empty })
    assert(task.toString.startsWith("Task.Async"), "task.toString.startsWith(\"Task.Async\")")
  }

  test("Task.FlatMap") {
    val task = Task.now(1).flatMap(Task.now)
    assert(task.toString.startsWith("Task.FlatMap"), "task.toString.startsWith(\"Task.FlatMap\")")
  }

  test("Task.Suspend") {
    val task = Task.defer(Task.now(1))
    assert(task.toString.startsWith("Task.Suspend"), "task.toString.startsWith(\"Task.Suspend\")")
  }

  test("Task.MemoizeSuspend") {
    val task = Task(1).memoize
    assert(task.toString.startsWith("Task.MemoizeSuspend"), "task.toString.startsWith(\"Task.MemoizeSuspend\")")
  }
}
