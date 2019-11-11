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

import java.io.IOException
import java.util.concurrent.{CountDownLatch, TimeUnit}

import minitest.SimpleTestSuite
import monix.eval.Task
import monix.reactive.Observable

import scala.concurrent.blocking
import scala.concurrent.duration._
import scala.util.Random

object ToInputStreamSuite extends SimpleTestSuite {

  testAsync("InputStream reads all bytes one by one") {
    var completed = false
    val observable = Observable.fromIterable(Seq(1.toByte, 2.toByte, 3.toByte))
      .map(Array(_))
      .doOnComplete(Task {
        completed = true
      })

    Observable.toInputStream(observable)
      .map { inputStream =>
        assertEquals(inputStream.read(), 1.toByte)
        assertEquals(inputStream.read(), 2.toByte)
        assertEquals(inputStream.read(), 3.toByte)

        assertEquals(inputStream.read(), -1.toByte)
        assertEquals(inputStream.read(), -1.toByte)

        assert(completed)
      }
      .runToFuture(monix.execution.Scheduler.global)
  }

  testAsync("InputStream reads delayed bytes one by one") {
    var completed = false
    val observable = Observable.fromIterable(Seq(1.toByte, 2.toByte, 3.toByte))
      .map(Array(_))
      .delayOnNext(100.millis)
      .doOnComplete(Task {
        completed = true
      })

    Observable.toInputStream(observable)
      .map { inputStream =>
        assertEquals(inputStream.read(), 1.toByte)
        assertEquals(inputStream.read(), 2.toByte)
        assertEquals(inputStream.read(), 3.toByte)

        assertEquals(inputStream.read(), -1.toByte)
        assertEquals(inputStream.read(), -1.toByte)

        assert(completed)
      }
      .runToFuture(monix.execution.Scheduler.global)
  }

  testAsync("InputStream waits until enough bytes are available") {
    val array1 = randomByteArrayOfSize(10)
    val array2 = randomByteArrayOfSize(5)
    val array3 = randomByteArrayOfSize(5)
    var completed = false
    val observable = Observable.fromIterable(Seq(array1, array2, array3))
      .doOnComplete(Task {
        completed = true
      })

    Observable.toInputStream(observable)
      .map { inputStream =>
        val result = new Array[Byte](20)
        inputStream.read(result)

        assertEquals(result.toList, (array1 ++ array2 ++ array3).toList)
        assertEquals(inputStream.read(), -1.toByte)
        assert(completed)
      }
      .runToFuture(monix.execution.Scheduler.global)
  }

  testAsync("InputStream reads the amount of bytes that is available") {
    val array1 = randomByteArrayOfSize(10)
    val array2 = randomByteArrayOfSize(5)
    val array3 = randomByteArrayOfSize(5)
    var completed = false
    val observable = Observable.fromIterable(Seq(array1, array2, array3))
      .doOnComplete(Task {
        completed = true
      })

    Observable.toInputStream(observable)
      .map { inputStream =>
        val result = new Array[Byte](200)
        inputStream.read(result, 0, 30)

        val (filledBytes, emptyBytes) = result.splitAt(20)
        assertEquals(filledBytes.toList, (array1 ++ array2 ++ array3).toList)
        assertEquals(emptyBytes.toList, List.fill(180)(0))
        assertEquals(inputStream.read(), -1.toByte)
        assert(completed)
      }
      .runToFuture(monix.execution.Scheduler.global)
  }

  testAsync("Observable cancels when the input stream is closed") {
    val latch = new CountDownLatch(1)
    val observable = Observable.fromIterable(Seq(1.toByte, 2.toByte, 3.toByte))
      .map(Array(_))
      .doOnSubscriptionCancel(Task {
        latch.countDown()
      })

    Observable.toInputStream(observable)
      .map { inputStream =>
        assertEquals(inputStream.read(), 1.toByte)
        inputStream.close()
        blocking(latch.await(1, TimeUnit.SECONDS))
        assertEquals(latch.getCount, 0)
      }
      .runToFuture(monix.execution.Scheduler.global)
  }

  testAsync("Observable cancels when the input stream is closed without reading anything") {
    val latch = new CountDownLatch(1)
    val observable = Observable.fromIterable(Seq(1.toByte, 2.toByte, 3.toByte))
      .map(Array(_))
      .doOnSubscriptionCancel(Task {
        latch.countDown()
      })

    Observable.toInputStream(observable)
      .map { inputStream =>
        inputStream.close()
        blocking(latch.await(1, TimeUnit.SECONDS))
        assertEquals(latch.getCount, 0)
        assertEquals(inputStream.read(), -1.toByte)
      }
      .runToFuture(monix.execution.Scheduler.global)
  }

  testAsync("InputStream reads all bytes one until error occurs") {
    val observable = Observable
      .fromIterable(
        Seq(
          Task.eval(Array(1.toByte)),
          Task.eval(Array(2.toByte)),
          Task.raiseError(new IllegalArgumentException("Expected exception")),
          Task.eval(Array(3.toByte))
        )
      )
      .mapEvalF(identity)

    Observable.toInputStream(observable)
      .map { inputStream =>
        assertEquals(inputStream.read(), 1.toByte)
        assertEquals(inputStream.read(), 2.toByte)
        intercept[IOException] {
          inputStream.read()
        }
      }
      .runToFuture(monix.execution.Scheduler.global)
  }

  testAsync("InputStream should throw an exception if element does not array before timeout") {
    val observable = Observable.fromIterable(Seq(1.toByte, 2.toByte, 3.toByte))
      .map(Array(_))
      .delayOnNext(100.millis)

    Observable.toInputStream(observable, 10.millis)
      .map { inputStream =>
        intercept {
          inputStream.read()
        }
      }
      .runToFuture(monix.execution.Scheduler.global)
  }

  def randomByteArrayOfSize(size: Int): Array[Byte] = {
    val bytes = new Array[Byte](size)
    Random.nextBytes(bytes)
    bytes
  }

}
