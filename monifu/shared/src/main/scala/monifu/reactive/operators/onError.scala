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
import scala.concurrent.Future

object onError {
  /**
   * Implementation for [[Observable.onErrorResumeNext]]
   */
  def onErrorResumeNext[T](source: Observable[T], resumeSequence: Observable[T]): Observable[T] = {
    Observable.create { subscriber =>
      implicit val s = subscriber.scheduler
      val observer = subscriber.observer

      source.unsafeSubscribe(new Observer[T] {
        override def onNext(elem: T): Future[Ack] = observer.onNext(elem)

        override def onError(ex: Throwable): Unit = resumeSequence.unsafeSubscribe(observer)

        override def onComplete(): Unit = observer.onComplete()
      })
    }
  }
}
