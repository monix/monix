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

import monix.streams.exceptions.CompositeException
import monix.streams.Observable
import scala.concurrent.duration._

object CombineLatestDelayErrorSuite extends BaseOperatorSuite {
  case class SomeException(value: Long) extends RuntimeException

  def createObservable(sourceCount: Int) = Some {
    def recovered(o: Observable[Long]) =
      o.onErrorRecoverWith {
        case CompositeException(errors) =>
          val sum = errors.collect { case ex: SomeException => ex.value }.sum
          Observable.now(sum)
      }

    val o1 = Observable.now(1).endWithError(SomeException(100))
    val o2 = Observable.range(0, sourceCount).endWithError(SomeException(200))
    val o3 = Observable.now(2).endWithError(SomeException(300))

    val source1 = recovered {
      o1.combineLatestDelayError(o2)
        .map { case (x1, x2) => x1 + x2 }
    }

    val source2 = recovered {
      source1.combineLatestDelayError(o3)
        .map { case (x2, x3) => x2 + x3 }
    }

    Sample(source2, count(sourceCount), sum(sourceCount), Duration.Zero, Duration.Zero)
  }

  def count(sourceCount: Int) = sourceCount + 2
  def sum(sourceCount: Int) = {
    (3 to (sourceCount + 2)).sum + 602
  }

  def observableInError(sourceCount: Int, ex: Throwable) = None
  def brokenUserCodeObservable(sourceCount: Int, ex: Throwable) = None
}