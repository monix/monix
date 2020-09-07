package monix.reactive.compress

import minitest.api.AssertionException
import monix.reactive.Observable

abstract class BaseDecompressionTest extends BaseTestSuite with CompressionTestData {

  def jdkCompressedStream(input: Array[Byte]):Observable[Byte]
  def decompress(bufferSize: Int=32 * 1024, chunkSize:Int=8 * 1024) : Observable[Byte]=>Observable[Byte]

  testAsync("short stream") {
    jdkCompressedStream(shortText)
      .transform(decompress(bufferSize = 64))
      .toListL
      .map(list => assertEquals(list, shortText.toList))
      .runToFuture
  }
  testAsync("stream of two deflated inputs") {
    (jdkCompressedStream(shortText) ++ jdkCompressedStream(otherShortText))
      .transform(decompress(bufferSize = 64, chunkSize = 5))
      .toListL
      .map(list => assertEquals(list, (shortText ++ otherShortText).toList))
      .runToFuture
  }
  testAsync("stream of two deflated inputs as a single chunk") {
    (jdkCompressedStream(shortText) ++ jdkCompressedStream(otherShortText))
      .transform(decompress(bufferSize = 64))
      .toListL
      .map(list => assertEquals(list, (shortText ++ otherShortText).toList))
      .runToFuture
  }
  testAsync("long input") {
    jdkCompressedStream(longText)
      .transform(decompress(bufferSize = 64))
      .toListL
      .map(list => assertEquals(list, longText.toList))
      .runToFuture
  }
  testAsync("long input, buffer smaller than chunks") {
    jdkCompressedStream(longText)
      .transform(decompress(bufferSize = 1, chunkSize = 500))
      .toListL
      .map(list => assertEquals(list, longText.toList))
      .runToFuture
  }
  testAsync("long input, chunks smaller then buffer") {
    jdkCompressedStream(longText)
      .transform(decompress(bufferSize = 500, chunkSize = 1))
      .toListL
      .map(list => assertEquals(list, longText.toList))
      .runToFuture
  }
  testAsync("fail early if header is corrupted") {
    Observable
      .fromIterable(Seq(1, 2, 3, 4, 5).map(_.toByte))
      .transform(decompress())
      .toListL
      .map(_ => fail("should have failed"))
      .onErrorRecover { case e if !e.isInstanceOf[AssertionException] => () }
      .runToFuture
  }

  testAsync("fail if input stream finished unexpected") {
    jdkCompressedStream(longText)
      .take(20)
      .transform(decompress())
      .toListL
      .map(_ => fail("should have failed"))
      .onErrorRecover { case e if !e.isInstanceOf[AssertionException] => () }
      .void
      .runToFuture
  }
}
