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

import java.io.{ ByteArrayInputStream, ByteArrayOutputStream, ObjectInputStream, ObjectOutputStream }
import cats.laws._
import cats.laws.discipline._
import monix.execution.Ack
import monix.execution.Ack.Continue
import scala.util.{ Failure, Success, Try }

class SerializableSuite extends BaseLawsTestSuite {
  def serialize(obj: Serializable) = {
    val bytes = new ByteArrayOutputStream()
    val out = new ObjectOutputStream(bytes)
    out.writeObject(obj)
    out.flush()
    out.close()
    bytes.toByteArray
  }

  def deserialize[A](bytes: Array[Byte]): Try[A] =
    Try {
      val in = new ByteArrayInputStream(bytes)
      val oin = new ObjectInputStream(in)
      val ref = oin.readObject().asInstanceOf[A]
      if (ref == null) throw null
      ref
    }

  fixture.test("Observable is serializable") { implicit s =>
    check1 { (stream: Observable[Int]) =>
      val stream2 = deserialize[Observable[Int]](serialize(stream)) match {
        case Success(v) => v
        case Failure(e) => Observable.raiseError(e)
      }

      stream <-> stream2
    }
  }

  fixture.test("Observer is serializable") { implicit s =>
    class MyObserver extends Observer.Sync[Int] {
      var sum = 0
      var completed: Option[Throwable] = _

      override def onNext(elem: Int): Ack = {
        sum += elem
        Continue
      }

      override def onError(ex: Throwable): Unit =
        completed = Some(ex)
      override def onComplete(): Unit =
        completed = None
    }

    val obs = new MyObserver
    val obs2 = deserialize[MyObserver](serialize(obs)) match {
      case Success(v) => v
      case Failure(e) => throw e
    }

    obs2.onNext(1)
    obs2.onNext(2)
    obs2.onNext(3)
    obs2.onComplete()

    assertEquals(obs2.sum, 6)
    assertEquals(obs2.completed, None)
  }

  fixture.test("Consumer is serializable") { implicit s =>
    val ref1 = Consumer.foldLeft[Long, Long](0)(_ + _)
    val ref2 = deserialize[Consumer[Long, Long]](serialize(ref1)) match {
      case Success(v) => v
      case Failure(e) => throw e
    }

    val f = Observable.range(0, 100).consumeWith(ref2).runToFuture

    s.tick()
    assertEquals(f.value, Some(Success((99 * 50).toLong)))
  }

  fixture.test("Pipe is serializable") { implicit s =>
    val ref1 = Pipe.publish[Int]
    val ref2 = deserialize[Pipe[Int, Int]](serialize(ref1)) match {
      case Success(v) => v
      case Failure(e) => throw e
    }

    val (in, out) = ref2.concurrent
    val f = out.sumL.runToFuture

    in.onNext(1)
    in.onNext(2)
    in.onNext(3)
    in.onComplete()

    s.tick()
    assertEquals(f.value, Some(Success(6)))
  }
}
