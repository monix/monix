/*
 * Copyright (c) 2014-2021 by The Monix Project Developers.
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

package monix.reactive.compression

import java.io.{ ByteArrayInputStream, ByteArrayOutputStream }
import java.nio.charset.StandardCharsets
import java.util.zip.{ CRC32, GZIPInputStream, GZIPOutputStream }

import monix.reactive.Observable

import scala.annotation.tailrec

trait GzipTestsUtils extends CompressionTestData {

  val headerHeadBytes = Array(31.toByte, 139.toByte, 8.toByte)
  val mTimeXflAndOsBytes = Array.fill(6)(0.toByte)

  val headerWithExtra =
    makeStreamWithCustomHeader(
      4,
      (Seq(13.toByte, 0.toByte) ++ Seq.fill(13)(42.toByte)).toArray
    )

  val headerWithComment =
    makeStreamWithCustomHeader(
      16,
      "ZIO rocks!".getBytes(StandardCharsets.ISO_8859_1) ++ Array(0.toByte)
    )

  val headerWithFileName =
    makeStreamWithCustomHeader(
      8,
      "some-file-name.md".getBytes(StandardCharsets.ISO_8859_1) ++ Array(
        0.toByte
      )
    )

  val headerWithCrc = {
    val crcFlag = 2
    val headerBytes = Array(31, 139, 8, crcFlag, 0, 0, 0, 0, 0, 0).map(_.toByte)
    val crc32 = new CRC32
    crc32.update(headerBytes)
    val crc16 = (crc32.getValue() & 0xffffL).toInt
    val crc16Byte1 = (crc16 & 0xff).toByte
    val crc16Byte2 = (crc16 >> 8).toByte
    val header = headerBytes ++ Array(crc16Byte1, crc16Byte2)
    Observable.now(header ++ jdkGzip(shortText).drop(10))
  }

  val headerWithAll = {
    val flags = 2 + 4 + 8 + 16
    val fixedHeader = Array(31, 139, 8, flags, 0, 0, 0, 0, 0, 0).map(_.toByte)
    val extra = (Seq(7.toByte, 0.toByte) ++ Seq.fill(7)(99.toByte)).toArray
    val fileName =
      "win32.ini".getBytes(StandardCharsets.ISO_8859_1) ++ Array(0.toByte)
    val comment =
      "the last test".getBytes(StandardCharsets.ISO_8859_1) ++ Array(0.toByte)
    val headerUpToCrc = fixedHeader ++ extra ++ fileName ++ comment
    val crc32 = new CRC32
    crc32.update(headerUpToCrc)
    val crc16 = (crc32.getValue() & 0xffffL).toInt
    val crc16Byte1 = (crc16 & 0xff).toByte
    val crc16Byte2 = (crc16 >> 8).toByte
    val header = headerUpToCrc ++ Array(crc16Byte1, crc16Byte2)
    Observable.now(header ++ jdkGzip(shortText).drop(10))
  }
  def jdkGzippedStream(bytes: Array[Byte], syncFlush: Boolean = true, chunkSize: Int = 32 * 1024) =
    Observable.fromIterable(jdkGzip(bytes, syncFlush)).bufferTumbling(chunkSize).map(_.toArray)

  def jdkGzip(bytes: Array[Byte], syncFlush: Boolean = true): Array[Byte] = {
    val baos = new ByteArrayOutputStream(1024)
    val gzos = new GZIPOutputStream(baos, 1024, syncFlush)
    gzos.write(bytes)
    gzos.finish()
    gzos.flush()
    baos.toByteArray()
  }

  def jdkGunzip(gzipped: Array[Byte]): Array[Byte] = {
    val bigBuffer = new Array[Byte](1024 * 1024)
    val bais = new ByteArrayInputStream(gzipped)
    val gzis = new GZIPInputStream(bais)

    @tailrec
    def gunzip(acc: Array[Byte]): Array[Byte] = {
      val read = gzis.read(bigBuffer, 0, bigBuffer.length)
      if (read <= 0) acc
      else gunzip(acc ++ bigBuffer.take(read).toList)
    }
    gunzip(Array.emptyByteArray)
  }

  def makeStreamWithCustomHeader(flag: Int, headerTail: Array[Byte]) = {
    val headerHead = Array(31, 139, 8, flag, 0, 0, 0, 0, 0, 0).map(_.toByte)
    Observable.now(
      headerHead ++ headerTail ++ jdkGzip(shortText).drop(10)
    )
  }

  def u8(b: Byte): Int = b & 0xff
}
