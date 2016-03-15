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
import monix.streams.exceptions.DummyException
import scala.concurrent.duration._

object CombineLatest6Suite extends BaseOperatorSuite {
  def waitFirst = Duration.Zero
  def waitNext = Duration.Zero

  def createObservable(sc: Int) = Some {
    val sourceCount = 10
    val o1 = Observable.now(1)
    val o2 = Observable.now(2)
    val o3 = Observable.now(3)
    val o4 = Observable.now(4)
    val o5 = Observable.now(5)
    val o6 = Observable.range(0, sourceCount)
    val o = Observable.combineLatestWith6(o1,o2,o3,o4,o5,o6)(_+_+_+_+_+_)

    Sample(o, count(sourceCount), sum(sourceCount), waitFirst, waitNext)
  }

  def count(sourceCount: Int) = sourceCount
  def sum(sourceCount: Int) = sourceCount * (sourceCount + 1) / 2 +
    (14 * sourceCount)

  def observableInError(sourceCount: Int, ex: Throwable) = Some {
    val o1 = Observable.now(1)
    val o2 = Observable.now(2)
    val o3 = Observable.now(3)
    val o4 = Observable.now(4)
    val o5 = Observable.now(5)
    val flawed = createObservableEndingInError(Observable.range(0, sourceCount), ex)
    val o = Observable.combineLatestWith6(o1,o2,o3,o4,o5,flawed)(_+_+_+_+_+_)

    Sample(o, count(sourceCount-1), sum(sourceCount-1), waitFirst, waitNext)
  }

  def brokenUserCodeObservable(sourceCount: Int, ex: Throwable) = Some {
    val dummy = DummyException("dummy")
    val o1 = Observable.now(1)
    val o2 = Observable.now(2)
    val o3 = Observable.now(3)
    val o4 = Observable.now(4)
    val o5 = Observable.now(5)
    val o6 = Observable.range(0, sourceCount)

    val o = Observable.combineLatestWith6(o1,o2,o3,o4,o5,o6) { (a1,a2,a3,a4,a5,a6) =>
      if (a6 == sourceCount-1) throw dummy else a1+a2+a3+a4+a5+a6
    }

    Sample(o, count(sourceCount-1), sum(sourceCount-1), waitFirst, waitNext)
  }

  override def cancelableObservables(): Seq[Sample] = {
    val sample1 = {
      val o1 = Observable.range(0, 10).delayOnNext(1.second)
      val o2 = Observable.range(0, 10).delayOnNext(1.second)
      val o3 = Observable.range(0, 10).delayOnNext(1.second)
      val o4 = Observable.range(0, 10).delayOnNext(1.second)
      val o5 = Observable.range(0, 10).delayOnNext(1.second)
      val o6 = Observable.range(0, 10).delayOnNext(1.second)
      Observable.combineLatestWith6(o1,o2,o3,o4,o5,o6)(_+_+_+_+_+_)
    }

    Seq(Sample(sample1, 0, 0, 0.seconds, 0.seconds))
  }
}