package monifu.reactive.operators

import monifu.concurrent.{Cancelable, Scheduler}
import monifu.concurrent.locks.SpinLock
import monifu.reactive.Ack.{Cancel, Continue}
import monifu.reactive.{Observer, Observable}
import monifu.reactive.observers.SynchronousObserver

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration
import scala.util.{Failure, Success}
import scala.util.control.NonFatal

object sample {
  /**
   * Implementation for `Observable.sample(initialDelay, delay)`.
   *
   * By comparison with [[monifu.reactive.Observable.sampleRepeated]],
   * this version does not emit any events if no fresh values were emitted
   * since the last sampling.
   */
  def once[T](source: Observable[T], initialDelay: FiniteDuration, delay: FiniteDuration)(implicit s: Scheduler) =
    Observable.create[T] { observer =>
      source.unsafeSubscribe(new SampleObserver(
        observer,
        initialDelay,
        delay,
        shouldRepeatOnSilence = false
      ))
    }

  /**
   * Implementation for `Observable.sampleRepeated(initialDelay, delay)`.
   *
   * By comparison with [[monifu.reactive.Observable.sample]], this version always
   * emits values at the requested interval, even if no fresh value in the meantime.
   */
  def repeated[T](source: Observable[T], initialDelay: FiniteDuration, delay: FiniteDuration)(implicit s: Scheduler) =
     Observable.create[T] { observer =>
      source.unsafeSubscribe(new SampleObserver(
        observer,
        initialDelay,
        delay,
        shouldRepeatOnSilence = true
      ))
    }
  
  protected[reactive] class SampleObserver[T]
      (downstream: Observer[T], initialDelay: FiniteDuration, delay: FiniteDuration, shouldRepeatOnSilence: Boolean)
      (implicit s: Scheduler)
    extends SynchronousObserver[T] {
    
    private[this] val lock = SpinLock()
    // must be synchronized by lock
    private[this] var isDone = false
    // must be synchronized by lock
    private[this] var lastEvent: T = _
    // must be synchronized by lock
    private[this] var valueHappened = false
    // must be canceled when streaming ends
    private[this] val task = initSchedule()

    /**
     * Schedules the repeated task that streams the last 
     * repeated value downstream.
     */
    def initSchedule(): Cancelable =
      s.scheduleRecursive(initialDelay, delay, { reschedule =>
        lock.enter {
          if (!isDone) {
            if (valueHappened) {
              valueHappened = shouldRepeatOnSilence

              val result =
                try downstream.onNext(lastEvent) catch {
                  case NonFatal(ex) =>
                    Future.failed(ex)
                }

              result match {
                case sync if sync.isCompleted =>
                  sync.value.get match {
                    case Success(Continue) =>
                      reschedule()
                    case Success(Cancel) =>
                      isDone = true
                    case Failure(ex) =>
                      onError(ex)
                  }
                case async =>
                  async.onComplete {
                    case Success(Continue) =>
                      reschedule()
                    case Success(Cancel) =>
                      lock.enter { isDone = true }
                    case Failure(ex) =>
                      onError(ex)
                  }
              }
            }
            else {
              reschedule()
            }
          }
        }
      })

    def onNext(elem: T) =
      lock.enter {
        if (isDone) Cancel
        else {
          valueHappened = true
          lastEvent = elem
          Continue
        }
      }

    def onError(ex: Throwable): Unit =
      lock.enter {
        if (!isDone) {
          isDone = true
          task.cancel()
          downstream.onError(ex)
        }
      }

    def onComplete(): Unit =
      lock.enter {
        if (!isDone) {
          isDone = true
          task.cancel()
          downstream.onComplete()
        }
      }
  }
}
