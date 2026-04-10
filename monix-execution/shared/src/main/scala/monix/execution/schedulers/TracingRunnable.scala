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

package monix.execution.schedulers

import monix.execution.misc.Local

/** Wraps a `Runnable` into one that restores the given
  * [[monix.execution.misc.Local.Context Local.Context]]
  * upon execution of `run()`.
  *
  * Used by [[TracingScheduler]].
  */
final class TracingRunnable(r: Runnable, contextRef: Local.Context = Local.getContext()) extends Runnable {

  override def run(): Unit = {
    val prev = Local.getContext()
    Local.setContext(contextRef)
    try r.run()
    finally Local.setContext(prev)
  }
}
