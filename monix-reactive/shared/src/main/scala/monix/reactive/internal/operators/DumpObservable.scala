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

import java.io.PrintStream

import monix.execution.misc.NonFatal
import monix.execution.{Ack, Cancelable}
import monix.reactive.Observable
import monix.reactive.observers.Subscriber

import scala.concurrent.Future

private[reactive] final
class DumpObservable[T](source: Observable[T], prefix: String, out: PrintStream)
  extends Observable[T] {

  def unsafeSubscribeFn(subscriber: Subscriber[T]): Cancelable = {
    var pos = 0

    val upstream = source.unsafeSubscribeFn(new Subscriber[T] {
      implicit val scheduler = subscriber.scheduler

      private[this] val downstreamActive = Cancelable { () =>
        pos += 1
        out.println(s"$pos: $prefix stopped")
      }

      def onNext(elem: T): Future[Ack] = {
        try {
          out.println(s"$pos: $prefix-->$elem")
          pos += 1
        } catch {
          case NonFatal(_) => () // ignore
        }

        subscriber.onNext(elem).syncOnStopOrFailure(_ => downstreamActive.cancel())
      }

      def onError(ex: Throwable) = {
        try {
          out.println(s"$pos: $prefix-->$ex")
          pos += 1
        } catch {
          case NonFatal(_) => () // ignore
        }

        subscriber.onError(ex)
      }

      def onComplete() = {
        try {
          out.println(s"$pos: $prefix completed")
          pos += 1
        } catch {
          case NonFatal(_) =>
            () // ignore
        }

        subscriber.onComplete()
      }
    })

    Cancelable { () =>
      upstream.cancel()
      pos += 1
      out.println(s"$pos: $prefix canceled")
    }
  }
}
