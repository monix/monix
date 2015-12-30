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

package monix.internal.operators

import monix.OverflowStrategy.Unbounded
import monix.Observable
import monix.observers.BufferedSubscriber
import scala.concurrent.duration.Duration.Zero

object DoWorkSuite extends BaseOperatorSuite {
  def createObservable(sourceCount: Int) = Some {
    val o = Observable.unsafeCreate[Long] { s =>
      import s.scheduler
      val buffer = BufferedSubscriber[Long](s, Unbounded)

      Observable.range(0, sourceCount)
        .doWork(x => buffer.onNext(x))
        .doOnComplete(buffer.onComplete())
        .subscribe()
    }

    Sample(o, count(sourceCount), sum(sourceCount), Zero, Zero)
  }

  def count(sourceCount: Int) = sourceCount

  def observableInError(sourceCount: Int, ex: Throwable) = Some {
    val o = Observable.unsafeCreate[Long] { s =>
      import s.scheduler

      val buffer = BufferedSubscriber[Long](s, Unbounded)
      createObservableEndingInError(Observable.range(0, sourceCount), ex)
        .doWork(x => buffer.onNext(x))
        .doOnError(buffer.onError)
        .subscribe()
    }

    Sample(o, count(sourceCount), sum(sourceCount), Zero, Zero)
  }

  def sum(sourceCount: Int) = {
    (0 until sourceCount).sum
  }

  def brokenUserCodeObservable(sourceCount: Int, ex: Throwable) = Some {
    val o = Observable.unsafeCreate[Long] { s =>
      import s.scheduler

      val buffer = BufferedSubscriber[Long](s, Unbounded)
      Observable.range(0, sourceCount)
        .doWork(x => if (x == sourceCount - 1) throw ex else buffer.onNext(x))
        .doOnError(buffer.onError)
        .subscribe()
    }

    Sample(o, count(sourceCount-1), sum(sourceCount-1), Zero, Zero)
  }
}
