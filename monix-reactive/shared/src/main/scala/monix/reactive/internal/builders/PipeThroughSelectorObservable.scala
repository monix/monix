/*
 * Copyright (c) 2014-2017 by its authors. Some rights reserved.
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

import monix.execution.{Ack, Cancelable}
import monix.execution.cancelables.SingleAssignmentCancelable
import monix.execution.misc.NonFatal
import monix.reactive.observers.Subscriber
import monix.reactive.{Observable, Pipe}

import scala.concurrent.Future

private[reactive] final class PipeThroughSelectorObservable[A,B,C]
  (source: Observable[A], pipe: Pipe[A,B], f: Observable[B] => Observable[C])
  extends Observable[C] {

  def unsafeSubscribeFn(out: Subscriber[C]): Cancelable = {
    import out.scheduler
    var streamErrors = true
    val upstream = SingleAssignmentCancelable()

    try {
      val connectable = source.multicast(pipe)
      val observable = f(connectable)
      streamErrors = false

      val downstream = observable.unsafeSubscribeFn(
        new Subscriber[C] {
          implicit val scheduler = out.scheduler
          def onError(ex: Throwable) = out.onError(ex)
          def onComplete() = out.onComplete()

          def onNext(elem: C): Future[Ack] = {
            // Treating STOP event
            out.onNext(elem).syncOnStopOrFailure(_ => upstream.cancel())
          }
        })

      upstream := connectable.connect()
      Cancelable { () =>
        upstream.cancel()
        downstream.cancel()
      }
    } catch {
      case NonFatal(ex) =>
        upstream.cancel()
        if (streamErrors) out.onError(ex)
        else out.scheduler.reportFailure(ex)
        Cancelable.empty
    }
  }
}