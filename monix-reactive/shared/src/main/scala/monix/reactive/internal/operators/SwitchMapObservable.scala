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

package monix.reactive.internal.operators

import monix.execution.Ack.{Continue, Stop}
import monix.execution.cancelables.{CompositeCancelable, SerialCancelable, SingleAssignmentCancelable}
import monix.execution.misc.NonFatal
import monix.execution.{Ack, Cancelable}
import monix.reactive.observers.Subscriber
import monix.reactive.{Observable, Observer}

import scala.concurrent.Future

private[reactive] final class SwitchMapObservable[A,B](
  source: Observable[A], f: A => Observable[B])
  extends Observable[B] {

  def unsafeSubscribeFn(out: Subscriber[B]): Cancelable = {
    val activeChild = SerialCancelable()
    val mainTask = SingleAssignmentCancelable()
    val composite = CompositeCancelable(activeChild, mainTask)

    mainTask := source.unsafeSubscribeFn(new Subscriber.Sync[A] { self =>
      implicit val scheduler = out.scheduler
      // MUST BE synchronized by `self`
      private[this] var ack: Future[Ack] = Continue
      // MUST BE synchronized by `self`
      private[this] var activeChildIndex: Int = -1
      // MUST BE synchronized by `self`
      private[this] var upstreamIsDone: Boolean = false

      def onNext(elem: A): Ack = self.synchronized {
        if (upstreamIsDone) Stop else {
          // Protects calls to user code from within the operator.
          val childObservable =
            try f(elem) catch {
              case NonFatal(ex) =>
                Observable.raiseError(ex)
            }

          ack = ack.syncTryFlatten
          activeChildIndex += 1
          val myChildIndex = activeChildIndex

          activeChild := childObservable.unsafeSubscribeFn(new Observer[B] {
            def onNext(elem: B) =
              self.synchronized {
                if (upstreamIsDone || myChildIndex != activeChildIndex)
                  Stop
                else {
                  ack = out.onNext(elem).syncOnStopOrFailure(_ => cancelFromDownstream())
                  ack
                }
              }

            def onComplete(): Unit = ()
            def onError(ex: Throwable): Unit =
              self.synchronized {
                if (myChildIndex == activeChildIndex)
                  self.onError(ex)
              }
          })

          Continue
        }
      }

      def cancelFromDownstream(): Stop = self.synchronized {
        if (upstreamIsDone) Stop else {
          upstreamIsDone = true
          activeChildIndex = -1
          ack = Stop
          mainTask.cancel()
          Stop
        }
      }

      def onError(ex: Throwable): Unit = self.synchronized {
        if (!upstreamIsDone) {
          upstreamIsDone = true
          activeChildIndex = -1
          composite.cancel()
          out.onError(ex)
        }
      }

      def onComplete(): Unit = self.synchronized {
        if (!upstreamIsDone) {
          upstreamIsDone = true
          activeChildIndex = -1
          activeChild.cancel()
          out.onComplete()
        }
      }
    })

    composite
  }
}