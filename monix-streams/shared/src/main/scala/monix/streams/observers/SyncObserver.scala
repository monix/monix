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

package monix.streams.observers

import monix.streams.{Ack, Observer}

/** A `SyncObserver` is an [[Observer]] that signals demand
  * to upstream synchronously (i.e. the upstream observable doesn't need to
  * wait on a `Future` in order to decide whether to send the next event
  * or not).
  *
  * Can be used for optimizations.
  */
trait SyncObserver[-T] extends Observer[T] {
  /**
    * Returns either a [[monix.streams.Ack.Continue Continue]] or a
    * [[monix.streams.Ack.Cancel Cancel]], in response to an `elem` event
    * being received.
    */
  def onNext(elem: T): Ack
}

