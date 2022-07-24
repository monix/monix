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

package monix.reactive

import scala.util.Success

class PipeSuite extends BaseTestSuite {
  fixture.test("Pipe works for MulticastStrategy.publish") { implicit s =>
    val ref = Pipe(MulticastStrategy.publish[Int])
    val (in, out) = ref.multicast
    val f = out.sumL.runToFuture

    in.onNext(1)
    in.onNext(2)
    in.onNext(3)
    in.onComplete()
    s.tick()

    val g = out.sumL.runToFuture

    s.tick()
    assertEquals(f.value, Some(Success(6)))
    assertEquals(g.value, Some(Success(0)))
  }

  fixture.test("Pipe works for MulticastStrategy.behaviour") { implicit s =>
    val ref = Pipe(MulticastStrategy.behavior[Int](2))
    val (in, out) = ref.concurrent
    val f = out.sumL.runToFuture

    in.onNext(1)
    in.onNext(2)
    in.onNext(3)
    in.onComplete()

    s.tick()
    assertEquals(f.value, Some(Success(8)))
  }

  fixture.test("Pipe works for MulticastStrategy.async") { implicit s =>
    val ref = Pipe(MulticastStrategy.async[Int])
    val (in, out) = ref.concurrent
    val f = out.sumL.runToFuture

    in.onNext(1)
    in.onNext(2)
    in.onNext(3)
    in.onComplete()

    s.tick()
    assertEquals(f.value, Some(Success(3)))
  }

  fixture.test("Pipe works for MulticastStrategy.replay") { implicit s =>
    val ref1 = Pipe(MulticastStrategy.replay[Int])
    val (in1, out1) = ref1.multicast
    val f1 = out1.sumL.runToFuture

    in1.onNext(1)
    in1.onNext(2)
    in1.onNext(3)
    in1.onComplete()
    s.tick()

    val g1 = out1.sumL.runToFuture

    s.tick()
    assertEquals(f1.value, Some(Success(6)))
    assertEquals(g1.value, Some(Success(6)))

    val ref2 = Pipe(MulticastStrategy.replay[Int](Seq(3)))
    val (in2, out2) = ref2.multicast
    val f2 = out2.sumL.runToFuture

    in2.onNext(1)
    in2.onComplete()
    s.tick()

    val g2 = out2.sumL.runToFuture

    s.tick()
    assertEquals(f2.value, Some(Success(4)))
    assertEquals(g2.value, Some(Success(4)))
  }

  fixture.test("Pipe works for MulticastStrategy.replayLimited") { implicit s =>
    val ref1 = Pipe.replayLimited[Int](1)
    val (in1, out1) = ref1.multicast
    val f1 = out1.sumL.runToFuture

    in1.onNext(1)
    in1.onNext(2)
    in1.onNext(3)
    in1.onComplete()
    s.tick()

    val g1 = out1.sumL.runToFuture

    s.tick()
    assertEquals(f1.value, Some(Success(6)))
    assertEquals(g1.value, Some(Success(3)))

    val ref2 = Pipe(MulticastStrategy.replayLimited[Int](1, Seq(3)))
    val (in2, out2) = ref2.multicast
    val f2 = out2.sumL.runToFuture

    in2.onNext(1)
    in2.onNext(2)
    in2.onComplete()
    s.tick()

    val g2 = out2.sumL.runToFuture

    s.tick()
    assertEquals(f2.value, Some(Success(6)))
    assertEquals(g2.value, Some(Success(3)))
  }
}
