/*
 * Copyright (c) 2014-2015 by its authors. Some rights reserved.
 * See the project homepage at: http://www.monifu.org
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
 
package monifu.reactive

import monifu.reactive.observables.LiftOperators2
import scala.concurrent.Future

/**
 * A `Subject` is a sort of bridge or proxy that acts both as an
 * [[Observer]] and as an [[Observable]] and that must respect the contract of both.
 *
 * Because it is a `Observer`, it can subscribe to an `Observable` and because it is an `Observable`,
 * it can pass through the items it observes by re-emitting them and it can also emit new items.
 *
 * Useful to build multicast Observables or reusable processing pipelines.
 */
trait Subject[I, +T] extends Observable[T] with Observer[I]
  with LiftOperators2[I, T, Subject] { self =>

  protected def liftToSelf[U](f: Observable[T] => Observable[U]): Subject[I, U] =
    new Subject[I,U] {
      def onNext(elem: I): Future[Ack] = self.onNext(elem)
      def onError(ex: Throwable): Unit = self.onError(ex)
      def onComplete(): Unit = self.onComplete()

      private[this] val lifted = f(self)
      def onSubscribe(subscriber: Subscriber[U]): Unit =
        lifted.onSubscribe(subscriber)
    }
}
