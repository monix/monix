/*
 * Copyright (c) 2014-2021 by The Monix Project Developers.
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
import monix.eval.Coeval.{ Always, Eager, Error, FlatMap, Map, Now, Suspend, Trace }
import monix.eval.tracing.{ CoevalEvent, CoevalTrace }
import monix.execution.internal.collection.ChunkedArrayStack
import monix.eval.internal.TracingPlatform.{ enhancedExceptions, isStackTracing }

import scala.reflect.NameTransformer
import scala.util.control.NonFatal

private[eval] object CoevalRunLoop {
  private type Current = Coeval[Any]
  private type Bind = Any => Coeval[Any]
  private type CallStack = ChunkedArrayStack[Bind]

  /** Trampoline for lazy evaluation. */
  def start[A](source: Coeval[A]): Eager[A] = {
    var current: Current = source
    var bFirst: Bind = null
    var bRest: CallStack = null
    // Values from Now, Always and Once are unboxed in this var, for code reuse
    var hasUnboxed: Boolean = false
    var unboxed: AnyRef = null
    var tracingCtx: CoevalStackTracedContext = null

    while (true) {
      current match {
        case bind @ FlatMap(fa, bindNext, _) =>
          if (isStackTracing) {
            val trace = bind.trace
            if (tracingCtx eq null) tracingCtx = new CoevalStackTracedContext
            if (trace ne null) tracingCtx.pushEvent(trace.asInstanceOf[CoevalEvent])
          }

          if (bFirst ne null) {
            if (bRest eq null) bRest = ChunkedArrayStack()
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
          } catch {
            case e if NonFatal(e) =>
              current = Error(e)
          }

        case bindNext @ Map(fa, _, _) =>
          if (isStackTracing) {
            val trace = bindNext.trace
            if (tracingCtx eq null) tracingCtx = new CoevalStackTracedContext
            if (trace ne null) tracingCtx.pushEvent(trace.asInstanceOf[CoevalEvent])
          }

          if (bFirst ne null) {
            if (bRest eq null) bRest = ChunkedArrayStack()
            bRest.push(bFirst)
          }
          bFirst = bindNext.asInstanceOf[Bind]
          current = fa

        case Suspend(thunk) =>
          // Try/catch described as statement, otherwise ObjectRef happens ;-)
          try {
            current = thunk()
          } catch {
            case ex if NonFatal(ex) =>
              current = Error(ex)
          }

        case ref @ Error(ex) =>
          if (isStackTracing && enhancedExceptions) {
            if (tracingCtx eq null) tracingCtx = new CoevalStackTracedContext
            augmentException(ex, tracingCtx)
          }

          findErrorHandler(bFirst, bRest) match {
            case null =>
              return ref
            case bind =>
              // Try/catch described as statement, otherwise ObjectRef happens ;-)
              try {
                current = bind.recover(ex)
              } catch { case e if NonFatal(e) => current = Error(e) }
              bFirst = null
          }

        case Trace(sourceTask, frame) =>
          if (tracingCtx eq null) tracingCtx = new CoevalStackTracedContext
          tracingCtx.pushEvent(frame)
          current = sourceTask
      }

      if (hasUnboxed) {
        popNextBind(bFirst, bRest) match {
          case null =>
            return (if (current ne null) current else Now(unboxed)).asInstanceOf[Eager[A]]
          case bind =>
            // Try/catch described as statement, otherwise ObjectRef happens ;-)
            try {
              current = bind(unboxed)
            } catch {
              case ex if NonFatal(ex) => current = Error(ex)
            }
            hasUnboxed = false
            unboxed = null
            bFirst = null
        }
      }
    }
    // $COVERAGE-OFF$
    null // Unreachable code
    // $COVERAGE-ON$
  }

  private def findErrorHandler(bFirst: Bind, bRest: CallStack): StackFrame[Any, Coeval[Any]] = {
    bFirst match {
      case ref: StackFrame[Any, Coeval[Any]] @unchecked => ref
      case _ =>
        if (bRest eq null) null
        else {
          while (true) {
            val ref = bRest.pop()
            if (ref eq null)
              return null
            else if (ref.isInstanceOf[StackFrame[_, _]])
              return ref.asInstanceOf[StackFrame[Any, Coeval[Any]]]
          }
          // $COVERAGE-OFF$
          null
          // $COVERAGE-ON$
        }
    }
  }

  private def popNextBind(bFirst: Bind, bRest: CallStack): Bind = {
    if ((bFirst ne null) && !bFirst.isInstanceOf[StackFrame.ErrorHandler[_, _]])
      return bFirst

    if (bRest eq null) return null
    while (true) {
      val next = bRest.pop()
      if (next eq null) {
        return null
      } else if (!next.isInstanceOf[StackFrame.ErrorHandler[_, _]]) {
        return next
      }
    }
    // $COVERAGE-OFF$
    null
    // $COVERAGE-ON$
  }

  /**
    * If stack tracing and contextual exceptions are enabled, this
    * function will rewrite the stack trace of a captured exception
    * to include the async stack trace.
    */
  private[internal] def augmentException(ex: Throwable, ctx: CoevalStackTracedContext): Unit = {
    val stackTrace = ex.getStackTrace
    if (stackTrace.nonEmpty) {
      val augmented = stackTrace(stackTrace.length - 1).getClassName.indexOf('@') != -1
      if (!augmented) {
        val prefix = dropRunLoopFrames(stackTrace)
        val suffix = ctx
          .getStackTraces()
          .flatMap(t => CoevalTrace.getOpAndCallSite(t.stackTrace))
          .map {
            case (methodSite, callSite) =>
              val op = NameTransformer.decode(methodSite.getMethodName)

              new StackTraceElement(
                op + " @ " + callSite.getClassName,
                callSite.getMethodName,
                callSite.getFileName,
                callSite.getLineNumber
              )
          }
          .toArray
        ex.setStackTrace(prefix ++ suffix)
      }
    }
  }

  private def dropRunLoopFrames(frames: Array[StackTraceElement]): Array[StackTraceElement] =
    frames.takeWhile(ste => !runLoopFilter.exists(ste.getClassName.startsWith(_)))

  private[this] val runLoopFilter = List(
    "monix.eval.",
    "scala.runtime."
  )
}
