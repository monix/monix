/*
 * Copyright (c) 2014-2020 by The Monix Project Developers.
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

package monix.reactive.observables

import minitest.TestSuite
import monix.execution.schedulers.TestScheduler
import monix.reactive.Observable.Transformation
import monix.reactive.internal.transformer.MapTransformer
import monix.reactive.{Observable, Transformer}

import scala.concurrent.duration._
import scala.concurrent.Await


object TransformerSuite extends TestSuite[TestScheduler] {
  def setup(): TestScheduler = TestScheduler()
  def tearDown(s: TestScheduler): Unit = {
    assert(s.state.tasks.isEmpty, "TestScheduler should have no pending tasks")
  }

  test("transformer ") { implicit s =>
    val transformer: Transformation[Int, String] = Transformer.map[String](i => i.toString)

    s.tick()

    val f = Observable.now(1).transform(transformer).headL.runToFuture
    val r = Await.result(f, 1.seconds)

    assertEquals(r, "1")
  }



  test("transformer chain") { implicit s =>
    val transformer: Observable[Int] => Observable[String] = Transformer
      .map[String](i => i.toString)
      .map[String](s => s + s)
      .chainPrevious

    s.tick()

    val f = Observable.now(1).transform(transformer).headL.runToFuture
    val r = Await.result(f, 1.seconds)

    assertEquals(r, "11")
  }



}
