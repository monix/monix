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

package monix.streams.internal.operators

import monix.execution.Ack.Continue
import monix.execution.cancelables.{CompositeCancelable, MultiAssignmentCancelable}
import monix.execution.{Ack, Cancelable}
import monix.streams.Observable
import monix.streams.observers.Subscriber
import monix.streams.subjects.{ReplaySubject, Subject}
import scala.concurrent.Future
import scala.util.Success

private[streams] final class RepeatObservable[A](source: Observable[A])
  extends Observable[A] {

  // recursive function - subscribes the observer again when
  // onComplete happens
  def loop(subject: Subject[A, A], out: Subscriber[A],
    task: MultiAssignmentCancelable, index: Long): Unit = {

    val cancelable = subject.unsafeSubscribeFn(new Subscriber[A] {
      implicit val scheduler = out.scheduler
      private[this] var isDone = false
      private[this] var ack: Future[Ack] = Continue

      def onNext(elem: A): Future[Ack] = {
        ack = out.onNext(elem)
        ack
      }

      def onError(ex: Throwable): Unit =
        if (!isDone) {
          isDone = true
          out.onError(ex)
        }

      def onComplete(): Unit =
        if (!isDone) {
          isDone = true
          // Creating an asynchronous boundary, otherwise we might
          // blow up the stack.
          ack.onComplete {
            case Success(Continue) =>
              loop(subject, out, task, index + 1)
            case _ =>
              () // do nothing
          }
        }
    })

    // We need to do an `orderedUpdate`, because `onComplete` might have
    // already executed and we might be resubscribed by now.
    task.orderedUpdate(cancelable, index)
  }

  def unsafeSubscribeFn(out: Subscriber[A]): Cancelable = {
    val subject = ReplaySubject[A]()
    val repeatTask = MultiAssignmentCancelable()
    loop(subject, out, repeatTask, index=0)

    val mainTask = source.unsafeSubscribeFn(Subscriber(subject, out.scheduler))
    CompositeCancelable(mainTask, repeatTask)
  }
}
