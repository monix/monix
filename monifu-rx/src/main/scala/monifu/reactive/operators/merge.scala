package monifu.reactive.operators

import monifu.concurrent.Scheduler
import monifu.reactive.BufferPolicy.{OverflowTriggering, Unbounded}
import monifu.reactive.internals.{BoundedMergeBuffer, UnboundedMergeBuffer}
import monifu.reactive.observers.SynchronousObserver
import monifu.reactive.{Observer, Ack, BufferPolicy, Observable}

object merge {
  /**
   * Implementation for [[monifu.reactive.Observable.merge]].
   */
  def apply[T,U](bufferPolicy: BufferPolicy, batchSize: Int)
      (implicit ev: T <:< Observable[U], s: Scheduler) =
    (source: Observable[T]) =>
      Observable.create[U] { observerB =>
        // if the parallelism is unbounded and the buffer policy allows for a
        // synchronous buffer, then we can use a more efficient implementation
        bufferPolicy match {
          case Unbounded | OverflowTriggering(_) if batchSize <= 0 =>
            source.unsafeSubscribe(new SynchronousObserver[T] {
              private[this] val buffer =
                new UnboundedMergeBuffer[U](observerB, bufferPolicy)
              def onNext(elem: T): Ack =
                buffer.merge(elem)
              def onError(ex: Throwable): Unit =
                buffer.onError(ex)
              def onComplete(): Unit =
                buffer.onComplete()
            })
  
          case _ =>
            source.unsafeSubscribe(new Observer[T] {
              private[this] val buffer: BoundedMergeBuffer[U] =
                new BoundedMergeBuffer[U](observerB, batchSize, bufferPolicy)
              def onNext(elem: T) =
                buffer.merge(elem)
              def onError(ex: Throwable) =
                buffer.onError(ex)
              def onComplete() =
                buffer.onComplete()
            })
        }
      }
}
