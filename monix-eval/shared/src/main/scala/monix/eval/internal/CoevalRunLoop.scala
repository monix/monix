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

package monix.eval.internal

import monix.eval.Coeval
import monix.eval.Coeval.{Always, Eager, Error, FlatMap, Map, Now, Once, Suspend}
import monix.execution.internal.collection.ArrayStack
import monix.execution.misc.NonFatal

private[eval] object CoevalRunLoop {
  private type Current = Coeval[Any]
  private type Bind = Any => Coeval[Any]
  private type CallStack = ArrayStack[Bind]

  /** Trampoline for lazy evaluation. */
  def start[A](source: Coeval[A]): Eager[A] = {
    var current: Current = source
    var bFirst: Bind = null
    var bRest: CallStack = null
    // Values from Now, Always and Once are unboxed in this var, for code reuse
    var hasUnboxed: Boolean = false
    var unboxed: AnyRef = null

    do {
      current match {
        case FlatMap(fa, bindNext) =>
          if (bFirst ne null) {
            if (bRest eq null) bRest = new ArrayStack()
            bRest.push(bFirst)
          }
          bFirst = bindNext.asInstanceOf[Bind]
          current = fa

        case Now(value) =>
          unboxed = value.asInstanceOf[AnyRef]
          hasUnboxed = true

        case Always(thunk) =>
          try {
            unboxed = thunk().asInstanceOf[AnyRef]
            hasUnboxed = true
            current = null
          } catch { case e if NonFatal(e) =>
            current = Error(e)
          }

        case bindNext @ Map(fa, _, _) =>
          if (bFirst ne null) {
            if (bRest eq null) bRest = new ArrayStack()
            bRest.push(bFirst)
          }
          bFirst = bindNext.asInstanceOf[Bind]
          current = fa

        case Suspend(thunk) =>
          // Try/catch described as statement, otherwise ObjectRef happens ;-)
          try { current = thunk() }
          catch { case ex if NonFatal(ex) => current = Error(ex) }

        case eval @ Once(_) =>
          current = eval.run

        case ref @ Error(ex) =>
          findErrorHandler(bFirst, bRest) match {
            case null =>
              return ref
            case bind =>
              // Try/catch described as statement, otherwise ObjectRef happens ;-)
              try { current = bind.recover(ex) }
              catch { case e if NonFatal(e) => current = Error(e) }
              bFirst = null
          }
      }

      if (hasUnboxed) {
        popNextBind(bFirst, bRest) match {
          case null =>
            return (if (current ne null) current else Now(unboxed)).asInstanceOf[Eager[A]]
          case bind =>
            // Try/catch described as statement, otherwise ObjectRef happens ;-)
            try { current = bind(unboxed) }
            catch { case ex if NonFatal(ex) => current = Error(ex) }
            hasUnboxed = false
            unboxed = null
            bFirst = null
        }
      }
    } while (true)
    // $COVERAGE-OFF$
    null // Unreachable code
    // $COVERAGE-ON$
  }

  private def findErrorHandler(bFirst: Bind, bRest: CallStack): StackFrame[Any, Coeval[Any]] = {
    var result: StackFrame[Any, Coeval[Any]] = null
    var cursor = bFirst
    var continue = true

    while (continue) {
      if (cursor != null && cursor.isInstanceOf[StackFrame[_, _]]) {
        result = cursor.asInstanceOf[StackFrame[Any, Coeval[Any]]]
        continue = false
      } else {
        cursor = if (bRest ne null) bRest.pop() else null
        continue = cursor != null
      }
    }
    result
  }

  private def popNextBind(bFirst: Bind, bRest: CallStack): Bind = {
    if ((bFirst ne null) && !bFirst.isInstanceOf[StackFrame.ErrorHandler[_, _]])
      return bFirst

    if (bRest eq null) return null
    do {
      bRest.pop() match {
        case null => return null
        case _: StackFrame.ErrorHandler[_, _] => // next please
        case ref => return ref
      }
    } while (true)
    // $COVERAGE-OFF$
    null
    // $COVERAGE-ON$
  }
}
