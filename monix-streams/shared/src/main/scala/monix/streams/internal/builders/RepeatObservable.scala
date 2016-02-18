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

package monix.streams.internal.builders

import monix.streams.Observable
import monix.streams.observers.Subscriber

/** Repeats the given elements */
private[streams] final class RepeatObservable[T](elems: T*) extends Observable[T] {
  private[this] val source =
    if (elems.isEmpty) Observable.empty
    else if (elems.length == 1)
      new RepeatOneObservable(elems.head)
    else
      Observable.from(elems).repeat

  def unsafeSubscribeFn(subscriber: Subscriber[T]): Unit =
    source.unsafeSubscribeFn(subscriber)
}
