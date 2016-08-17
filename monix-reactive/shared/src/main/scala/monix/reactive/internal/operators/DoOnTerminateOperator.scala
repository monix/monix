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

import monix.execution.{Ack, Cancelable}
import monix.reactive.observables.ObservableLike.Operator
import monix.reactive.observers.Subscriber
import scala.concurrent.Future
import scala.util.control.NonFatal

private[reactive] final
class DoOnTerminateOperator[A](onTerminate: () => Unit)
  extends Operator[A,A] {

  def apply(out: Subscriber[A]): Subscriber[A] =
    new Subscriber[A] {
      // Wrapping in a cancelable in order to protect it from
      // being called multiple times
      private[this] val active = Cancelable(onTerminate)
      implicit val scheduler = out.scheduler

      def onNext(elem: A): Future[Ack] = {
        out.onNext(elem).syncOnStopOrFailure {
          try active.cancel() catch {
            case NonFatal(ex) =>
              scheduler.reportFailure(ex)
          }
        }
      }

      def onComplete(): Unit = {
        var streamErrors = true
        try {
          active.cancel()
          streamErrors = false
          out.onComplete()
        } catch {
          case NonFatal(ex) =>
            if (streamErrors) out.onError(ex)
            else scheduler.reportFailure(ex)
        }
      }

      def onError(ex: Throwable): Unit = {
        // In case our callback throws an error the behavior
        // is undefined, so we just log it.
        try active.cancel() catch {
          case NonFatal(err) =>
            scheduler.reportFailure(err)
        } finally {
          out.onError(ex)
        }
      }
    }
}