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
import monix.reactive.Observable
import org.reactivestreams.Publisher
import org.reactivestreams.tck.PublisherVerification
import org.scalatest.testng.TestNGSuiteLike
import scala.util.Random

class ObservableToPublisherTest extends PublisherVerification[Long](env())
  with TestNGSuiteLike {

  def eval[A](x: A): Observable[A] = {
    val n = Random.nextInt()
    if (math.abs(n % 10) == 0)
      Observable.now(x).executeAsync
    else
      Observable.now(x)
  }

  def createPublisher(elements: Long): Publisher[Long] = {
    if (elements == Long.MaxValue)
      Observable.repeat(1L).flatMap(eval)
        .toReactivePublisher
    else
      Observable.range(0, elements).flatMap(eval)
        .toReactivePublisher
  }

  def createFailedPublisher(): Publisher[Long] = {
    Observable.raiseError(new RuntimeException("dummy"))
      .asInstanceOf[Observable[Long]]
      .toReactivePublisher
  }
}