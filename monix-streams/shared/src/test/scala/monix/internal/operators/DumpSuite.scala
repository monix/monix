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

package monix.internal.operators

import java.io.{OutputStream, PrintStream}
import org.sincron.atomic.{AtomicInt, Atomic}
import monix.exceptions.DummyException
import monix.Observable
import scala.concurrent.duration.Duration.Zero


object DumpSuite extends BaseOperatorSuite {
  def dummyOut(count: AtomicInt = null) = {
    val out = new OutputStream { def write(b: Int) = () }
    new PrintStream(out) {
      override def println(x: String) = {
        super.println(x)
        if (count != null) {
          val c = count.decrementAndGet()
          if (c == 0) throw new DummyException("dummy")
        }
      }
    }
  }

  def createObservable(sourceCount: Int) = Some {
    val o = Observable.range(0, sourceCount)
      .dump("o", dummyOut())

    Sample(o, count(sourceCount), sum(sourceCount), Zero, Zero)
  }

  def observableInError(sourceCount: Int, ex: Throwable) = Some {
    val o = createObservableEndingInError(Observable.range(0, sourceCount), ex)
      .dump("o", dummyOut())

    Sample(o, count(sourceCount), sum(sourceCount), Zero, Zero)
  }

  def brokenUserCodeObservable(sourceCount: Int, ex: Throwable) = Some {
    val o = Observable.range(0, sourceCount * 2)
      .dump("o", dummyOut(Atomic(sourceCount)))

    Sample(o, count(sourceCount-1), sum(sourceCount-1), Zero, Zero)
  }

  def count(sourceCount: Int) = sourceCount
  def sum(sourceCount: Int) =
    sourceCount * (sourceCount - 1) / 2
}
