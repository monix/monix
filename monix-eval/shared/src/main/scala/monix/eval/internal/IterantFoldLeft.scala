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
import scala.collection.mutable
import scala.util.control.NonFatal

private[eval] object IterantFoldLeft {
  /**
    * Implementation for `Iterant#foldLeftL`
    */
  final def apply[S, A](source: Iterant[A], seed: => S)(op: (S,A) => S): Task[S] = {
    def loop(self: Iterant[A], state: S): Task[S] = {
      try self match {
        case Next(a, rest, stop) =>
          val newState = op(state, a)
          rest.flatMap(loop(_, newState))
        case NextSeq(cursor, rest, stop) =>
          val newState = cursor.foldLeft(state)(op)
          rest.flatMap(loop(_, newState))
        case NextGen(gen, rest, stop) =>
          val newState = gen.iterator.foldLeft(state)(op)
          rest.flatMap(loop(_, newState))
        case Suspend(rest, stop) =>
          rest.flatMap(loop(_, state))
        case Last(item) =>
          Task.now(op(state,item))
        case Halt(None) =>
          Task.now(state)
        case Halt(Some(ex)) =>
          Task.raiseError(ex)
      }
      catch {
        case NonFatal(ex) =>
          source.earlyStop.flatMap(_ => Task.raiseError(ex))
      }
    }

    Task.defer {
      var catchErrors = true
      try {
        val init = seed
        catchErrors = false
        loop(source, init)
      }
      catch {
        case NonFatal(ex) if catchErrors =>
          source.earlyStop.flatMap(_ => Task.raiseError(ex))
      }
    }
  }

  /**
    * Implementation for `Iterant#toListL`
    */
  def toListL[A](source: Iterant[A]): Task[List[A]] = {
    IterantFoldLeft(source, mutable.ListBuffer.empty[A])((acc, a) => acc += a)
      .map(_.toList)
  }
}
