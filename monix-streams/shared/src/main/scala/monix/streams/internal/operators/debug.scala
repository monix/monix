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

import java.io.PrintStream
import monix.execution.Cancelable
import monix.streams.Ack.Cancel
import monix.streams.internal._
import monix.streams.{Ack, Observable, Observer}
import scala.concurrent.Future
import scala.util.control.NonFatal


private[monix] object debug {
  /**
    * Implementation for [[Observable.dump]].
    */
  def dump[T](source: Observable[T], prefix: String, out: PrintStream): Observable[T] =
    Observable.unsafeCreate[T] { subscriber =>
      import subscriber.{scheduler => s}

      source.unsafeSubscribeFn(new Observer[T] {
        private[this] var pos = 0
        private[this] val downstreamActive = Cancelable {
          pos += 1; out.println(s"$pos: $prefix canceled")
        }

        def onNext(elem: T): Future[Ack] = {
          // Protects calls to user code from within the operator
          var streamError = true
          try {
            out.println(s"$pos: $prefix-->$elem")
            streamError = false

            pos += 1
            subscriber.onNext(elem)
              .ifCanceledDoCancel(downstreamActive)
          }
          catch {
            case NonFatal(ex) =>
              if (streamError) { subscriber.onError(ex); Cancel } else
                Future.failed(ex)
          }
        }

        def onError(ex: Throwable) = {
          try {
            out.println(s"$pos: $prefix-->$ex")
            pos += 1
          }
          catch {
            case NonFatal(_) =>
              () // ignore
          }

          subscriber.onError(ex)
        }

        def onComplete() = {
          try {
            out.println(s"$pos: $prefix completed")
            pos += 1
          }
          catch {
            case NonFatal(_) =>
              () // ignore
          }

          subscriber.onComplete()
        }
      })
    }
}
