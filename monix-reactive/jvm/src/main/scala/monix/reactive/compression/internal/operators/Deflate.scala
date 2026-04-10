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
import java.{ util => ju }

import monix.reactive.compression.FlushMode

import scala.annotation.tailrec

private[compression] object Deflate {

  def pullOutput(
    deflater: Deflater,
    buffer: Array[Byte],
    flushMode: FlushMode
  ): Array[Byte] = {
    @tailrec
    def next(acc: Array[Byte]): Array[Byte] = {
      val size = deflater.deflate(buffer, 0, buffer.length, flushMode.jValue)
      val current = ju.Arrays.copyOf(buffer, size)
      if (current.isEmpty) acc
      else next(acc ++ current)
    }

    next(Array.emptyByteArray)
  }
}
