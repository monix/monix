/*
 * Copyright (c) 2014-2015 by its authors. Some rights reserved.
 * See the project homepage at: http://www.monix.io
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

package monix.channels

import monix.observables.LiftOperators2
import monix.{Subscriber, Channel, Observable}

trait ObservableChannel[I,+O]
  extends Observable[O] with Channel[I]
  with LiftOperators2[I, O, ObservableChannel] { self =>

  protected
  def liftToSelf[U](f: (Observable[O]) => Observable[U]): ObservableChannel[I, U] =
    new ObservableChannel[I, U] {
      def pushComplete(): Unit = self.pushComplete()
      def pushNext(elem: I*): Unit = self.pushNext(elem:_*)
      def pushError(ex: Throwable): Unit = self.pushError(ex)

      private[this] val lifted = f(self)
      def unsafeSubscribeFn(subscriber: Subscriber[U]): Unit =
        lifted.unsafeSubscribeFn(subscriber)
    }
}
