package monifu.rx.async

import monifu.rx.base.{ObservableBuilder => ObservableBuilderBase}
import scala.concurrent.{Future, ExecutionContext}
import monifu.concurrent.{Scheduler, Cancelable}
import monifu.rx.base.Ack.{Stop, Continue}
import scala.concurrent.duration.FiniteDuration
import monifu.concurrent.atomic.Atomic
import monifu.concurrent.cancelables.SingleAssignmentCancelable
import scala.util.{Failure, Success}

final class ObservableBuilder(implicit val ec: ExecutionContext)
  extends AnyVal with ObservableBuilderBase[Observable] {

  def empty[A]: Observable[A] =
    Observable.create { observer =>
      observer.onCompleted()
      Cancelable.alreadyCanceled
    }

  /**
   * Creates an Observable that only emits the given ''a''
   */
  def unit[A](elem: A): Observable[A] =
    Observable.create { observer =>
      val sub = Cancelable()
      observer.onNext(elem).onSuccess {
        case Continue =>
          if (!sub.isCanceled)
            observer.onCompleted()
        case _ =>
        // nothing
      }
      sub
    }

  /**
   * Creates an Observable that emits an error.
   */
  def error(ex: Throwable): Observable[Nothing] =
    Observable.create { observer =>
      observer.onError(ex)
      Cancelable.alreadyCanceled
    }

  /**
   * Creates an Observable that doesn't emit anything and that never completes.
   */
  def never: Observable[Nothing] =
    Observable.create { _ => Cancelable() }

  /**
   * Creates an Observable that emits the elements of the given ''sequence''
   */
  def fromTraversable[T](seq: TraversableOnce[T]): Observable[T] =
    Observable.create { observer =>
      def nextInput(iterator: Iterator[T]) =
        Future {
          if (iterator.hasNext)
            Some(iterator.next())
          else
            None
        }

      def startFeedLoop(subscription: Cancelable, iterator: Iterator[T]): Unit =
        if (!subscription.isCanceled)
          nextInput(iterator).onComplete {
            case Success(Some(elem)) =>
              observer.onNext(elem).onSuccess {
                case Continue =>
                  startFeedLoop(subscription, iterator)
                case Stop =>
                // do nothing else
              }
            case Success(None) =>
              observer.onCompleted()

            case Failure(ex) =>
              observer.onError(ex)
          }

      val iterator = seq.toIterator
      val subscription = Cancelable()
      startFeedLoop(subscription, iterator)
      subscription
    }

  /**
   * Merges the given list of ''observables'' into a single observable.
   *
   * NOTE: the result should be the same as [[monifu.rx.async.Observable.concat concat]] and in
   *       the asynchronous version it always is.
   */
  def merge[T](sources: Observable[T]*): Observable[T] =
    Observable.fromTraversable(sources).flatten

  /**
   * Concatenates the given list of ''observables''.
   */
  def concat[T](sources: Observable[T]*): Observable[T] =
    if (sources.isEmpty)
      empty
    else
      sources.tail.foldLeft(sources.head)((acc, elem) => acc ++ elem)
}
