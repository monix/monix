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

import java.util.concurrent.ConcurrentHashMap

import monix.eval.Task
import monix.eval.Task.Trace
import monix.eval.tracing.TaskEvent

/**
  * All Credits to https://github.com/typelevel/cats-effect and https://github.com/RaasAhsan
  */
private[eval] object TaskTracing {

  def decorated[A](source: Task[A]): Task[A] =
    Trace(source, buildFrame())

  def uncached(): TaskEvent =
    buildFrame()

  def cached(clazz: Class[_]): TaskEvent =
    buildCachedFrame(clazz)

  private def buildCachedFrame(clazz: Class[_]): TaskEvent = {
    val currentFrame = frameCache.get(clazz)
    if (currentFrame eq null) {
      val newFrame = buildFrame()
      frameCache.put(clazz, newFrame)
      newFrame
    } else {
      currentFrame
    }
  }

  private def buildFrame(): TaskEvent =
    TaskEvent.StackTrace(new Throwable().getStackTrace.toList)

  /**
    * Global cache for trace frames. Keys are references to lambda classes.
    * Should converge to the working set of traces very quickly for hot code paths.
    */
  private[this] val frameCache: ConcurrentHashMap[Class[_], TaskEvent] = new ConcurrentHashMap()

}
