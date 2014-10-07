/*
 * Copyright (c) 2014 by its authors. Some rights reserved.
 * See the project homepage at
 *
 *     http://www.monifu.org/
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  	http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package monifu.reactive.builders

import monifu.concurrent.Scheduler
import monifu.reactive.Ack.Continue
import monifu.reactive.Observable

import scala.annotation.tailrec
import scala.util.control.NonFatal

object from {
  /**
   * Creates an Observable that emits the elements of the given ''iterable''.
   *
   * <img src="https://raw.githubusercontent.com/wiki/monifu/monifu/assets/rx-operators/fromIterable.png" />
   */
  def iterable[T](iterable: Iterable[T])(implicit s: Scheduler): Observable[T] =
    Observable.create { observer =>
      def startFeedLoop(iterator: Iterator[T]): Unit =
        s.execute(new Runnable {
          @tailrec
          def fastLoop(): Unit = {
            val ack = observer.onNext(iterator.next())
            if (iterator.hasNext)
              ack match {
                case sync if sync.isCompleted =>
                  if (sync == Continue || sync.value.get == Continue.IsSuccess)
                    fastLoop()
                case async =>
                  async.onSuccess {
                    case Continue =>
                      startFeedLoop(iterator)
                  }
              }
            else
              observer.onComplete()
          }

          def run(): Unit = {
            try fastLoop() catch {
              case NonFatal(ex) =>
                observer.onError(ex)
            }
          }
        })

      val iterator = iterable.iterator
      if (iterator.hasNext)
        startFeedLoop(iterator)
      else
        observer.onComplete()
    }
}
