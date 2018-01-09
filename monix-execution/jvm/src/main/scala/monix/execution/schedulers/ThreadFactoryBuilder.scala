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

import java.lang.Thread.UncaughtExceptionHandler
import java.util.concurrent.ThreadFactory
import monix.execution.UncaughtExceptionReporter

private[schedulers] object ThreadFactoryBuilder {
  /** Constructs a ThreadFactory using the provided name prefix and appending
    * with a unique incrementing thread identifier.
    *
    * @param name     the created threads name prefix, for easy identification.
    * @param daemonic specifies whether the created threads should be daemonic
    *                 (non-daemonic threads are blocking the JVM process on exit).
    */
  def apply(name: String, reporter: UncaughtExceptionReporter, daemonic: Boolean): ThreadFactory = {
    new ThreadFactory {
      def newThread(r: Runnable) = {
        val thread = new Thread(r)
        thread.setName(name + "-" + thread.getId)
        thread.setDaemon(daemonic)
        thread.setUncaughtExceptionHandler(
          new UncaughtExceptionHandler {
            override def uncaughtException(t: Thread, e: Throwable): Unit =
              reporter.reportFailure(e)
          })

        thread
      }
    }
  }
}
