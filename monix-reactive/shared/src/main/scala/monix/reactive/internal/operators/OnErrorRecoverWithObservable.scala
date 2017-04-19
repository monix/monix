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

import monix.execution.Ack.Continue
import monix.execution.cancelables.MultiAssignmentCancelable
import monix.execution.misc.NonFatal
import monix.execution.{Ack, Cancelable}
import monix.reactive.Observable
import monix.reactive.observers.Subscriber

import scala.concurrent.Future

private[reactive] final
class OnErrorRecoverWithObservable[A](source: Observable[A], f: Throwable => Observable[A])
  extends Observable[A] {

  def unsafeSubscribeFn(out: Subscriber[A]): Cancelable = {
    val cancelable = MultiAssignmentCancelable()

    val main = source.unsafeSubscribeFn(new Subscriber[A] {
      implicit val scheduler = out.scheduler
      private[this] var ack: Future[Ack] = Continue

      def onNext(elem: A) = {
        ack = out.onNext(elem)
        ack
      }

      def onComplete(): Unit = out.onComplete()

      def onError(ex: Throwable) = {
        // protecting user level code
        var streamError = true
        try {
          val fallbackTo = f(ex)
          streamError = false
          // We need asynchronous execution to avoid a synchronous loop
          // blowing out the call stack. We also need to apply back-pressure
          // on the last ack, otherwise we break back-pressure.
          ack.onComplete { r =>
            if (r.isSuccess && (r.get eq Continue))
              cancelable.orderedUpdate(fallbackTo.unsafeSubscribeFn(out), order=2)
          }
        }
        catch {
          case NonFatal(err) if streamError =>
            // streaming the immediate exception
            try out.onError(err) finally {
              // logging the original exception
              scheduler.reportFailure(ex)
            }
        }
      }
    })

    cancelable.orderedUpdate(main, order=1)
  }
}