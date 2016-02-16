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

package monix.internal.concurrent

import monix.{Observable, Subscriber}

/** Ready-made Runnable class that triggers a
  * subscription on the given observable.
  */
private[monix] final class UnsafeSubscribeRunnable[T] private
  (obs: Observable[T], subscriber: Subscriber[T])
  extends Runnable {

  def run(): Unit = {
    obs.unsafeSubscribeFn(subscriber)
  }
}

private[monix] object UnsafeSubscribeRunnable {
  /** Builder for [[UnsafeSubscribeRunnable]] */
  def apply[T](obs: Observable[T], subscriber: Subscriber[T]): Runnable =
    new UnsafeSubscribeRunnable[T](obs, subscriber)
}
