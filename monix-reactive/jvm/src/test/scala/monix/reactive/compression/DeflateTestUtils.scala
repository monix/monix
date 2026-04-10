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

package monix.reactive.compression

import java.io.ByteArrayInputStream
import java.util.Arrays
import java.util.zip.{ Deflater, DeflaterInputStream, Inflater, InflaterInputStream }

import monix.reactive.Observable

import scala.annotation.tailrec

trait DeflateTestUtils extends CompressionTestData {
  val inflateRandomExampleThatFailed: Array[Byte] =
    Array(100, 96, 2, 14, 108, -122, 110, -37, 35, -11, -10, 14, 47, 30, 43, 111, -80, 44, -34, 35, 35, 37, -103).map(
      _.toByte
    )

  def deflatedStream(bytes: Array[Byte], chunkSize: Int = 32 * 1024) =
    deflatedWith(bytes, new Deflater(), chunkSize)

  def noWrapDeflatedStream(bytes: Array[Byte], chunkSize: Int = 32 * 1024) =
    deflatedWith(bytes, new Deflater(9, true), chunkSize)

  def jdkDeflate(bytes: Array[Byte], deflater: Deflater): Array[Byte] = {
    val bigBuffer = new Array[Byte](1024 * 1024)
    val dif = new DeflaterInputStream(new ByteArrayInputStream(bytes), deflater)
    val read = dif.read(bigBuffer, 0, bigBuffer.length)
    Arrays.copyOf(bigBuffer, read)
  }

  def deflatedWith(bytes: Array[Byte], deflater: Deflater, chunkSize: Int = 32 * 1024) = {
    val arr = jdkDeflate(bytes, deflater)
    Observable
      .fromIterable(arr)
      .bufferTumbling(chunkSize)
      .map(_.toArray)
  }

  def jdkInflate(bytes: Array[Byte], noWrap: Boolean): Array[Byte] = {
    val bigBuffer = new Array[Byte](1024 * 1024)
    val inflater = new Inflater(noWrap)
    val iif = new InflaterInputStream(
      new ByteArrayInputStream(bytes),
      inflater
    )

    @tailrec
    def inflate(acc: Array[Byte]): Array[Byte] = {
      val read = iif.read(bigBuffer, 0, bigBuffer.length)
      if (read <= 0) acc
      else inflate(acc ++ bigBuffer.take(read).toList)
    }

    inflate(Array.emptyByteArray)
  }
}
