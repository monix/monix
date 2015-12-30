/*
 * Copyright (c) 2014-2015 by its authors. Some rights reserved.
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

import monix.Subscriber
import monix.internal._

private[monix] final class NextThenCompleteRunnable[T] private
  (subscriber: Subscriber[T], elem: T) extends Runnable {

  def run(): Unit = {
    subscriber.onNext(elem)
      .onContinueSignalComplete(subscriber)(subscriber.scheduler)
  }
}

private[monix] object NextThenCompleteRunnable {
  def apply[T](subscriber: Subscriber[T], elem: T): Runnable =
    new NextThenCompleteRunnable[T](subscriber, elem)
}
