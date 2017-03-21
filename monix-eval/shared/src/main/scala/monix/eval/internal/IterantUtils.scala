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

package monix.eval.internal

import monix.eval.Iterant
import monix.eval.Iterant._

private[eval] object IterantUtils {
  /** Used for error handling. */
  def signalError[A, B](source: Iterant[A], ex: Throwable): Iterant[B] = {
    val halt = Iterant.haltS[B](Some(ex))
    source match {
      case Next(_,_,stop) =>
        Suspend(stop.map(_ => halt), stop)
      case NextSeq(_,_,stop) =>
        Suspend(stop.map(_ => halt), stop)
      case NextGen(_,_,stop) =>
        Suspend(stop.map(_ => halt), stop)
      case Suspend(_,stop) =>
        Suspend(stop.map(_ => halt), stop)
      case Last(_) | Halt(_) =>
        halt
    }
  }
}
