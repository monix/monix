/*
 * Copyright (c) 2014-2021 by The Monix Project Developers.
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

import monix.eval.Coeval
import monix.eval.Coeval.Trace
import monix.eval.tracing.CoevalEvent

/**
  * All Credits to https://github.com/typelevel/cats-effect and https://github.com/RaasAhsan
  */
private[eval] object CoevalTracing {

  def decorated[A](source: Coeval[A]): Coeval[A] =
    Trace(source, buildFrame())

  def uncached(): CoevalEvent =
    buildFrame()

  def cached(clazz: Class[_]): CoevalEvent =
    buildCachedFrame(clazz)

  private def buildCachedFrame(clazz: Class[_]): CoevalEvent = {
    val currentFrame = frameCache.get(clazz)
    if (currentFrame eq null) {
      val newFrame = buildFrame()
      frameCache.put(clazz, newFrame)
      newFrame
    } else {
      currentFrame
    }
  }

  private def buildFrame(): CoevalEvent =
    CoevalEvent.StackTrace(new Throwable().getStackTrace.toList)

  /**
    * Global cache for trace frames. Keys are references to lambda classes.
    * Should converge to the working set of traces very quickly for hot code paths.
    */
  private[this] val frameCache: ConcurrentHashMap[Class[_], CoevalEvent] = new ConcurrentHashMap()

}
