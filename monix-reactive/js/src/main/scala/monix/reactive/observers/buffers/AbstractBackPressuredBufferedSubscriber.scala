/*
 * Copyright (c) 2014-2016 by its authors. Some rights reserved.
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

package monix.reactive.observers.buffers

import monix.execution.Ack
import monix.execution.Ack.{Continue, Stop}
import monix.execution.internal.collection.ArrayQueue
import monix.execution.internal.math.nextPowerOf2
import monix.execution.misc.NonFatal
import monix.reactive.observers.{BufferedSubscriber, Subscriber}

import scala.concurrent.{Future, Promise}
import scala.util.{Failure, Success}

/** Shared internals between [[BackPressuredBufferedSubscriber]] and
  * [[BatchedBufferedSubscriber]].
  */
private[observers] abstract class AbstractBackPressuredBufferedSubscriber[A,R]
  (out: Subscriber[R], _size: Int)
  extends BufferedSubscriber[A] {

  require(_size > 0, "bufferSize must be a strictly positive number")
  private[this] val bufferSize = nextPowerOf2(_size)

  private[this] val em = out.scheduler.executionModel
  implicit final val scheduler = out.scheduler

  private[this] var upstreamIsComplete = false
  private[this] var downstreamIsComplete = false
  private[this] var errorThrown: Throwable = null
  private[this] var isLoopActive = false
  private[this] var backPressured: Promise[Ack] = null
  private[this] var lastIterationAck: Future[Ack] = Continue
  protected val queue = ArrayQueue.unbounded[A]

  final def onNext(elem: A): Future[Ack] = {
    if (upstreamIsComplete || downstreamIsComplete)
      Stop
    else if (elem == null) {
      onError(new NullPointerException("Null not supported in onNext"))
      Stop
    }
    else backPressured match {
      case null =>
        if (queue.length < bufferSize) {
          queue.offer(elem)
          pushToConsumer()
          Continue
        } else {
          backPressured = Promise[Ack]()
          queue.offer(elem)
          pushToConsumer()
          backPressured.future
        }
      case promise =>
        queue.offer(elem)
        pushToConsumer()
        promise.future
    }
  }

  final def onError(ex: Throwable): Unit = {
    if (!upstreamIsComplete && !downstreamIsComplete) {
      errorThrown = ex
      upstreamIsComplete = true
      pushToConsumer()
    }
  }

  final def onComplete(): Unit = {
    if (!upstreamIsComplete && !downstreamIsComplete) {
      upstreamIsComplete = true
      pushToConsumer()
    }
  }

  private def pushToConsumer(): Unit =
    if (!isLoopActive) {
      isLoopActive = true
      scheduler.execute(consumerRunLoop)
    }

  protected def fetchNext(): R

  private[this] val consumerRunLoop = new Runnable {
    def run(): Unit = fastLoop(lastIterationAck, 0)

    private final def signalNext(next: R): Future[Ack] =
      try {
        val ack = out.onNext(next)
        // Tries flattening the Future[Ack] to a
        // synchronous value
        if (ack == Continue || ack == Stop)
          ack
        else ack.value match {
          case Some(Success(success)) =>
            success
          case Some(Failure(ex)) =>
            downstreamSignalComplete(ex)
            Stop
          case None =>
            ack
        }
      } catch {
        case NonFatal(ex) =>
          downstreamSignalComplete(ex)
          Stop
      }

    private def downstreamSignalComplete(ex: Throwable = null): Unit = {
      downstreamIsComplete = true
      try {
        if (ex != null) out.onError(ex)
        else out.onComplete()
      } catch {
        case NonFatal(err) =>
          scheduler.reportFailure(err)
      }
    }

    private final def goAsync(next: R, ack: Future[Ack]): Unit = {
      ack.onComplete {
        case Success(Continue) =>
          val nextAck = signalNext(next)
          val isSync = ack == Continue || ack == Stop
          val nextFrame = if (isSync) em.nextFrameIndex(0) else 0
          fastLoop(nextAck, nextFrame)

        case Success(Stop) =>
          // ending loop
          downstreamIsComplete = true
          isLoopActive = false

        case Failure(ex) =>
          // ending loop
          isLoopActive = false
          downstreamSignalComplete(ex)
      }
    }

    private def stopStreaming(): Unit = {
      downstreamIsComplete = true
      isLoopActive = false
      if (backPressured != null) {
        backPressured.success(Stop)
        backPressured = null
      }
    }

    private final def fastLoop(prevAck: Future[Ack], startIndex: Int): Unit = {
      var ack = if (prevAck == null) Continue else prevAck
      var isFirstIteration = ack == Continue
      var nextIndex = startIndex
      var streamErrors = true

      try while (isLoopActive && !downstreamIsComplete) {
        val next = fetchNext()

        if (next != null) {
          if (nextIndex > 0 || isFirstIteration) {
            isFirstIteration = false

            ack match {
              case Continue =>
                ack = signalNext(next)
                if (ack == Stop) {
                  stopStreaming()
                  return
                } else {
                  val isSync = ack == Continue
                  nextIndex = if (isSync) em.nextFrameIndex(nextIndex) else 0
                }

              case Stop =>
                stopStreaming()
                return

              case async =>
                goAsync(next, ack)
                return
            }
          }
          else {
            goAsync(next, ack)
            return
          }
        }
        else {
          // Ending loop
          if (backPressured != null) {
            backPressured.success(if (upstreamIsComplete) Stop else Continue)
            backPressured = null
          }

          streamErrors = false
          if (upstreamIsComplete)
            downstreamSignalComplete(errorThrown)

          lastIterationAck = ack
          isLoopActive = false
          return
        }
      }
      catch {
        case NonFatal(ex) =>
          if (streamErrors) downstreamSignalComplete(ex)
          else scheduler.reportFailure(ex)
          lastIterationAck = Stop
          isLoopActive = false
      }
    }
  }
}