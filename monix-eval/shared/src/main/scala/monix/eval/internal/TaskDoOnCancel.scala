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

package monix.eval.internal

import monix.eval.Task.{ Async, Context }
import monix.execution.Callback
import monix.eval.Task
import monix.execution.exceptions.CallbackCalledMultipleTimesException
import monix.execution.schedulers.TrampolinedRunnable

private[eval] object TaskDoOnCancel {
  /**
    * Implementation for `Task.doOnCancel`
    */
  def apply[A](self: Task[A], callback: Task[Unit]): Task[A] = {
    if (callback eq Task.unit) {
      self
    } else {
      val start = (context: Context, onFinish: Callback[Throwable, A]) => {
        implicit val s = context.scheduler
        context.connection.push(callback)
        Task.unsafeStartNow(self, context, new CallbackThatPops(context, onFinish))
      }
      Async(start, trampolineBefore = false, trampolineAfter = false, restoreLocals = false)
    }
  }

  private final class CallbackThatPops[A](ctx: Task.Context, cb: Callback[Throwable, A])
    extends Callback[Throwable, A] with TrampolinedRunnable {

    private[this] var isActive = true
    private[this] var value: A = _
    private[this] var error: Throwable = _

    override def onSuccess(value: A): Unit =
      if (!tryOnSuccess(value)) {
        throw new CallbackCalledMultipleTimesException("onSuccess")
      }

    override def onError(e: Throwable): Unit =
      if (!tryOnError(e)) {
        throw new CallbackCalledMultipleTimesException("onError")
      }

    override def tryOnSuccess(value: A): Boolean = {
      if (isActive) {
        isActive = false
        ctx.connection.pop()
        this.value = value
        ctx.scheduler.execute(this)
        true
      } else {
        false
      }
    }

    override def tryOnError(e: Throwable): Boolean = {
      if (isActive) {
        isActive = false
        ctx.connection.pop()
        this.error = e
        ctx.scheduler.execute(this)
        true
      } else {
        false
      }
    }

    override def run(): Unit = {
      if (error != null) {
        val e = error
        error = null
        cb.onError(e)
      } else {
        val v = value
        value = null.asInstanceOf[A]
        cb.onSuccess(v)
      }
    }
  }
}
