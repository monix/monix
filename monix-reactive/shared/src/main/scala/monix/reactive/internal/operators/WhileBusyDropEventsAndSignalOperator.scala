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

import monix.execution.Ack
import monix.execution.Ack.{Continue, Stop}
import monix.execution.misc.NonFatal
import monix.reactive.observables.ObservableLike.Operator
import monix.reactive.observers.Subscriber

import scala.concurrent.Future

private[reactive] final
class WhileBusyDropEventsAndSignalOperator[A](onOverflow: Long => A)
  extends Operator[A,A] {

  def apply(out: Subscriber[A]): Subscriber.Sync[A] =
    new Subscriber.Sync[A] {
      implicit val scheduler = out.scheduler

      private[this] var ack = Continue : Future[Ack]
      private[this] var eventsDropped = 0L
      private[this] var isDone = false

      def onNext(elem: A) =
        if (isDone) Stop else
          ack.syncTryFlatten match {
            case Continue =>
              // Protects calls to user code from within the operator and
              // stream the error downstream if it happens, but if the
              // error happens because of calls to `onNext` or other
              // protocol calls, then the behavior should be undefined.
              var streamError = true
              val hasOverflow = eventsDropped > 0
              try {
                if (hasOverflow) {
                  val message = onOverflow(eventsDropped)
                  eventsDropped = 0
                  streamError = false
                  ack = out.onNext(message)
                  onNext(elem) // retry, recursive call, take care!
                } else {
                  streamError = false
                  ack = out.onNext(elem)
                  if (ack eq Stop) Stop else Continue
                }
              } catch {
                case NonFatal(ex) if streamError =>
                  onError(ex)
                  Stop
              }

            case Stop => Stop
            case async =>
              eventsDropped += 1
              Continue
          }

      def onError(ex: Throwable) =
        if (!isDone) {
          isDone = true
          out.onError(ex)
        }

      def onComplete() =
        if (!isDone) {
          isDone = true
          val hasOverflow = eventsDropped > 0

          if (!hasOverflow)
            out.onComplete()
          else ack.syncOnContinue {
            // Protects calls to user code from within the operator and
            // stream the error downstream if it happens, but if the
            // error happens because of calls to `onNext` or other
            // protocol calls, then the behavior should be undefined.
            var streamError = true
            try {
              val message = onOverflow(eventsDropped)
              streamError = false
              out.onNext(message)
              out.onComplete()
            } catch {
              case NonFatal(ex) if streamError =>
                out.onError(ex)
            }
          }
        }
    }
}