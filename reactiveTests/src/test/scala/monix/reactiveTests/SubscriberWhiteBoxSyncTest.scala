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

package monix.reactiveTests

import monix.execution.Scheduler.Implicits.global
import monix.reactive.Observer
import monix.execution.Ack.Continue
import monix.reactiveTests.SubscriberWhiteBoxSyncTest.Value
import org.reactivestreams.tck.SubscriberWhiteboxVerification.WhiteboxSubscriberProbe
import org.reactivestreams.tck.SubscriberWhiteboxVerification
import org.reactivestreams.{Subscriber, Subscription}
import org.scalatest.testng.TestNGSuiteLike

class SubscriberWhiteBoxSyncTest
  extends SubscriberWhiteboxVerification[Value](env())
  with TestNGSuiteLike {

  def createSubscriber(probe: WhiteboxSubscriberProbe[Value]): Subscriber[Value] = {
    val underlying = Observer.toReactiveSubscriber(new Observer.Sync[Value] {
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
