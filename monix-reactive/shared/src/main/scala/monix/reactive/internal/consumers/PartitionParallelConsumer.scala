package monix.reactive.internal.consumers

import monix.eval.{Callback, Task}
import monix.execution.Ack.{Continue, Stop}
import monix.execution.{Ack, Cancelable, Scheduler}
import monix.execution.atomic.AtomicBoolean
import monix.execution.cancelables.{AssignableCancelable, RefCountCancelable, SingleAssignmentCancelable}
import monix.execution.exceptions.CompositeException
import monix.reactive.{Consumer, OverflowStrategy}
import monix.reactive.observers.Subscriber
import monix.reactive.subjects.PublishSubject

import scala.concurrent.Future
import scala.util.control.NonFatal

/** Implementation for [[Consumer.partitionParallel]] */
private[reactive]
final class PartitionParallelConsumer[A](nrPartitions: Int, overflowStrategy: OverflowStrategy.Synchronous[A], partition: A => Int, consumer: (Int, Seq[A]) => Task[Unit]) extends Consumer[A, Unit] {
  override def createSubscriber(cb: Callback[Unit], s: Scheduler): (Subscriber[A], AssignableCancelable) = {

    val errors = scala.collection.mutable.ArrayBuffer.empty[Throwable]
    val sac = SingleAssignmentCancelable()

    val publishSubject = PublishSubject[A]()
    val stream = publishSubject.whileBusyBuffer(overflowStrategy)
    val isUpstreamDone = AtomicBoolean(false)

    val refCount = RefCountCancelable { () =>
      sac.cancel()
      if (errors.nonEmpty) {
        cb.onError(CompositeException(errors))
      } else {
        cb.onSuccess(())
      }
    }

    def subscriber(part: Int): Cancelable = {

      def consumerSubscriber(ref: Cancelable) = new Subscriber[Seq[A]] {
        implicit def scheduler: Scheduler = s

        def onNext(elements: Seq[A]) =
          try consumer(part, elements).coeval.value match {
            case Left(future) =>
              future.map(_ => Continue)
            case Right(()) =>
              Continue
          } catch {
            case NonFatal(ex) =>
              errors += ex
              //cancel the partition cancellation token
              ref.cancel()
              //only cancel the "main" cancellation token once
              if (!isUpstreamDone.getAndSet(true)) {
                refCount.cancel()
              }
              Stop
          }

        def onError(ex: Throwable) = ()

        def onComplete() = {
          //cancel the partition cancellation token
          ref.cancel()
          //only cancel the "main" cancellation token once
          if (!isUpstreamDone.getAndSet(true)) {
            refCount.cancel()
          }
        }
      }

      stream
        .filter(x => partition(x) % nrPartitions == part)
        .bufferIntrospective(s.executionModel.recommendedBatchSize)
        .subscribe(consumerSubscriber(refCount.acquire())) // per partition we create a cancellation token
    }

    val out = new Subscriber[A] {
      implicit def scheduler: Scheduler = s

      def onNext(elem: A): Future[Ack] = publishSubject.onNext(elem)

      def onError(ex: Throwable): Unit = {
        //upon receiving an error from upstream, cancel immediately and signal this error once
        sac.cancel()
        cb.onError(ex)
      }

      def onComplete(): Unit = publishSubject.onComplete()
    }

    (0 until nrPartitions).foreach(subscriber)

    out -> sac
  }
}
