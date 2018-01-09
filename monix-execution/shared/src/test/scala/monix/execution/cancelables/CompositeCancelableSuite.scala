/*
 * Copyright (c) 2014-2018 by The Monix Project Developers.
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

package monix.execution.cancelables

import minitest.SimpleTestSuite
import minitest.laws.Checkers
import monix.execution.Cancelable
import scala.collection.mutable.ListBuffer

object CompositeCancelableSuite extends SimpleTestSuite with Checkers {
  test("simple cancel") {
    val s = CompositeCancelable()
    val b1 = BooleanCancelable()
    val b2 = BooleanCancelable()
    s += b1
    s += b2
    s.cancel()

    assert(s.isCanceled)
    assert(b1.isCanceled)
    assert(b2.isCanceled)
  }

  test("cancel on assignment after being canceled") {
    val s = CompositeCancelable()
    val b1 = BooleanCancelable()
    s += b1
    s.cancel()

    val b2 = BooleanCancelable()
    s += b2

    assert(s.isCanceled)
    assert(b1.isCanceled)
    assert(b2.isCanceled)
  }

  test("addAll should be equivalent with repeated add") {
    check2 { (numbers: List[Int], preCancel: Boolean) =>
      val s1 = CompositeCancelable()
      if (preCancel) s1.cancel()

      val r1 = ListBuffer.empty[Int]
      s1 ++= numbers.map(n => Cancelable(() => r1 += n))

      val s2 = CompositeCancelable()
      if (preCancel) s2.cancel()

      val r2 = ListBuffer.empty[Int]
      for (n <- numbers) s2 += Cancelable(() => r2 += n)

      if (!preCancel) s1.cancel()
      if (!preCancel) s2.cancel()

      r1.toList.sorted == r2.toList.sorted
    }
  }

  test("addAll should cancel everything after composite is canceled") {
    val s = CompositeCancelable()
    s.cancel()

    val seq = (0 until 10).map(_ => BooleanCancelable())
    s ++= seq

    for (c <- seq) assert(c.isCanceled, "c.isCanceled")
  }

  test("remove should work") {
    val s = CompositeCancelable()

    val c1 = BooleanCancelable()
    s += c1
    val c2 = BooleanCancelable()
    s += c2

    s -= c1
    s.cancel()

    assert(!c1.isCanceled, "!c1.isCanceled")
    assert(c2.isCanceled, "c2.isCanceled")
  }

  test("removeAll should work") {
    val s = CompositeCancelable()

    val c1 = BooleanCancelable()
    val c2 = BooleanCancelable()
    val c3 = BooleanCancelable()
    s ++= Seq(c1,c2,c3)

    s --= Seq(c1,c2)
    s.cancel()

    assert(!c1.isCanceled, "!c1.isCanceled")
    assert(!c2.isCanceled, "!c2.isCanceled")
    assert(c3.isCanceled,  "c3.isCanceled")
  }

  test("removeAll should be equivalent with repeated remove") {
    check2 { (numbers: List[Int], preCancel: Boolean) =>
      val s1 = CompositeCancelable()
      if (preCancel) s1.cancel()

      val r1 = ListBuffer.empty[Int]
      val c1 = numbers.map(n => (n, Cancelable(() => r1 += n)))
      s1 ++= c1.map(_._2)
      s1 --= c1.filter(_._1 % 2 == 0).map(_._2)

      val s2 = CompositeCancelable()
      if (preCancel) s2.cancel()

      val r2 = ListBuffer.empty[Int]
      val c2 = numbers.map(n => (n, Cancelable(() => r2 += n)))
      s2 ++= c2.map(_._2)
      c2.filter(_._1 % 2 == 0).foreach(s2 -= _._2)

      if (!preCancel) s1.cancel()
      if (!preCancel) s2.cancel()

      r1.toList.sorted == r2.toList.sorted
    }
  }

  test("reset") {
    val seq = for (_ <- 0 until 10) yield BooleanCancelable()
    val cc = CompositeCancelable(seq:_*)

    cc.reset()
    cc.cancel()
    assert(seq.forall(!_.isCanceled))
  }

  test("getAndResetTo") {
    val set1 = List.fill(10)(BooleanCancelable())
    val cc = CompositeCancelable.fromSet(set1.toSet[Cancelable])

    val set2 = List.fill(10)(BooleanCancelable())
    assertEquals(cc.getAndSet(set2), set1.toSet[Cancelable])

    cc.cancel()
    assert(set1.forall(c => !c.isCanceled), "set1.forall(c => !c.isCanceled)")
    assert(set2.forall(_.isCanceled), "set2.forall(_.isCanceled)")

    val set3 = List.fill(10)(BooleanCancelable())
    assertEquals(cc.getAndSet(set3), Set.empty)
    assert(set3.forall(_.isCanceled), "set3.forall(_.isCanceled)")
  }
}
