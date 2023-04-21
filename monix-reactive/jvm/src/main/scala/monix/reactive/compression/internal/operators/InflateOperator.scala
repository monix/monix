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

package monix.reactive.compression.internal.operators

import java.util.zip.{ DataFormatException, Inflater }
import java.{ util => ju }

import monix.execution.Ack
import monix.execution.Ack.{ Continue, Stop }
import monix.reactive.Observable.Operator
import monix.reactive.compression.CompressionException
import monix.reactive.observers.Subscriber

import scala.annotation.tailrec
import scala.concurrent.Future
import scala.util.Success
import scala.util.control.NonFatal

private[compression] final class InflateOperator(bufferSize: Int, noWrap: Boolean)
  extends Operator[Array[Byte], Array[Byte]] {

  def apply(out: Subscriber[Array[Byte]]): Subscriber[Array[Byte]] =
    new Subscriber[Array[Byte]] {
      implicit val scheduler = out.scheduler

      private[this] var isDone = false
      private[this] var ack: Future[Ack] = _
      private[this] val inflater = new InflateAdapter(bufferSize, noWrap)

      def onNext(elem: Array[Byte]): Future[Ack] = {
        if (isDone) {
          Stop
        } else {
          try {
            val result = inflater.onChunk(elem)
            // signaling downstream
            ack = out.onNext(result)
            ack
          } catch {
            case e if NonFatal(e) =>
              onError(e)
              Stop
          }
        }
      }

      def onError(ex: Throwable): Unit =
        if (!isDone) {
          isDone = true
          inflater.close()
          out.onError(ex)
        }

      def onComplete(): Unit =
        if (!isDone) {
          isDone = true
          if (ack == null) ack = Continue
          ack.syncOnComplete {
            case Success(Continue) =>
              var streamErrors = true
              try {
                val lastArray = inflater.finish()
                streamErrors = false
                out.onNext(lastArray)
                out.onComplete()
              } catch {
                case NonFatal(e) if streamErrors =>
                  out.onError(e)
              } finally {
                inflater.close()
              }
            case _ => inflater.close()
          }
          ()
        }
    }
}

// https://github.com/zio/zio/blob/master/streams/jvm/src/main/scala/zio/stream/platform.scala
private final class InflateAdapter(bufferSize: Int, noWrap: Boolean) {
  private val inflater = new Inflater(noWrap)
  private val buffer = new Array[Byte](bufferSize)

  def finish(): Array[Byte] = {
    try {
      if (inflater.finished()) {
        inflater.reset()
        Array.emptyByteArray
      } else {
        throw CompressionException(
          "Inflater is not finished when input stream completed"
        )
      }
    } catch {
      case e: DataFormatException => throw CompressionException(e)
    }
  }

  def onChunk(input: Array[Byte]): Array[Byte] = {
    try {
      inflater.setInput(input)
      pullAllOutput(input)
    } catch {
      case e: DataFormatException => throw CompressionException(e)
    }
  }

  private def pullAllOutput(input: Array[Byte]): Array[Byte] = {
    @tailrec
    def next(acc: Array[Byte]): Array[Byte] = {
      val read = inflater.inflate(buffer)
      val remaining = inflater.getRemaining()
      val current = ju.Arrays.copyOf(buffer, read)
      if (remaining > 0) {
        if (read > 0) next(acc ++ current)
        else if (inflater.finished()) {
          val leftover = input.takeRight(remaining)
          inflater.reset()
          inflater.setInput(leftover.toArray)
          next(acc ++ current)
        } else {
          // Impossible happened (aka programmer error). Die.
          throw new Exception("read = 0, remaining > 0, not finished")
        }
      } else if (read > 0) next(acc ++ current)
      else acc ++ current
    }
    if (inflater.needsInput()) Array.emptyByteArray
    else next(Array.emptyByteArray)
  }

  def close(): Unit = inflater.`end`()
}
