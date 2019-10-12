/*
 * Copyright (c) 2014-2019 by The Monix Project Developers.
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

package monix.reactive.internal.builders

import minitest.SimpleTestSuite
import monix.eval.{Coeval, Task}
import monix.execution.schedulers.TestScheduler
import monix.reactive.Observable

import scala.concurrent.duration._
import scala.util.Random

object ToInputStreamSuite extends SimpleTestSuite {

  test("InputStream reads all bytes one by one") {
    implicit val s = TestScheduler()
    val observable = Observable.fromIterable(Seq(1.toByte, 2.toByte, 3.toByte)).map(Array(_))

    s.tick()
    val inputStream = Observable.toInputStream(observable).runToFuture.value.get.get
    s.tick()

    assertEquals(inputStream.read(), 1.toByte)
    assertEquals(inputStream.read(), 2.toByte)
    assertEquals(inputStream.read(), 3.toByte)
    assertEquals(inputStream.read(), -1.toByte)
    assertEquals(inputStream.read(), -1.toByte)
  }

  test("InputStream reads delayed bytes one by one") {
    import monix.execution.Scheduler.Implicits.global

    val observable = Observable
      .fromIterable(Seq(1.toByte, 2.toByte, 3.toByte))
      .map(Array(_))
      .delayOnNext(100.millis)

    val inputStream = Observable.toInputStream(observable).runToFuture.value.get.get

    assertEquals(inputStream.read(), 1.toByte)
    assertEquals(inputStream.read(), 2.toByte)
    assertEquals(inputStream.read(), 3.toByte)
    assertEquals(inputStream.read(), -1.toByte)
    assertEquals(inputStream.read(), -1.toByte)
  }

  test("InputStream waits until enough bytes are available") {
    implicit val s = TestScheduler()
    val array1 = randomByteArrayOfSize(10)
    val array2 = randomByteArrayOfSize(5)
    val array3 = randomByteArrayOfSize(5)
    val observable = Observable.fromIterable(Seq(array1, array2, array3))
    val result = new Array[Byte](20)

    s.tick()
    val inputStream = Observable.toInputStream(observable).runToFuture.value.get.get
    s.tick()
    inputStream.read(result)

    assertEquals(result.toList, (array1 ++ array2 ++ array3).toList)
    assertEquals(inputStream.read(), -1.toByte)
  }

  test("InputStream reads the amount of bytes that is available") {
    implicit val s = TestScheduler()
    val array1 = randomByteArrayOfSize(10)
    val array2 = randomByteArrayOfSize(5)
    val array3 = randomByteArrayOfSize(5)
    val observable = Observable.fromIterable(Seq(array1, array2, array3))
    val result = new Array[Byte](200)

    s.tick()
    val inputStream = Observable.toInputStream(observable).runToFuture.value.get.get
    s.tick()
    inputStream.read(result, 0, 30)

    val (filledBytes, emptyBytes) = result.splitAt(20)
    assertEquals(filledBytes.toList, (array1 ++ array2 ++ array3).toList)
    assertEquals(emptyBytes.toList, List.fill(180)(0))
    assertEquals(inputStream.read(), -1.toByte)
  }

  test("Observable completes when the input stream is closed") {
    implicit val s = TestScheduler()
    var completed = false
    val observable = Observable
      .fromIterable(Seq(1.toByte, 2.toByte, 3.toByte))
      .map(Array(_))
      .doOnCompleteF(Coeval { completed = true })

    s.tick()
    val inputStream = Observable.toInputStream(observable).runToFuture.value.get.get
    s.tick()

    assertEquals(inputStream.read(), 1.toByte)
    inputStream.close()
    assert(completed, "The Observable is not completed")
    assertEquals(inputStream.read(), -1.toByte)
  }

  test("InputStream reads all bytes one until error occurs") {
    implicit val s = TestScheduler()
    val observable = Observable
      .fromIterable(
        Seq(
          Task.eval(Array(1.toByte)),
          Task.eval(Array(2.toByte)),
          Task.raiseError(new RuntimeException("Expected exception")),
          Task.eval(Array(3.toByte))
        )
      )
      .mapEvalF(identity)

    s.tick()
    val inputStream = Observable.toInputStream(observable).runToFuture.value.get.get
    s.tick()

    assertEquals(inputStream.read(), 1.toByte)
    assertEquals(inputStream.read(), 2.toByte)
    assertEquals(inputStream.read(), -1.toByte)
  }

  def randomByteArrayOfSize(size: Int): Array[Byte] = {
    val bytes = new Array[Byte](size)
    Random.nextBytes(bytes)
    bytes
  }

}
