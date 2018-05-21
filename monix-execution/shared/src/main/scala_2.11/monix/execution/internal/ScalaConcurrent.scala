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

package scala.concurrent

import monix.execution.schedulers.TrampolineExecutionContext.immediate
import scala.util.control.NonFatal
import scala.util.Try

object MonixInternals {
  /** Implements `transformWith` for Scala 2.11. */
  def transformWith[T, S](source: Future[T], f: Try[T] => Future[S])(implicit ec: ExecutionContext): Future[S] = {
    import impl.Promise.DefaultPromise

    val p = new DefaultPromise[S]()
    source.onComplete { result =>
      val fb = try f(result) catch { case t if NonFatal(t) => Future.failed(t) }
      fb match {
        // If possible, link DefaultPromises to avoid space leaks
        case dp: DefaultPromise[_] => dp.asInstanceOf[DefaultPromise[S]].linkRootOf(p)
        case fut => fut.onComplete(p.complete)(immediate)
      }
    }
    p.future
  }
}