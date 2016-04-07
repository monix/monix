/*
 * Copyright (c) 2014-2016 by its authors. Some rights reserved.
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

package monix.reactive.internal.builders

import monix.eval._
import monix.eval.ConsStream.{Empty, Error, NextSeq, Next}
import monix.execution.Ack
import monix.execution.Ack.Continue
import monix.execution.cancelables.SingleAssignmentCancelable
import monix.reactive.Observable
import monix.reactive.observers.Subscriber
import scala.concurrent.{Future, Promise}

private[reactive] object ObservableToTaskEnumerator {
  def apply[A](source: Observable[A], batchSize: Int): Task[TaskEnumerator[A]] =
    buildStream(source, batchSize).map(TaskEnumerator.fromStream)

  def buildStream[A](source: Observable[A], batchSize: Int): Task[ConsStream[A,Task]] =
    Task.unsafeCreate { (context, cancelable, cb) =>
      val initial = Promise[ConsStream[A,Task]]()
      initial.future.onComplete(cb)(context)

      val mainTask = SingleAssignmentCancelable()
      cancelable push mainTask

      mainTask := source.bufferIntrospective(context.executionModel.recommendedBatchSize).unsafeSubscribeFn(
        new Subscriber[List[A]] { self =>
          implicit val scheduler = context
          private[this] var currentPromise = initial
          private[this] var ack: Future[Ack] = Continue
          private[this] var isDone = false

          def onNext(elems: List[A]): Future[Ack] = {
            val acknowledgement = Promise[Ack]()
            val currentPromise = this.currentPromise
            val restPromise = Promise[ConsStream[A,Task]]()
            this.currentPromise = restPromise

            // Task execution must be idempotent ;-)
            // When this executes, it means that the client wants more.
            val restTask: Task[ConsStream[A,Task]] =
              Task.unsafeCreate { (scheduler, c, cb) =>
                // Executing task means continuing Observer
                restPromise.future.onComplete(cb)(scheduler)
                // The acknowledgement unfreezes the observer, allowing it
                // to complete the nextPromise
                acknowledgement.trySuccess(Continue)
              }

            if (elems.length == 1)
              currentPromise.success(Next(elems.head, restTask))
            else
              currentPromise.success(NextSeq(elems, restTask))

            ack = acknowledgement.future
            ack
          }

          def onError(ex: Throwable): Unit =
            ack.syncOnContinue {
              if (!isDone) {
                isDone = true
                currentPromise.success(Error(ex))
              }
            }

          def onComplete(): Unit =
            ack.syncOnContinue {
              if (!isDone) {
                isDone = true
                currentPromise.success(Empty[Task]())
              }
            }
        })
    }
}
