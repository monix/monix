package monifu.reactive.observers

import monifu.reactive.Observer
import java.util.concurrent.ConcurrentLinkedQueue
import monifu.reactive.api.Ack.{Done, Continue}
import monifu.concurrent.atomic.padded.Atomic
import monifu.concurrent.Scheduler
import scala.util.control.NonFatal
import scala.util.Failure
import scala.annotation.tailrec
import monifu.reactive.api.Ack
import scala.concurrent.Future


/**
 * A highly optimized concurrent Observer implementation.
 *
 * Currently used in [[monifu.reactive.Observable.merge Observable.merge]].
 */
final class BufferedObserver[-T] private (underlying: Observer[T])(implicit scheduler: Scheduler) extends Observer[T] {
  private[this] val queue = new ConcurrentLinkedQueue[T]()
  // to be modified only in onError, before upstreamIsComplete
  private[this] var errorThrown: Throwable = null
  // to be modified only in onError / onComplete
  @volatile private[this] var upstreamIsComplete = false
  // to be modified only by consumer
  @volatile private[this] var downstreamIsDone = false
  // for enforcing non-concurrent updates
  private[this] val itemsToPush = Atomic(0)

  def isComplete = upstreamIsComplete || downstreamIsDone

  def onNext(elem: T): Ack with Future[Ack] = {
    if (!upstreamIsComplete && !downstreamIsDone) {
      try {
        queue.offer(elem)
        pushToConsumer()
        Continue
      }
      catch {
        case NonFatal(ex) =>
          onError(ex)
          Done
      }
    }
    else
      Done
  }

  def onError(ex: Throwable) = {
    if (!upstreamIsComplete && !downstreamIsDone) {
      errorThrown = ex
      upstreamIsComplete = true
      pushToConsumer()
    }
  }

  def onComplete() = {
    if (!upstreamIsComplete && !downstreamIsDone) {
      upstreamIsComplete = true
      pushToConsumer()
    }
  }

  private[this] def pushToConsumer(): Unit =
    if (itemsToPush.getAndIncrement() == 0)
      scheduler.execute(new Runnable {
        def run() = fastLoop(0)
      })

  private[this] def rescheduled(processed: Int): Unit = {
    fastLoop(processed)
  }

  @tailrec
  private[this] def fastLoop(processed: Int): Unit = {
    if (!downstreamIsDone) {
      val next = queue.poll()

      if (next != null)
        underlying.onNext(next) match {
          case sync if sync.isCompleted =>
            sync match {
              case continue if continue == Continue || continue.value.get == Continue.IsSuccess =>
                // process next
                fastLoop(processed + 1)

              case done if done == Done || done.value.get == Done.IsSuccess =>
                // ending loop
                downstreamIsDone = true
                itemsToPush.set(0)

              case error if error.value.get.isFailure =>
                // ending loop
                downstreamIsDone = true
                itemsToPush.set(0)
                underlying.onError(error.value.get.failed.get)
            }

          case async =>
            async.onComplete {
              case Continue.IsSuccess =>
                // re-run loop (in different thread)
                rescheduled(processed + 1)

              case Done.IsSuccess =>
                // ending loop
                downstreamIsDone = true
                itemsToPush.set(0)

              case Failure(ex) =>
                // ending loop
                downstreamIsDone = true
                itemsToPush.set(0)
                underlying.onError(ex)

              case other =>
                // never happens, but to appease Scala's compiler
                downstreamIsDone = true
                itemsToPush.set(0)
                underlying.onError(new MatchError(s"$other"))
            }
        }
      else if (upstreamIsComplete) {
        // ending loop
        downstreamIsDone = true
        itemsToPush.set(0)
        if (errorThrown ne null)
          underlying.onError(errorThrown)
        else
          underlying.onComplete()
      }
      else {
        val remaining = itemsToPush.decrementAndGet(processed)
        // if the queue is non-empty (i.e. concurrent modifications just happened)
        // then start all over again
        if (remaining > 0) fastLoop(0)
      }
    }
  }
}

object BufferedObserver {
  def apply[T](observer: Observer[T])(implicit scheduler: Scheduler): BufferedObserver[T] = {
    new BufferedObserver[T](observer)
  }
}
