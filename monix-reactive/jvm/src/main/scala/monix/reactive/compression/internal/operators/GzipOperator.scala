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

import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.zip.{ CRC32, Deflater }

import monix.execution.Ack
import monix.execution.Ack.Continue
import monix.execution.Scheduler
import monix.reactive.Observable.Operator
import monix.reactive.compression.internal.operators.Gzipper.gzipOperatingSystem
import monix.reactive.compression.{
  gzipCompressionMethod,
  gzipExtraFlag,
  gzipFlag,
  gzipMagicFirstByte,
  gzipMagicSecondByte,
  zeroByte,
  CompressionLevel,
  CompressionParameters,
  CompressionStrategy,
  FlushMode
}
import monix.reactive.observers.Subscriber

import scala.concurrent.Future
import scala.util.Success
import scala.util.control.NonFatal

private[compression] final class GzipOperator(
  fileName: Option[String],
  modificationTime: Option[Instant],
  comment: Option[String],
  bufferSize: Int,
  params: CompressionParameters
) extends Operator[Array[Byte], Array[Byte]] {
  override def apply(out: Subscriber[Array[Byte]]): Subscriber[Array[Byte]] = {
    new Subscriber[Array[Byte]] {
      implicit val scheduler: Scheduler = out.scheduler

      private[this] var ack: Future[Ack] = _
      private[this] val gzipper =
        new Gzipper(
          bufferSize,
          params.level,
          params.strategy,
          params.flushMode,
          fileName,
          modificationTime,
          comment
        )

      def onNext(elem: Array[Byte]): Future[Ack] = {
        val result = gzipper.onChunk(elem)

        // signaling downstream
        ack = out.onNext(result)
        ack
      }

      def onError(ex: Throwable): Unit = {
        gzipper.close()
        out.onError(ex)
      }

      def onComplete(): Unit = {
        if (ack == null) ack = Continue
        ack.syncOnComplete {
          case Success(Continue) =>
            var streamErrors = true
            try {
              val lastArray = gzipper.finish()
              streamErrors = false
              out.onNext(lastArray)
              out.onComplete()
            } catch {
              case NonFatal(e) if streamErrors =>
                out.onError(e)
            } finally {
              gzipper.close()
            }
          case _ =>
            gzipper.close()
        }
        ()
      }
    }
  }
}

// From https://github.com/zio/zio/blob/master/streams/jvm/src/main/scala/zio/stream/compression/Gzipper.scala
private final class Gzipper(
  bufferSize: Int,
  level: CompressionLevel,
  strategy: CompressionStrategy,
  flushMode: FlushMode,
  fileName: Option[String],
  modificationTime: Option[Instant],
  comment: Option[String]
) {
  private val crc = new CRC32()
  private val buffer = new Array[Byte](bufferSize)
  private var headerSent = false
  private var inputSize: Long = 0
  private val deflater: Deflater = {
    val deflater = new Deflater(level.value, true)
    deflater.setStrategy(strategy.jValue)
    deflater
  }

  def finish(): Array[Byte] = {
    deflater.finish()
    val restAndTrailer =
      Deflate.pullOutput(deflater, buffer, flushMode) ++ getTrailer
    val lastChunk =
      if (headerSent) restAndTrailer
      else
        header() ++ restAndTrailer
    deflater.reset()
    crc.reset()
    inputSize = 0
    headerSent = false
    lastChunk
  }

  def onChunk(chunk: Array[Byte]): Array[Byte] = {
    inputSize += chunk.length
    crc.update(chunk)
    deflater.setInput(chunk)
    val deflated = Deflate.pullOutput(deflater, buffer, flushMode)
    if (headerSent) deflated
    else {
      headerSent = true
      header() ++ deflated
    }
  }

  def getTrailer: Array[Byte] = {
    def byte(v: Long, n: Int) = ((v >> n * 8) & 0xff).toByte

    val v = crc.getValue
    val s = inputSize & 0xffff
    Array(
      byte(v, 0),
      byte(v, 1),
      byte(v, 2),
      byte(v, 3),
      byte(s, 0),
      byte(s, 1),
      byte(s, 2),
      byte(s, 3)
    )
  }

  def close(): Unit =
    deflater.finish()

  private def header(): Array[Byte] = {
    val secondsSince197001010000: Long =
      modificationTime.fold(0L)(_.getEpochSecond)
    val header = Array(
      gzipMagicFirstByte,
      gzipMagicSecondByte,
      gzipCompressionMethod.DEFLATE,
      (gzipFlag.FHCRC + fileName.fold(zeroByte)(_ => gzipFlag.FNAME) + comment
        .fold(zeroByte)(_ => gzipFlag.FCOMMENT)).toByte,
      (secondsSince197001010000 & 0xff).toByte,
      ((secondsSince197001010000 >> 8) & 0xff).toByte,
      ((secondsSince197001010000 >> 16) & 0xff).toByte,
      ((secondsSince197001010000 >> 24) & 0xff).toByte,
      level.value match { // XFL: Extra flags
        case Deflater.BEST_COMPRESSION =>
          gzipExtraFlag.DEFLATE_MAX_COMPRESSION_SLOWEST_ALGO
        case Deflater.BEST_SPEED => gzipExtraFlag.DEFLATE_FASTEST_ALGO
        case _ => zeroByte
      },
      gzipOperatingSystem.THIS
    ).map(_.toByte)

    val crc32 = new CRC32()
    crc32.update(header)
    val fileNameEncoded = fileName.map { string =>
      val bytes =
        string.replaceAll("\u0000", "_").getBytes(StandardCharsets.ISO_8859_1)
      crc32.update(bytes)
      crc32.update(Array(zeroByte))
      bytes
    }
    val commentEncoded = comment.map { string =>
      val bytes =
        string.replaceAll("\u0000", " ").getBytes(StandardCharsets.ISO_8859_1)
      crc32.update(bytes)
      crc32.update(Array(zeroByte))
      bytes
    }
    val crc32Value = crc32.getValue
    val crc16 = Array[Byte](
      (crc32Value & 0xff).toByte,
      ((crc32Value >> 8) & 0xff).toByte
    )
    header ++ fileNameEncoded.getOrElse(Array.emptyByteArray) ++ commentEncoded
      .getOrElse(Array.emptyByteArray) ++ crc16
  }
}

private object Gzipper {

  private object gzipOperatingSystem {
    val FAT_FILESYSTEM: Byte = 0
    val AMIGA: Byte = 1
    val VMS: Byte = 2
    val UNIX: Byte = 3
    val VM_CMS: Byte = 4
    val ATARI_TOS: Byte = 5
    val HPFS_FILESYSTEM: Byte = 6
    val MACINTOSH: Byte = 7
    val Z_SYSTEM: Byte = 8
    val CP_M: Byte = 9
    val TOPS_20: Byte = 10
    val NTFS_FILESYSTEM: Byte = 11
    val QDOS: Byte = 12
    val ACORN_RISCOS: Byte = 13
    val UNKNOWN: Byte = 255.toByte

    val THIS: Byte = System.getProperty("os.name").toLowerCase() match {
      case name if name.indexOf("nux") > 0 => UNIX
      case name if name.indexOf("nix") > 0 => UNIX
      case name if name.indexOf("aix") >= 0 => UNIX
      case name if name.indexOf("win") >= 0 => NTFS_FILESYSTEM
      case name if name.indexOf("mac") >= 0 => MACINTOSH
      case _ => UNKNOWN
    }
  }

}
