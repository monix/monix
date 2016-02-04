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

package monix.streams.internal.operators

import monix.streams.{Observer, Observable, Ack}
import monix.streams.Ack.Continue
import monix.streams.exceptions.CompositeException
import monix.streams.Observer
import scala.concurrent.duration._
import scala.util.Random

object ConcatDelayErrorManySuite extends BaseOperatorSuite {
  case class SomeException(value: Long) extends RuntimeException

  def create(sourceCount: Int, ex: Throwable = null) = Some {
    val source = if (ex == null) Observable.range(0, sourceCount)
    else Observable.range(0, sourceCount).endWithError(ex)

    val o = source.flatMapDelayError(i =>
      Observable.from(i, i, i, i).endWithError(SomeException(10)))

    val recovered = o.onErrorRecoverWith {
      case composite: CompositeException =>
        val sum = composite
          .errors.collect { case ex: SomeException => ex.value }
          .sum

        Observable.now(sum)
    }

    Sample(recovered, count(sourceCount), sum(sourceCount), waitFirst, waitNext)
  }

  def createObservable(sourceCount: Int) = create(sourceCount)
  def observableInError(sourceCount: Int, ex: Throwable) = None
  def brokenUserCodeObservable(sourceCount: Int, ex: Throwable) = None

  def count(sourceCount: Int) = sourceCount * 4 + 1
  def sum(sourceCount: Int) =
    sourceCount * (sourceCount - 1) / 2 * 4 + sourceCount * 10

  def waitFirst = Duration.Zero
  def waitNext = Duration.Zero

  test("error emitted by the source should also be delayed") { implicit s =>
    val sourceCount = Random.nextInt(300) + 100
    var received = 0
    var receivedSum = 0L
    var wasCompleted = false

    val Some(Sample(obs, count, sum, _, _)) =
      create(sourceCount, SomeException(100))

    obs.unsafeSubscribeFn(new Observer[Long] {
      def onNext(elem: Long) = {
        received += 1
        receivedSum += elem
        Continue
      }

      def onError(ex: Throwable): Unit = ()
      def onComplete(): Unit = wasCompleted = true
    })

    s.tick()
    assertEquals(received, count)
    assertEquals(receivedSum, sum + 100)
    s.tick()
    assert(wasCompleted)
  }
}