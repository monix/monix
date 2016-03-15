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

import monix.streams.Observable
import scala.concurrent.duration._
import scala.concurrent.duration.Duration._

object SumSuite extends BaseOperatorSuite {
  def createObservable(sourceCount: Int) = Some {
    val o = Observable.range(0, sourceCount).sumF
    Sample(o, count(sourceCount), sum(sourceCount), Zero, Zero)
  }

  def observableInError(sourceCount: Int, ex: Throwable) = {
    val o = Observable.range(0, sourceCount+1).endWithError(ex).sumF
    Some(Sample(o, 0, 0, Zero, Zero))
  }

  def count(sourceCount: Int) = 1
  def sum(sourceCount: Int) = (0 until sourceCount).sum

  def brokenUserCodeObservable(sourceCount: Int, ex: Throwable) = {
    val num = new Numeric[Long] {
      def plus(x: Long, y: Long): Long = throw ex
      def toDouble(x: Long): Double = throw ex
      def toFloat(x: Long): Float = throw ex
      def toInt(x: Long): Int = throw ex
      def negate(x: Long): Long = throw ex
      def fromInt(x: Int): Long = throw ex
      def toLong(x: Long): Long = throw ex
      def times(x: Long, y: Long): Long = throw ex
      def minus(x: Long, y: Long): Long = throw ex
      def compare(x: Long, y: Long): Int = throw ex
    }

    val o = Observable.range(0, sourceCount+1).sumF(num)
    Some(Sample(o, 0, 0, Zero, Zero))
  }

  def waitForNext = Duration.Zero
  def waitForFirst = Duration.Zero

  override def cancelableObservables() = {
    val o = Observable.now(1L).delayOnNext(1.second).sumF
    Seq(Sample(o,0,0,0.seconds,0.seconds))
  }
}
