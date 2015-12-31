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

package monix.tckTests

import monix.concurrent.Implicits.globalScheduler
import monix.Observable
import org.reactivestreams.Publisher
import org.reactivestreams.tck.{PublisherVerification, TestEnvironment}
import org.scalatest.testng.TestNGSuiteLike
import scala.concurrent.Future

class PublisherTest
  extends PublisherVerification[Long](new TestEnvironment(1000))
  with TestNGSuiteLike {

  def createPublisher(elements: Long): Publisher[Long] = {
    if (elements == Long.MaxValue)
      Observable.repeat(1L)
        .flatMap(x => Future(x))
        .toReactive
    else
      Observable.range(0, elements)
        .flatMap(x => Future(x))
        .toReactive
  }

  def createFailedPublisher(): Publisher[Long] = {
    Observable.error(new RuntimeException("dummy"))
      .asInstanceOf[Observable[Long]]
      .toReactive
  }
}