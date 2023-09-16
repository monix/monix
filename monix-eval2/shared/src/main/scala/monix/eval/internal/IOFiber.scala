/*
 * Copyright (c) 2014-2022 Monix Contributors.
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

package monix.eval
package internal

import cats.effect.kernel.{Fiber, Outcome}
import monix.eval.IO.RaiseError
import monix.eval.internal.IOFiber._
import monix.execution.{Callback, Scheduler}
import scala.annotation.switch
import scala.util.control.NonFatal

private[eval] final class IOFiber[A] private[eval] (
  source: IO[A],
  cb: Callback[Throwable, A],
  initCallStack: IOCallStack = null,
  initIsCancelled: Boolean = false
)(implicit
  scheduler: Scheduler
) extends Fiber[IO, Throwable, A] with IO.Visitor[Any, Control] with Runnable {
  // TODO: add unboxed optimization
  private[this] var currentRef: Current = source
  private[this] var callStackRef: IOCallStack = initCallStack
  private[this] var _restartCallback: IORestartCallback = _
  private[this] var isCanceled: Boolean = initIsCancelled

  @inline
  private def callStack: IOCallStack = {
    if (callStackRef eq null) callStackRef = new IOCallStack(8)
    callStackRef
  }

  override def visit(ref: IO.Pure[Any]): Control =
    processUnboxedValue(ref.a.asInstanceOf[AnyRef])

  override def visit[S](ref: IO.FlatMap[S, Any]): Control = {
    callStack.pushFlatMap(ref.f.asInstanceOf[Any => IO[Any]])
    currentRef = ref.source
    Continue
  }

  override def visit[S](ref: IO.HandleErrorWith[S, Any]): Control = {
    callStack.pushHandleError(ref.f)
    currentRef = ref.source
    Continue
  }

  override def visit(ref: IO.OnCancel[Any]): Control = {
    callStack.pushOnCancel(ref.onCancel)
    currentRef = ref.source
    Continue
  }

  override def visit(ref: IO.RaiseError): Control = {
    val err = ref.e
    callStack.findAndPopNextHandleError() match {
      case null =>
        cb.onError(err)
        Break
      case bind =>
        // Try/catch described as statement, otherwise ObjectRef happens ;-)
        try {
          currentRef = bind(err)
        } catch {
          case e if NonFatal(e) =>
            currentRef = RaiseError(e)
        }
        Continue
    }
  }

  override def visit(ref: IO.AsyncSimple[Any]): Control = {
    restartCallback().start(ref)
    Break
  }

  override def visit[S](ref: IO.AsyncCont[S, Any]): Control = {
    val cb1 = new IOCallbackIndirection[Throwable, S]
    val fs = IO.AsyncSimple((_, cb2) => cb1.register(cb2))
    currentRef = ref.cont(scheduler, cb1, fs)
    Continue
  }


  override def visit(ref: IO.Cancelled.type): Control = {
    isCanceled = true
    callStack.findAndPopNextOnCancel() match {
      case null =>
        currentRef = ref
        Break
      case onCancel =>
        currentRef = onCancel
        Continue
    }
  }

  def continueWithRef(ref: Current): Unit = {
    currentRef = ref
    run()
  }

  override def run(): Unit = {
    var continue = Continue
    while (continue) {
      (currentRef.tag: @switch) match {
        case 0 =>
          continue = visit(currentRef.asInstanceOf[IO.Pure[AnyRef]])
        case 1 =>
          continue = visit(currentRef.asInstanceOf[IO.RaiseError])
        case 2 =>
          continue = visit(currentRef.asInstanceOf[IO.FlatMap[Any, Any]])
        case _ =>
          continue = currentRef.accept(this)
      }
    }
  }

  override def cancel: IO[Unit] =
    IO.raiseError(new NotImplementedError("IOFiber.cancel"))

  override def join: IO[Outcome[IO, Throwable, A]] =
    IO.raiseError(new NotImplementedError("IOFiber.join"))

  private def processUnboxedValue(unboxedRef: AnyRef): Control = {
    callStack.findAndPopNextFlatMap() match {
      case null =>
        cb.onSuccess(unboxedRef.asInstanceOf[A])
        Break
      case bind =>
        // Try/catch described as statement to prevent ObjectRef ;-)
        try {
          currentRef = bind(unboxedRef)
        } catch {
          case ex if NonFatal(ex) =>
            currentRef = IO.RaiseError(ex)
        }
        Continue
    }
  }

  private def restartCallback(): IORestartCallback = {
    if (_restartCallback == null)
      _restartCallback = new IORestartCallback(this, scheduler)
    _restartCallback
  }
}

object IOFiber {
  private type Current = IO[Any]

  private type Control = Boolean
  private final val Continue: Control = true
  private final val Break: Control = false
}
