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

import java.io.{ ByteArrayInputStream, ByteArrayOutputStream, InputStream }
import java.util.zip.{ GZIPInputStream, GZIPOutputStream }

import monix.eval.Task
import monix.reactive.Observable
import org.scalacheck.Prop

object GzipIntegrationTest extends CompressionIntegrationSuite {
  private implicit def a[A]: Task[Boolean] => Prop =
    _.runSyncUnsafe()

  test("gunzip(gzip(_)) <-> identity") {
    check1 { (input: String) =>
      Observable
        .now(input.getBytes())
        .transform(gzip())
        .transform(gunzip())
        .toListL
        .map(l => new String(l.flatten.toArray) == input)
    }
  }

  test("gunzip(jgzip(_)) <-> identity") {
    check1 { (input: String) =>
      val outputStream = new ByteArrayOutputStream()
      val gzos = new GZIPOutputStream(outputStream)
      gzos.write(input.getBytes())
      gzos.finish()

      val compressed = outputStream.toByteArray
      gzos.close()
      Observable
        .now(compressed)
        .transform(gunzip())
        .toListL
        .map(l => new String(l.flatten.toArray) == input)
    }
  }

  test("jgunzip(gzip(_) <-> identity") {
    check1 { (input: String) =>
      Observable
        .now(input.getBytes())
        .transform(gzip())
        .toListL
        .map { list =>
          val compressed = list.flatten.toArray
          val gzos = new GZIPInputStream(new ByteArrayInputStream(compressed))
          val decompressed = readAll(gzos)
          gzos.close()
          new String(decompressed) == input
        }
    }
  }

  private def readAll(is: InputStream) = {
    val buffer = new ByteArrayOutputStream
    var nRead = 0
    val data = new Array[Byte](1024)
    while ({
      nRead = is.read(data, 0, data.length)
      nRead != -1
    }) buffer.write(data, 0, nRead)

    buffer.flush()
    buffer.toByteArray
  }
}
