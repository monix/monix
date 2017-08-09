/*
 * Copyright (c) 2014-2017 by The Monix Project Developers.
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

import monix.eval.Coeval
import monix.eval.Coeval.{Always, Eager, Error, FlatMap, Now, Once, Suspend}
import monix.execution.internal.collection.ArrayStack
import monix.execution.misc.NonFatal
import scala.annotation.tailrec

private[eval] object CoevalRunLoop {
  private type Current = Coeval[Any]
  private type Bind = Any => Coeval[Any]
  private type CallStack = ArrayStack[Bind]

  /** Trampoline for lazy evaluation. */
  def start[A](source: Coeval[A]): Eager[A] = {
    // Tail-recursive run-loop
    @tailrec def loop(source: Coeval[Any], bFirst: Bind, bRest: CallStack): Eager[Any] = {
      source match {
        case now @ Now(a) =>
          popNextBind(bFirst, bRest) match {
            case null => now
            case bind =>
              val fa = try bind(a) catch { case NonFatal(ex) => Error(ex) }
              loop(fa, null, bRest)
          }

        case eval @ Once(_) =>
          loop(eval.runToEager, bFirst, bRest)

        case Always(thunk) =>
          val fa = try Now(thunk()) catch { case NonFatal(ex) => Error(ex) }
          loop(fa, bFirst, bRest)

        case Suspend(thunk) =>
          val fa = try thunk() catch { case NonFatal(ex) => Error(ex) }
          loop(fa, bFirst, bRest)

        case FlatMap(fa, f) =>
          var callStack: CallStack = bRest
          val bindNext = f.asInstanceOf[Bind]
          if (bFirst ne null) {
            if (callStack eq null) callStack = createCallStack()
            callStack.push(bFirst)
          }
          loop(fa, bindNext, callStack)

        case error @ Error(e) =>
          findErrorHandler(bFirst, bRest) match {
            case null => error
            case bind =>
              val fa = try bind.error(e) catch { case NonFatal(err) => Error(err) }
              loop(fa, null, bRest)
          }
      }
    }

    loop(source, null, null).asInstanceOf[Eager[A]]
  }

  /** Logic for finding the next `Transformation` reference,
    * meant for handling errors in the run-loop.
    */
  private def findErrorHandler(bFirst: Bind, bRest: CallStack): Transformation[Any, Coeval[Any]] = {
    var result: Transformation[Any, Coeval[Any]] = null
    var cursor = bFirst
    var continue = true

    while (continue) {
      if (cursor != null && cursor.isInstanceOf[Transformation[_, _]]) {
        result = cursor.asInstanceOf[Transformation[Any, Coeval[Any]]]
        continue = false
      } else {
        cursor = if (bRest ne null) bRest.pop() else null
        continue = cursor != null
      }
    }
    result
  }

  private def popNextBind(bFirst: Bind, bRest: CallStack): Bind = {
    if ((bFirst ne null) && !bFirst.isInstanceOf[Transformation.OnError[_,_]])
      bFirst
    else if (bRest ne null) {
      var cursor: Bind = null
      do { cursor = bRest.pop() }
      while(cursor != null && cursor.isInstanceOf[Transformation.OnError[_,_]])
      cursor
    } else {
      null
    }
  }

  /** Creates a new [[CallStack]]. */
  private def createCallStack(): CallStack =
    ArrayStack(4)
}
