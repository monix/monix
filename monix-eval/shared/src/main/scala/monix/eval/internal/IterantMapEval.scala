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
import scala.util.control.NonFatal

private[eval] object IterantMapEval {
  /**
    * Implementation for `Iterant#mapEval`
    */
  def apply[A, B](source: Iterant[A], f: A => Task[B]): Iterant[B] = {
    @inline def evalNextSeq(ref: NextSeq[A], cursor: Iterator[A], rest: Task[Iterant[A]], stop: Task[Unit]) = {
      if (!cursor.hasNext)
        Suspend[B](rest.map(loop), stop)
      else {
        val head = cursor.next()
        val fa = f(head)
        // If the iterator is empty, then we can skip a beat
        val tail = if (cursor.hasNext) Task.pure(ref) else rest
        val suspended = fa.map(h => nextS(h, tail.map(loop), stop))
        Suspend[B](suspended, stop)
      }
    }

    def loop(source: Iterant[A]): Iterant[B] =
      try source match {
        case Next(head, tail, stop) =>
          val fa = f(head)
          val rest = fa.map(h => nextS(h, tail.map(loop), stop))
          Suspend(rest, stop)

        case ref @ NextSeq(cursor, rest, stop) =>
          evalNextSeq(ref, cursor, rest, stop)

        case NextGen(gen, rest, stop) =>
          val cursor = gen.iterator
          val ref = NextSeq(cursor, rest, stop)
          evalNextSeq(ref, cursor, rest, stop)

        case Suspend(rest, stop) =>
          Suspend[B](rest.map(loop), stop)

        case Last(item) =>
          val fa = f(item)
          Suspend(fa.map(h => lastS[B](h)), Task.unit)

        case halt @ Halt(_) =>
          halt
      }
      catch {
        case NonFatal(ex) => signalError(source, ex)
      }

    source match {
      case Suspend(_, _) | Halt(_) => loop(source)
      case _ =>
        // Given function can be side-effecting,
        // so we must suspend the execution
        Suspend(Task.eval(loop(source)), source.earlyStop)
    }
  }
}
