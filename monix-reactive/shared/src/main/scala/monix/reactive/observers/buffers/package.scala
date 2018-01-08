/*
 * Copyright (c) 2014-2018 by The Monix Project Developers.
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

package monix.reactive.observers

import scala.collection.mutable.ListBuffer

package object buffers {
  // Internal reusable utility
  private[buffers] def subscriberBufferToList[A](out: Subscriber[List[A]]): Subscriber[ListBuffer[A]] =
    new Subscriber[ListBuffer[A]] {
      val scheduler = out.scheduler
      def onNext(elem: ListBuffer[A]) = out.onNext(elem.toList)
      def onError(ex: Throwable) = out.onError(ex)
      def onComplete() = out.onComplete()
    }
}
