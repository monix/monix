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

import monix.eval.{Iterant, Task}
import monix.eval.Iterant._
import monix.eval.internal.IterantUtils._
import monix.eval.internal.IterantStop.earlyStop
import scala.util.control.NonFatal

private[eval] object IterantFilter {
  /**
    * Implementation for `Iterant#filter`
    */
  def apply[F[_], A](source: Iterant[A], p: A => Boolean): Iterant[A] = {
    def loop(source: Iterant[A]): Iterant[A] = {
      try source match {
        case Next(item, rest, stop) =>
          if (p(item)) Next(item, rest.map(loop), stop)
          else Suspend(rest.map(loop), stop)

        case NextSeq(items, rest, stop) =>
          val filtered = items.filter(p)
          if (filtered.hasNext)
            NextSeq(filtered, rest.map(loop), stop)
          else
            Suspend(rest.map(loop), stop)

        case NextGen(items, rest, stop) =>
          NextGen(items.filter(p), rest.map(loop), stop)

        case Suspend(rest, stop) =>
          Suspend(rest.map(loop), stop)

        case last @ Last(item) =>
          if (p(item)) last else Halt(None)

        case halt @ Halt(_) =>
          halt
      }
      catch {
        case NonFatal(ex) => signalError(source, ex)
      }
    }

    source match {
      case Suspend(_, _) | Halt(_) => loop(source)
      case _ =>
        // Given function can be side-effecting,
        // so we must suspend the execution
        Suspend(Task.eval(loop(source)), earlyStop(source))
    }
  }
}
