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

package monix.streams.internal.operators2

import monix.execution.Ack
import monix.execution.Ack.{Cancel, Continue}
import monix.execution.cancelables.{BooleanCancelable, SerialCancelable}
import monix.streams.ObservableLike.Operator
import monix.streams.observers.Subscriber
import monix.streams.{Observable, Observer}
import scala.concurrent.Future
import scala.util.control.NonFatal

private[streams] final class SwitchMapOperator[A,B](f: A => Observable[B])
  extends Operator[A,B] {

  def apply(out: Subscriber[B]): Subscriber[A] =
    new Subscriber[A] { self =>
      implicit val scheduler = out.scheduler
      // Global subscription, is canceled by the downstream
      // observer and if canceled all streaming is supposed to stop
      private[this] val upstream = SerialCancelable()
      // MUST BE synchronized by `self`
      private[this] var ack: Future[Ack] = Continue
      // MUST BE synchronized by `self`

      def onNext(elem: A) = self.synchronized {
        if (upstream.isCanceled) Cancel else {
          // canceling current observable in order to
          // start the new stream
          val activeRef = BooleanCancelable()
          upstream := activeRef

          val lastAck = ack
          ack = Continue

          lastAck.syncFlatMap {
            case Continue =>
              // Protects calls to user code from within the operator.
              val childObservable =
                try f(elem) catch {
                  case NonFatal(ex) =>
                    Observable.error(ex)
                }

              childObservable.unsafeSubscribeFn(new Observer[B] {
                def onNext(elem: B) = self.synchronized {
                  if (activeRef.isCanceled) Cancel else {
                    ack = ack.syncFlatMap {
                      case Continue => out.onNext(elem)
                      case Cancel =>
                        upstream.cancel()
                        Cancel
                    }
                    ack
                  }
                }

                def onComplete(): Unit = ()
                def onError(ex: Throwable): Unit =
                  self.onError(ex)
              })

              Continue

            case Cancel =>
              Cancel
          }
        }
      }

      def onError(ex: Throwable): Unit = self.synchronized {
        if (!upstream.isCanceled) {
          upstream.cancel()
          out.onError(ex)
        }
      }

      def onComplete(): Unit = self.synchronized {
        if (!upstream.isCanceled) {
          upstream.cancel()
          out.onComplete()
        }
      }
    }
}