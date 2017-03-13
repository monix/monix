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

private[eval] object IterantMap {
  /**
    * Implementation for `Iterant#map`
    */
  def apply[A, B](source: Iterant[A], f: A => B): Iterant[B] = {
    def loop(source: Iterant[A]): Iterant[B] =
      try source match {
        case Next(head, tail, stop) =>
          Next(f(head), tail.map(loop), stop)

        case NextSeq(cursor, rest, stop) =>
          NextSeq(cursor.map(f), rest.map(loop), stop)

        case NextGen(gen, rest, stop) =>
          NextGen(gen.map(f), rest.map(loop), stop)

        case Suspend(rest, stop) =>
          Suspend(rest.map(loop), stop)

        case Last(item) =>
          Last(f(item))

        case empty @ Halt(_) =>
          empty
      }
      catch {
        case NonFatal(ex) => signalError(source, ex)
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
