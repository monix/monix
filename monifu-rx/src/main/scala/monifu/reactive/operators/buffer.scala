package monifu.reactive.operators

import monifu.concurrent.Scheduler
import monifu.concurrent.locks.SpinLock
import monifu.reactive.Ack.{Cancel, Continue}
import monifu.reactive.observers.SynchronousObserver
import monifu.reactive.{Ack, Observer, Observable}

import scala.collection.mutable.ArrayBuffer
import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration
import scala.util.{Failure, Success}
import scala.util.control.NonFatal

/**
 * Implementation for [[Observable.buffer]].
 */
object Buffer {
  def withSize[T](source: Observable[T])(count: Int): Observable[Seq[T]] =
    Observable.create { observer =>
      source.unsafeSubscribe(new Observer[T] {
        private[this] var buffer = ArrayBuffer.empty[T]
        private[this] var size = 0

        def onNext(elem: T): Future[Ack] = {
          size += 1
          buffer.append(elem)
          if (size >= count) {
            val oldBuffer = buffer
            buffer = ArrayBuffer.empty[T]
            size = 0
            observer.onNext(oldBuffer)
          }
          else
            Continue
        }

        def onError(ex: Throwable): Unit = {
          observer.onError(ex)
          buffer = null
        }

        def onComplete(): Unit = {
          if (size > 0) {
            observer.onNext(buffer)
            observer.onComplete()
          }
          else
            observer.onComplete()
          buffer = null
        }
      })
    }

  def withTimespan[T](source: Observable[T])(timespan: FiniteDuration)(implicit s: Scheduler) =
    Observable.create[Seq[T]] { observer =>
      source.unsafeSubscribe(new SynchronousObserver[T] {
        private[this] val lock = SpinLock()
        private[this] var queue = ArrayBuffer.empty[T]
        private[this] var isDone = false

        private[this] val task =
          s.scheduleRecursive(timespan, timespan, { reschedule =>
            lock.enter {
              if (!isDone) {
                val current = queue
                queue = ArrayBuffer.empty
                val result =
                  try observer.onNext(current) catch {
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
                        isDone = true
                        observer.onError(ex)
                    }
                  case async =>
                    async.onComplete {
                      case Success(Continue) =>
                        lock.enter {
                          if (!isDone) reschedule
                        }
                      case Success(Cancel) =>
                        lock.enter {
                          isDone = true
                        }
                      case Failure(ex) =>
                        lock.enter {
                          isDone = true
                          observer.onError(ex)
                        }
                    }
                }
              }
            }
          })

        def onNext(elem: T): Ack = lock.enter {
          if (!isDone) {
            queue.append(elem)
            Continue
          }
          else
            Cancel
        }

        def onError(ex: Throwable): Unit = lock.enter {
          if (!isDone) {
            isDone = true
            queue = null
            observer.onError(ex)
            task.cancel()
          }
        }

        def onComplete(): Unit = lock.enter {
          if (!isDone) {
            if (queue.nonEmpty) {
              observer.onNext(queue)
              observer.onComplete()
            }
            else
              observer.onComplete()

            isDone = true
            queue = null
            task.cancel()
          }
        }
      })
    }
}
