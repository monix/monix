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
import monix.execution.Ack.Continue
import monix.streams.Observable
import monix.streams.ObservableLike.Operator
import monix.streams.observers.Subscriber
import scala.concurrent.Future
import scala.language.higherKinds
import scala.util.control.NonFatal

private[streams] final
class OnErrorRecoverWithOperator[A](pf: PartialFunction[Throwable, Observable[A]])
  extends Operator[A, A] {

  def apply(out: Subscriber[A]): Subscriber[A] =
    new Subscriber[A] {
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
          if (pf.isDefinedAt(ex)) {
            val fallbackTo = pf(ex)
            // We need asynchronous execution to avoid a synchronous loop
            // blowing out the call stack. We also need to apply back-pressure
            // on the last ack, otherwise we break back-pressure.
            ack.onComplete { r =>
              if (r.isSuccess && (r.get eq Continue))
                fallbackTo.unsafeSubscribeFn(out)
            }
          } else {
            // we can't protect the onError call and if it throws
            // the behavior should be undefined
            streamError = false
            out.onError(ex)
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
    }
}