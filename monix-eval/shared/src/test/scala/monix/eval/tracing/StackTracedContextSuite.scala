/*
 * Copyright (c) 2014-2020 by The Monix Project Developers.
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

package monix.eval.tracing

import monix.eval.BaseTestSuite
import monix.eval.internal.StackTracedContext

/**
  * All Credits to https://github.com/typelevel/cats-effect and https://github.com/RaasAhsan
  */
object StackTracedContextSuite extends BaseTestSuite {
  val traceBufferSize: Int = 1 << monix.eval.internal.TracingPlatform.traceBufferLogSize
  val stackTrace = new Throwable().getStackTrace.toList

  test("push traces") { _ =>
    val ctx = new StackTracedContext()

    val t1 = TaskEvent.StackTrace(stackTrace)
    val t2 = TaskEvent.StackTrace(stackTrace)

    ctx.pushEvent(t1)
    ctx.pushEvent(t2)

    val trace = ctx.trace()
    assertEquals(trace.events, List(t1, t2))
    assertEquals(trace.captured, 2)
    assertEquals(trace.omitted, 0)
  }

  test("track omitted frames") { _ =>
    val ctx = new StackTracedContext()

    for (_ <- 0 until (traceBufferSize + 10)) {
      ctx.pushEvent(TaskEvent.StackTrace(stackTrace))
    }

    val trace = ctx.trace()
    assertEquals(trace.events.length, traceBufferSize)
    assertEquals(trace.captured, traceBufferSize + 10)
    assertEquals(trace.omitted, 10)
  }

}