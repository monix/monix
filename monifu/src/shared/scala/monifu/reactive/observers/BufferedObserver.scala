package monifu.reactive.observers

import monifu.reactive.Observer
import monifu.concurrent.internals.ConcurrentQueue
import monifu.reactive.api.Ack.{Done, Continue}
import monifu.concurrent.atomic.padded.Atomic
import monifu.concurrent.Scheduler
import scala.util.control.NonFatal
import scala.util.Failure
import scala.annotation.tailrec
import monifu.reactive.api.{BufferOverflowException, BufferPolicy, Ack}
import scala.concurrent.Future
import monifu.reactive.api.BufferPolicy.{TriggerOverflow, Unbounded}


/**
 * Interface describing [[monifu.reactive.Observer Observer]] wrappers that are thread-safe
 * (can receive concurrent events) and that return an immediate `Continue` when receiving `onNext`
 * events. Meant to be used by data sources that cannot uphold the no-concurrent events and the
 * back-pressure related requirements (i.e. data-sources that cannot wait on `Future[Continue]`
 * for sending the next event).
 *
 * Implementations of this interface have the following contract
 *
 *  - `onNext` / `onError` / `onComplete` of this interface MAY be called concurrently
 *  - `onNext` SHOULD return an immediate `Continue`, as long as the buffer is not full and
 *    the underlying observer hasn't signaled `Done` (N.B. due to the asynchronous nature, `Done` signaled by
 *    the underlying observer may be noticed later, so implementations of this interface make no guarantee about
 *    queued events - which could be generated, queued and dropped on the floor later)
 *  - `onNext` MUST return an immediate `Done` result, after it notices that the underlying observer signaled `Done`
 *    (due to the asynchronous nature of observers, this may happen later and queued events might get dropped on the floor)
 *  - in general the contract for the underlying Observer is fully respected (grammar, non-concurrent notifications, etc...)
 *  - when the underlying observer canceled (by returning `Done`), or when a concurrent upstream data source triggered
 *    an error, this SHOULD eventually be noticed and acted upon
 *  - as long as the buffer isn't full and the underlying observer isn't `Done`, then implementations
 *    of this interface SHOULD not lose events in the process
 *  - the buffer MAY BE either unbounded or bounded, in case of bounded buffers, then an appropriate policy
 *    needs to be set for when the buffer overflows - either an `onError` triggered in the underlying observer
 *    coupled with a `Done` signaled to the upstream data sources, or dropping events from the head or the tail of
 *    the queue
 *
 * See [[monifu.reactive.api.BufferPolicy BufferPolicy]] for the buffer policies available.
 */
trait BufferedObserver[-T] extends Observer[T]

/**
 * A highly optimized [[BufferedObserver]] implementation. It supports 2
 * [[monifu.reactive.api.BufferPolicy buffer policies]] - unbounded or bounded and terminated
 * with a [[monifu.reactive.api.BufferOverflowException BufferOverflowException]].
 *
 * Currently used in [[monifu.reactive.Observable.merge Observable.merge]].
 * 
 * The interface of this class is not public, so create an instance using an unbounded policy: {{{
 *   // by default, the constructor for BufferedObserver is returning this unbounded variant   
 *   BufferedObserver(observer)
 *   
 *   // or you can specify the Unbounded policy explicitly
 *   import monifu.reactive.api.BufferPolicy.Unbounded
 *   val buffered = BufferedObserver(observer, bufferPolicy = Unbounded)
 * }}}
 *
 * To create a bounded buffered observable that triggers
 * [[monifu.reactive.api.BufferOverflowException BufferOverflowException]]
 * when over capacity: {{{
 *   import monifu.reactive.api.BufferPolicy.TriggerOverflow
 *   // triggers buffer overflow error after 10000 messages
 *   val buffered = BufferedObserver(observer, bufferPolicy = TriggerOverflow(bufferSize = 10000))
 * }}}
 *
 * @param underlying is the underlying observer receiving the queued events
 * @param bufferSize is the maximum buffer size, or zero if unbounded
 */
private[observers] final class DefaultBufferedObserver[-T](underlying: Observer[T], bufferSize: Int = 0)(implicit scheduler: Scheduler) extends BufferedObserver[T] {
  require(bufferSize >= 0, "bufferSize must be a positive number")

  private[this] val queue = new ConcurrentQueue[T]()
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
    if (bufferSize == 0) {
      // unbounded branch
      if (itemsToPush.getAndIncrement() == 0)
        scheduler.execute(new Runnable {
          def run() = fastLoop(0)
        })
    }
    else {
      // bounded branch
      val leftToPush = itemsToPush.getAndIncrement()
      if (leftToPush > bufferSize && !upstreamIsComplete)
        onError(new BufferOverflowException(
          s"Downstream observer is too slow, buffer over capacity with a specified $bufferSize size and" +
          s" $leftToPush events being left for push"))
    }


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
                if (bufferSize == 0) {
                  // unbounded branch
                  rescheduled(processed + 1)
                }
                else {
                  itemsToPush.decrement(processed + 1)
                  rescheduled(0)
                }

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
  def apply[T](observer: Observer[T], bufferPolicy: BufferPolicy = Unbounded)(implicit scheduler: Scheduler): BufferedObserver[T] = {
    bufferPolicy match {
      case Unbounded =>
        new DefaultBufferedObserver[T](observer, bufferSize = 0)
      case TriggerOverflow(bufferSize) =>
        new DefaultBufferedObserver[T](observer, bufferSize = bufferSize)
      case _ =>
        throw new NotImplementedError(s"BufferedObserver($bufferPolicy)")
    }
  }
}
