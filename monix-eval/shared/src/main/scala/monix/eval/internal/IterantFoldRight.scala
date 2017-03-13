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

import monix.eval.Iterant.{Halt, Last, Next, NextGen, NextSeq, Suspend}
import monix.eval.{Iterant, Task}

import scala.util.control.NonFatal

private[eval] object IterantFoldRight {
  /**
    * Implementation for `Iterant#foldRightL`
    */
  def apply[A, B](source: Iterant[A], b: Task[B])(f: (A, Task[B]) => Task[B]): Task[B] = {
    @inline def evalNextSeq(items: Iterator[A], rest: Task[Iterant[A]], stop: Task[Unit]): Task[B] = {
      if (!items.hasNext)
        rest.flatMap(loop)
      else {
        val item = items.next()
        // Skipping a beat of items is empty?
        val tail = if (items.hasNext) Task.now(NextSeq(items, rest, stop)) else rest
        f(item, tail.flatMap(loop))
      }
    }

    def loop(source: Iterant[A]): Task[B] =
      try source match {
        case Next(item, rest, stop) =>
          f(item, rest.flatMap(loop))

        case NextSeq(items, rest, stop) =>
          evalNextSeq(items, rest, stop)

        case NextGen(items, rest, stop) =>
          evalNextSeq(items.iterator, rest, stop)

        case Suspend(rest, _) =>
          rest.flatMap(loop)

        case Last(item) =>
          f(item, b)

        case Halt(exOpt) =>
          exOpt match {
            case None => b
            case Some(ex) => Task.raiseError(ex)
          }
      }
      catch {
        case NonFatal(ex) =>
          val stop = IterantStop.earlyStop(source)
          stop.flatMap(_ => Task.raiseError(ex))
      }

    source match {
      case Suspend(_, _) | Halt(_) => loop(source)
      case _ =>
        // Given function can be side-effecting,
        // so we must suspend the execution
        Task.defer(loop(source))
    }
  }
}
