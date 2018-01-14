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

package monix.execution.schedulers

import monix.execution.misc.Local
import monix.execution.misc.Local.Context

/** Wraps a `Runnable` into one that restores the given
  * [[monix.execution.misc.Local.Context Local.Context]]
  * upon execution of `run()`.
  *
  * Used by [[TracingScheduler]].
  */
final class TracingRunnable(r: Runnable, context: Context = Local.getContext())
  extends Runnable {

  override def run(): Unit = {
    val prev: Context = Local.getContext()
    Local.setContext(context)
    try r.run() finally Local.setContext(prev)
  }
}
