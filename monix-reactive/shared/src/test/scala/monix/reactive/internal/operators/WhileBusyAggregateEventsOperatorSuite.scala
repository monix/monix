/*
 * Copyright (c) 2014-2022 Monix Contributors.
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

package monix.reactive.internal.operators

import cats.data.Chain
import monix.reactive.Observable

import scala.concurrent.duration._
import scala.util.Success

class WhileBusyAggregateEventsOperatorSuite extends BaseOperatorSuite {
  private val waitNext = 1.second

  private def sum(sourceCount: Int) = {
    (0 until sourceCount).sum
  }

  override def createObservable(sourceCount: Int) = Some {
    require(sourceCount > 0, "sourceCount must be strictly positive")
    val o = Observable
      .range(0L, sourceCount.toLong)
      .throttle(waitNext, 1)
      .whileBusyAggregateEvents(identity)(_ + _)

    Sample(o, sourceCount, sum(sourceCount), waitNext, waitNext)
  }

  override def observableInError(sourceCount: Int, ex: Throwable) = Some {
    require(sourceCount > 0, "sourceCount must be strictly positive")

    val o = Observable
      .range(0, sourceCount.toLong)
      .throttle(waitNext, 1)
      .endWithError(ex)
      .whileBusyAggregateEvents(identity)(_ + _)

    Sample(o, sourceCount, sum(sourceCount), waitNext, waitNext)
  }

  override def cancelableObservables() = {
    val o = Observable
      .range(0, 1000)
      .throttle(waitNext, 1)
      .whileBusyAggregateEvents(identity)(_ + _)

    Seq(
      Sample(o, 0, 0, 0.seconds, 0.seconds),
      Sample(o, 4, 1 + 2 + 3 + 4, waitNext * 4, 0.seconds)
    )
  }

  override def brokenUserCodeObservable(sourceCount: Int, ex: Throwable) = Some {
    require(sourceCount > 0, "sourceCount should be strictly positive")

    val o = Observable
      .range(0, sourceCount.toLong)
      .whileBusyAggregateEvents { elem =>
        if (sourceCount == 1) throw ex else elem
      } {
        case (acc, elem) =>
          if (elem == sourceCount - 1) throw ex
          else elem + acc
      }
      .throttle(waitNext, 1)

    Sample(o, 0, 0, waitNext, waitNext)
  }

  fixture.test("performs no conflation when upstream is slow than downstream") { implicit s =>
    val result = Observable
      .range(0, 10)
      .throttle(100.milliseconds, 1)
      .whileBusyAggregateEvents[Chain[Long]](Chain.apply(_)) { case (list, ele) => list.append(ele) }
      .throttle(10.milliseconds, 1)
      .toListL
      .runToFuture

    s.tick(10.seconds)

    assertEquals(result.value, Some(Success((0L until 10L).toList.map(Chain.apply(_)))))
  }

  fixture.test("performs conflation when upstream is faster than downstream") { implicit s =>
    val result = Observable
      .range(0, 5)
      .throttle(10.milliseconds, 1)
      .whileBusyAggregateEvents[Chain[Long]](Chain.apply(_)) { case (list, ele) => list.append(ele) }
      .throttle(100.milliseconds, 1)
      .toListL
      .runToFuture

    s.tick(10.seconds)

    assertEquals(result.value, Some(Success(List(Chain(0L), Chain(1L, 2L, 3L, 4L)))))
  }

  fixture.test("emits groups of conflated elements each time backpressuring stops") { implicit s =>
    val result = Observable
      .range(0, 9)
      .throttle(3.seconds, 1)
      .whileBusyAggregateEvents[Chain[Long]](Chain.apply(_)) { case (list, ele) => list.append(ele) }
      .throttle(10.seconds, 1)
      .toListL
      .runToFuture

    s.tick(10.seconds) // 3,6,9 seconds - elements 0,1,2
    s.tick(10.seconds) // 12,15,18 seconds - elements 3,4,5
    s.tick(10.seconds) // 21,24,27 seconds - elements 6,7,8
    s.tick(10.seconds)

    assertEquals(result.value, Some(Success(List(Chain(0L), Chain(1L, 2), Chain(3L, 4, 5), Chain(6L, 7, 8)))))
  }

  fixture.test("performs conflation when upstream is unbounded and downstream is slow") { implicit s =>
    val result = Observable
      .range(0, 10)
      .whileBusyAggregateEvents[Chain[Long]](Chain.apply(_)) { case (list, ele) => list.append(ele) }
      .throttle(100.milliseconds, 1)
      .toListL
      .runToFuture

    s.tick(10.seconds)

    assertEquals(result.value, Some(Success(List(Chain(0L), Chain(1L, 2, 3, 4, 5, 6, 7, 8, 9)))))
  }

  fixture.test("performs no conflation when downstream is unbounded") { implicit s =>
    val result = Observable
      .range(0, 10)
      .throttle(10.milliseconds, 1)
      .whileBusyAggregateEvents[Chain[Long]](Chain.apply(_)) { case (list, ele) => list.append(ele) }
      .toListL
      .runToFuture

    s.tick(10.seconds)

    assertEquals(result.value, Some(Success((0L until 10L).toList.map(Chain.apply(_)))))
  }
}
