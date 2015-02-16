/*
 * Copyright (c) 2015 Alexandru Nedelcu
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package monifu.reactive.operators

import monifu.reactive.{Ack, Observer, Observable}
import monifu.reactive.internals._
import scala.concurrent.Future


object debug {
  /**
   * Implementation for [[Observable.dump]].
   */
  def dump[T](source: Observable[T], prefix: String): Observable[T] =
    Observable.create[T] { subscriber =>
      implicit val s = subscriber.scheduler
      val observer = subscriber.observer

      source.unsafeSubscribe(new Observer[T] {
        private[this] var pos = 0

        def onNext(elem: T): Future[Ack] = {
          System.out.println(s"$pos: $prefix-->$elem")
          pos += 1
          val f = observer.onNext(elem)
          f.onCancel { pos += 1; System.out.println(s"$pos: $prefix canceled") }
          f
        }

        def onError(ex: Throwable) = {
          System.out.println(s"$pos: $prefix-->$ex")
          pos += 1
          observer.onError(ex)
        }

        def onComplete() = {
          System.out.println(s"$pos: $prefix completed")
          pos += 1
          observer.onComplete()
        }
      })
    }
}
