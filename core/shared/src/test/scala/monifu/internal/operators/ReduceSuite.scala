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

package monifu.internal.operators

import monifu.Ack.Continue
import monifu.Observable
import scala.concurrent.duration.Duration.Zero
import scala.util.Success

object ReduceSuite extends BaseOperatorSuite {
  def createObservable(sourceCount: Int) = Some {
    if (sourceCount > 1) {
      val o = Observable.range(1, sourceCount+1).reduce(_ + _)
      Sample(o, 1, sum(sourceCount), Zero, Zero)
    }
    else {
      val o = Observable.range(1, 3).reduce(_ + _)
      Sample(o, 1, 3, Zero, Zero)
    }
  }

  def sum(sourceCount: Int) =
    sourceCount * (sourceCount + 1) / 2

  def observableInError(sourceCount: Int, ex: Throwable) = Some {
    val o = Observable.create[Long] { subscriber =>
      implicit val s = subscriber.scheduler
      val source = createObservableEndingInError(Observable.range(0, sourceCount), ex)
        .reduce(_ + _)

      subscriber.onNext(sum(sourceCount)).onComplete {
        case Success(Continue) =>
          source.subscribe(subscriber)
        case _ =>
          ()
      }
    }

    Sample(o, 1, sum(sourceCount), Zero, Zero)
  }

  def brokenUserCodeObservable(sourceCount: Int, ex: Throwable) = Some {
    val o = Observable.range(1, sourceCount+1).reduce { (acc, elem) =>
      if (elem == sourceCount)
        throw ex
      else
        acc + elem
    }

    Sample(o, 0, 0, Zero, Zero)
  }
}
