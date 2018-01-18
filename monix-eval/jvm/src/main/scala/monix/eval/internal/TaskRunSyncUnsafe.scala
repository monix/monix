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

import java.util.concurrent.TimeoutException
import java.util.concurrent.locks.AbstractQueuedSynchronizer

import monix.eval.Task.{Async, Context, Error, Eval, FlatMap, Map, MemoizeSuspend, Now, Suspend}
import monix.eval.internal.TaskRunLoop._
import monix.eval.{Callback, Task}
import monix.execution.Scheduler
import monix.execution.internal.collection.ArrayStack
import monix.execution.misc.NonFatal

import scala.concurrent.blocking
import scala.concurrent.duration.{Duration, FiniteDuration}
import scala.util.{Failure, Success}

private[eval] object TaskRunSyncUnsafe {
  /** Run-loop specialization that evaluates the given task and blocks for the result
    * if the given task is asynchronous.
    */
  def apply[A](source: Task[A], timeout: Duration, scheduler: Scheduler, opts: Task.Options): A = {
    var current = source.asInstanceOf[Task[Any]]
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
          bFirst = bindNext
          current = fa

        case Now(value) =>
          unboxed = value.asInstanceOf[AnyRef]
          hasUnboxed = true

        case Eval(thunk) =>
          try {
            unboxed = thunk().asInstanceOf[AnyRef]
            hasUnboxed = true
          } catch {
            case e if NonFatal(e) =>
              current = Error(e)
          }

        case bindNext @ Map(fa, _, _) =>
          if (bFirst ne null) {
            if (bRest eq null) bRest = new ArrayStack()
            bRest.push(bFirst)
          }
          bFirst = bindNext
          current = fa

        case Suspend(thunk) =>
          // Try/catch described as statement to prevent ObjectRef ;-)
          try {
            current = thunk()
          }
          catch {
            case ex if NonFatal(ex) => current = Error(ex)
          }

        case Error(error) =>
          findErrorHandler(bFirst, bRest) match {
            case null => throw error
            case bind =>
              // Try/catch described as statement to prevent ObjectRef ;-)
              try { current = bind.recover(error) }
              catch { case e if NonFatal(e) => current = Error(e) }
              bFirst = null
          }

        case Async(register) =>
          return blockForResult(current, register, timeout, scheduler, opts, bFirst, bRest)

        case ref: MemoizeSuspend[_] =>
          // Already processed?
          ref.value match {
            case Some(materialized) =>
              materialized match {
                case Success(value) =>
                  unboxed = value.asInstanceOf[AnyRef]
                  hasUnboxed = true
                case Failure(error) =>
                  current = Error(error)
              }
            case None =>
              return blockForResult(current, null, timeout, scheduler, opts, bFirst, bRest)
          }
      }

      if (hasUnboxed) {
        popNextBind(bFirst, bRest) match {
          case null =>
            return unboxed.asInstanceOf[A]
          case bind =>
            // Try/catch described as statement to prevent ObjectRef ;-)
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
    } while (true)
    // $COVERAGE-OFF$
    throw new IllegalStateException("out of loop")
    // $COVERAGE-ON$
  }

  private def blockForResult[A](
    source: Current,
    register: (Context, Callback[Any]) => Unit = null,
    limit: Duration,
    scheduler: Scheduler,
    opts: Task.Options,
    bFirst: Bind,
    bRest: CallStack): A = {

    val latch = new OneShotLatch
    val cb = new BlockingCallback[Any](latch)
    val context = Context(scheduler, opts)

    // Starting actual execution
    if (register ne null) {
      val rcb = new RestartCallback(context, cb)
      executeAsyncTask(context, register, cb, rcb, bFirst, bRest, 1)
    } else {
      val fa = source.asInstanceOf[Task[A]]
      startFull(fa, context, cb, null, bFirst, bRest, 1)
    }

    val isFinished = limit match {
      case e if e eq Duration.Undefined =>
        throw new IllegalArgumentException("Cannot wait for Undefined period")
      case Duration.Inf =>
        blocking(latch.acquireSharedInterruptibly(1))
        true
      case f: FiniteDuration if f > Duration.Zero =>
        blocking(latch.tryAcquireSharedNanos(1, f.toNanos))
      case _ =>
        false
    }

    if (isFinished)
      cb.value.asInstanceOf[A]
    else
      throw new TimeoutException(s"Task.runSyncUnsafe($limit)")
  }

  private final class BlockingCallback[A](latch: OneShotLatch)
    extends Callback [A] {

    private[this] var success: A = _
    private[this] var error: Throwable = _

    def value: A =
      error match {
        case null => success
        case e => throw e
      }

    def onSuccess(value: A): Unit = {
      success = value
      latch.releaseShared(1)
    }

    def onError(ex: Throwable): Unit = {
      error = ex
      latch.releaseShared(1)
    }
  }

  private final class OneShotLatch extends AbstractQueuedSynchronizer {
    override protected def tryAcquireShared(ignored: Int): Int =
      if (getState != 0) 1 else -1

    override protected def tryReleaseShared(ignore: Int): Boolean = {
      setState(1)
      true
    }
  }
}
