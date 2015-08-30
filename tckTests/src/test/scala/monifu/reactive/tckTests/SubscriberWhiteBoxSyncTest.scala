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

package monifu.reactive.tckTests

import monifu.concurrent.Implicits.globalScheduler
import monifu.reactive.Ack.Continue
import monifu.reactive.Observer
import monifu.reactive.observers.SynchronousObserver
import monifu.reactive.tckTests.SubscriberWhiteBoxSyncTest.Value
import org.reactivestreams.tck.SubscriberWhiteboxVerification.WhiteboxSubscriberProbe
import org.reactivestreams.tck.{SubscriberWhiteboxVerification, TestEnvironment}
import org.reactivestreams.{Subscriber, Subscription}
import org.scalatest.testng.TestNGSuiteLike

class SubscriberWhiteBoxSyncTest
  extends SubscriberWhiteboxVerification[Value](new TestEnvironment(1000))
  with TestNGSuiteLike {

  def createSubscriber(probe: WhiteboxSubscriberProbe[Value]): Subscriber[Value] = {
    val underlying = Observer.toSubscriber(new SynchronousObserver[Value] {
      def onNext(elem: Value) = {
        probe.registerOnNext(elem)
        Continue
      }

      def onError(ex: Throwable): Unit = {
        probe.registerOnError(ex)
      }

      def onComplete(): Unit = {
        probe.registerOnComplete()
      }
    })

    new Subscriber[Value] {
      def onError(t: Throwable): Unit =
        underlying.onError(t)

      def onSubscribe(s: Subscription): Unit = {
        underlying.onSubscribe(s)
        probe.registerOnSubscribe(new SubscriberWhiteboxVerification.SubscriberPuppet {
          def triggerRequest(elements: Long): Unit = s.request(elements)
          def signalCancel(): Unit = s.cancel()
        })
      }

      def onComplete(): Unit =
        underlying.onComplete()

      def onNext(t: Value): Unit =
        underlying.onNext(t)
    }
  }

  def createElement(element: Int): Value = {
    Value(element)
  }
}

object SubscriberWhiteBoxSyncTest {
  case class Value(nr: Int)
}
