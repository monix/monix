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
    // Guards against user errors signaled by the returned Task
    @inline def asyncErrorGuard(fa: Task[B], stop: Task[Unit]): Task[B] =
      fa.onErrorHandleWith(ex => stop.flatMap(_ => Task.raiseError(ex)))

    @inline def evalNextSeq(ref: NextSeq[A], items: Iterator[A], rest: Task[Iterant[A]], stop: Task[Unit]): Task[B] = {
      if (!items.hasNext)
        rest.flatMap(loop)
      else {
        val item = items.next()
        // Skipping a beat of empty
        val tail = if (items.hasNext) Task.now(ref) else rest
        val fa = f(item, tail.flatMap(loop))
        asyncErrorGuard(fa, stop)
      }
    }

    def loop(source: Iterant[A]): Task[B] =
      try source match {
        case Next(item, rest, stop) =>
          asyncErrorGuard(f(item, rest.flatMap(loop)), stop)

        case ref @ NextSeq(items, rest, stop) =>
          evalNextSeq(ref, items, rest, stop)

        case ref @ NextGen(items, rest, stop) =>
          val ref = NextSeq(items.iterator, rest, stop)
          evalNextSeq(ref, ref.items, rest, stop)

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
