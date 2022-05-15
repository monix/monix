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

import java.util.zip.Deflater

import monix.execution.Ack
import monix.execution.Ack.Continue
import monix.reactive.Observable.Operator
import monix.reactive.compression.{ CompressionLevel, CompressionParameters, CompressionStrategy, FlushMode }
import monix.reactive.observers.Subscriber

import scala.concurrent.Future
import scala.util.Success
import scala.util.control.NonFatal

private[compression] final class DeflateOperator(
  bufferSize: Int,
  params: CompressionParameters,
  noWrap: Boolean
) extends Operator[Array[Byte], Array[Byte]] {
  override def apply(out: Subscriber[Array[Byte]]): Subscriber[Array[Byte]] = {
    new Subscriber[Array[Byte]] {
      implicit val scheduler = out.scheduler

      private[this] var ack: Future[Ack] = Continue
      private[this] val deflate =
        new DeflateAdapter(
          bufferSize,
          params.level,
          params.strategy,
          params.flushMode,
          noWrap
        )

      def onNext(elem: Array[Byte]): Future[Ack] = {
        val result = deflate.onChunk(elem)

        // signaling downstream
        ack = out.onNext(result)
        ack
      }

      def onError(ex: Throwable): Unit = {
        deflate.close()
        out.onError(ex)
      }

      def onComplete(): Unit = {
        ack.syncOnComplete {
          case Success(Continue) =>
            var streamErrors = true
            try {
              val lastArray = deflate.finish()
              streamErrors = false
              out.onNext(lastArray)
              out.onComplete()
            } catch {
              case NonFatal(e) if streamErrors =>
                out.onError(e)
            } finally {
              deflate.close()
            }
          case _ => deflate.close()
        }
        ()
      }
    }
  }
}

// From https://github.com/zio/zio/blob/master/streams/jvm/src/main/scala/zio/stream/compression/Deflate.scala
private class DeflateAdapter(
  bufferSize: Int,
  level: CompressionLevel,
  strategy: CompressionStrategy,
  flushMode: FlushMode,
  noWrap: Boolean
) {
  private val deflater = new Deflater(level.value, noWrap)
  deflater.setStrategy(strategy.jValue)
  private val buffer = new Array[Byte](bufferSize)

  def onChunk(chunk: Array[Byte]): Array[Byte] = {
    deflater.setInput(chunk)
    Deflate.pullOutput(deflater, buffer, flushMode)
  }

  def finish(): Array[Byte] = {
    deflater.finish()
    val out = Deflate.pullOutput(deflater, buffer, flushMode)
    deflater.reset()
    out
  }

  def close(): Unit = deflater.`end`()
}
