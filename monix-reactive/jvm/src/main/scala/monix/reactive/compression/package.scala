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

package monix.reactive

import java.time.Instant
import java.util.zip.Deflater

import monix.reactive.compression.internal.operators.{ DeflateOperator, GunzipOperator, GzipOperator, InflateOperator }

// From https://github.com/typelevel/fs2/blob/main/core/jvm/src/main/scala/fs2/compression.scala
package object compression {

  /** Returns a stream that incrementally compresses input into the GZIP format
    *
    * @param bufferSize            Size of buffer used internally, affects performance.
    * @param compressionParameters see [[CompressionParameters]]
    * @param fileName              optional file name
    * @param comment               optional file modification time
    * @param modificationTime      optional file comment
    * @return
    */
  def gzip(
    bufferSize: Int = 32 * 1024,
    compressionParameters: CompressionParameters = CompressionParameters.Default,
    fileName: Option[String] = None,
    comment: Option[String] = None,
    modificationTime: Option[Instant] = None
  ): Observable[Array[Byte]] => Observable[Array[Byte]] = { source =>
    source.liftByOperator(
      new GzipOperator(
        fileName,
        modificationTime,
        comment,
        bufferSize,
        compressionParameters
      )
    )
  }

  /**
    * Decompresses gzipped stream. Compression method is described in https://tools.ietf.org/html/rfc1952.
    *
    * @param bufferSize Size of buffer used internally, affects performance.
    */
  def gunzip(
    bufferSize: Int = 32 * 1024
  ): Observable[Array[Byte]] => Observable[Array[Byte]] = { source =>
    source.liftByOperator(new GunzipOperator(bufferSize))
  }

  /**
    * Compresses stream with 'deflate' method described in https://tools.ietf.org/html/rfc1951.
    *
    * @param bufferSize            Size of internal buffer used for pulling data from deflater, affects performance.
    * @param compressionParameters See [[CompressionParameters]]
    * @param noWrap                Whether output stream is wrapped in ZLIB header and trailer. For HTTP 'deflate' content-encoding should be false, see https://tools.ietf.org/html/rfc2616.
    */
  def deflate(
    bufferSize: Int = 32 * 1024,
    compressionParameters: CompressionParameters = CompressionParameters.Default,
    noWrap: Boolean = false
  ): Observable[Array[Byte]] => Observable[Array[Byte]] = { source =>
    source.liftByOperator(
      new DeflateOperator(bufferSize, compressionParameters, noWrap)
    )
  }

  /**
    * Decompresses deflated stream. Compression method is described in https://tools.ietf.org/html/rfc1951.
    *
    * @param bufferSize Size of buffer used internally, affects performance.
    * @param noWrap     Whether is wrapped in ZLIB header and trailer, see https://tools.ietf.org/html/rfc1951.
    *                   For HTTP 'deflate' content-encoding should be false, see https://tools.ietf.org/html/rfc2616.
    */
  def inflate(
    bufferSize: Int = 32 * 1024,
    noWrap: Boolean = false
  ): Observable[Array[Byte]] => Observable[Array[Byte]] = { source =>
    source.liftByOperator(new InflateOperator(bufferSize, noWrap))
  }

  private[compression] val zeroByte: Byte = 0
  private[compression] val gzipMagicFirstByte: Byte = 0x1f.toByte
  private[compression] val gzipMagicSecondByte: Byte = 0x8b.toByte

  private[compression] object gzipFlag {
    def apply(flags: Byte, flag: Byte): Boolean = (flags & flag) == flag

    def apply(flags: Byte, flag: Int): Boolean = (flags & flag) == flag

    def ftext(flags: Byte): Boolean = apply(flags, FTEXT)

    def fhcrc(flags: Byte): Boolean = apply(flags, FHCRC)

    def fextra(flags: Byte): Boolean = apply(flags, FEXTRA)

    def fname(flags: Byte): Boolean = apply(flags, FNAME)

    def fcomment(flags: Byte): Boolean = apply(flags, FCOMMENT)

    def reserved5(flags: Byte): Boolean = apply(flags, RESERVED_BIT_5)

    def reserved6(flags: Byte): Boolean = apply(flags, RESERVED_BIT_6)

    def reserved7(flags: Byte): Boolean = apply(flags, RESERVED_BIT_7)

    val FTEXT: Byte = 1
    val FHCRC: Byte = 2
    val FEXTRA: Byte = 4
    val FNAME: Byte = 8
    val FCOMMENT: Byte = 16
    val RESERVED_BIT_5 = 32
    val RESERVED_BIT_6 = 64
    val RESERVED_BIT_7: Int = 128
  }

  private[compression] object gzipExtraFlag {
    val DEFLATE_MAX_COMPRESSION_SLOWEST_ALGO: Byte = 2
    val DEFLATE_FASTEST_ALGO: Byte = 4
  }

  private[compression] object gzipCompressionMethod {
    val DEFLATE: Byte = Deflater.DEFLATED.toByte
  }
}
