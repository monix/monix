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

import cats.effect.CancelToken
import monix.catnap.CancelableF
import monix.execution.Callback
import monix.eval.Task
import monix.execution.atomic.{ Atomic, PaddingStrategy }

private[eval] object TaskRaceList {
  /**
    * Implementation for `Task.raceList`
    */
  def apply[A](tasks: Iterable[Task[A]]): Task[A] =
    Task.Async(new Register(tasks), trampolineBefore = true, trampolineAfter = true)

  // Implementing Async's "start" via `ForkedStart` in order to signal
  // that this is a task that forks on evaluation.
  //
  // N.B. the contract is that the injected callback gets called after
  // a full async boundary!
  private final class Register[A](tasks: Iterable[Task[A]]) extends ForkedRegister[A] {

    def apply(context: Task.Context, callback: Callback[Throwable, A]): Unit = {
      implicit val s = context.scheduler
      val conn = context.connection

      val isActive = Atomic.withPadding(true, PaddingStrategy.LeftRight128)
      val taskArray = tasks.toArray
      val cancelableArray = buildCancelableArray(taskArray.length)
      conn.pushConnections(cancelableArray.toIndexedSeq: _*)

      var index = 0
      while (index < taskArray.length) {
        val task = taskArray(index)
        val taskCancelable = cancelableArray(index)
        val taskContext = context.withConnection(taskCancelable)
        index += 1

        Task.unsafeStartEnsureAsync(
          task,
          taskContext,
          new Callback[Throwable, A] {
            private def popAndCancelRest(): CancelToken[Task] = {
              conn.pop()
              val arr2 = cancelableArray.collect {
                case cc if cc ne taskCancelable =>
                  cc.cancel
              }
              CancelableF.cancelAllTokens[Task](arr2.toIndexedSeq: _*)
            }

            def onSuccess(value: A): Unit =
              if (isActive.getAndSet(false)) {
                popAndCancelRest().map(_ => callback.onSuccess(value)).runAsyncAndForget
              }

            def onError(ex: Throwable): Unit =
              if (isActive.getAndSet(false)) {
                popAndCancelRest().map(_ => callback.onError(ex)).runAsyncAndForget
              } else {
                s.reportFailure(ex)
              }
          }
        )
      }
    }
  }

  private def buildCancelableArray(n: Int): Array[TaskConnection] = {
    val array = new Array[TaskConnection](n)
    var i = 0
    while (i < n) { array(i) = TaskConnection(); i += 1 }
    array
  }
}
