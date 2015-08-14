/*
 * Copyright (c) 2014-2015 Alexandru Nedelcu
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

package monifu.reactive.builders

import monifu.reactive.Ack.Continue
import monifu.reactive.Observable

object repeat {
  /**
   * Creates an Observable that continuously emits the given ''item'' repeatedly.
   */
  def apply[T](elems: T*): Observable[T] = {
    if (elems.size == 0)
      Observable.empty
    else if (elems.size == 1) {
      Observable.create { subscriber =>
        implicit val s = subscriber.scheduler
        val o = subscriber.observer

        def loop(elem: T): Unit =
          o.onNext(elem).onSuccess {
            case Continue =>
              loop(elem)
          }

        loop(elems.head)
      }
    }
    else
      Observable.fromIterable(elems).repeat
  }
}
