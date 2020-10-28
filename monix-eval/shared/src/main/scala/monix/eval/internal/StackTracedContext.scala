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

package monix.eval.internal

import monix.eval.tracing.{TaskEvent, TaskTrace}
import monix.eval.internal.TracingPlatform.traceBufferLogSize
import monix.execution.internal.RingBuffer

/**
  * All Credits to https://github.com/typelevel/cats-effect and https://github.com/RaasAhsan
  */
private[eval] final class StackTracedContext {
  private[this] val events: RingBuffer[TaskEvent] = new RingBuffer(traceBufferLogSize)
  private[this] var captured: Int = 0
  private[this] var omitted: Int = 0

  def pushEvent(fr: TaskEvent): Unit = {
    captured += 1
    if (events.push(fr) != null) omitted += 1
  }

  def trace(): TaskTrace =
    TaskTrace(events.toList, captured, omitted)

  def getStackTraces(): List[TaskEvent.StackTrace] =
    events.toList.collect { case ev: TaskEvent.StackTrace => ev }
}
