/*
 * Copyright (c) 2014-2018 by The Monix Project Developers.
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

import monix.execution.cancelables.SingleAssignmentCancelable
import monix.execution.{Ack, Cancelable}
import monix.reactive.observers.Subscriber
import monix.reactive.{Observable, Pipe}
import scala.concurrent.Future

private[reactive] final class PipeThroughObservable[A,B]
  (source: Observable[A], pipe: Pipe[A,B])
  extends Observable[B] {

  def unsafeSubscribeFn(out: Subscriber[B]): Cancelable = {
    import out.scheduler
    val (input,output) = pipe.unicast
    val upstream = SingleAssignmentCancelable()

    val downstream = output.unsafeSubscribeFn(new Subscriber[B] {
      implicit val scheduler = out.scheduler
      def onError(ex: Throwable) = out.onError(ex)
      def onComplete() = out.onComplete()

      def onNext(elem: B): Future[Ack] =
        out.onNext(elem).syncOnStopOrFailure(_ => upstream.cancel())
    })

    upstream := source.unsafeSubscribeFn(input)
    Cancelable { () =>
      upstream.cancel()
      downstream.cancel()
    }
  }
}
