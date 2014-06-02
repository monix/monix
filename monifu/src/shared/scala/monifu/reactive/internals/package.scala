package monifu.reactive

import scala.concurrent.{ExecutionContext, Promise, Future}
import monifu.reactive.api.Ack
import monifu.reactive.api.Ack.{Done, Continue}
import scala.util.{Try, Failure, Success}
import scala.util.control.NonFatal

package object internals {
  /**
   * Internal extensions to Future[Ack] used in the implementation of Observable.
   */
  implicit class FutureAckExtensions(val source: Future[Ack]) extends AnyVal {
    /**
     * On Continue, triggers onComplete on the given Observer.
     */
    def onContinueTriggerComplete[T](observer: Observer[T])(implicit ec: ExecutionContext): Future[Ack] = {
      source match {
        case Continue => observer.onComplete()
        case Done => // do nothing
        case sync if sync.isCompleted && sync.value.get.isSuccess =>
          sync.value.get.get match {
            case Continue => observer.onComplete()
            case Done => // do nothing
          }
        case async =>
          async.onComplete {
            case Success(Continue) => observer.onComplete()
            case Success(Done) => // nothing
            case Failure(ex) => observer.onError(ex)
          }
      }
      Done
    }

    /**
     * On Continue, triggers onComplete on the given Observer.
     */
    def onContinueTriggerError[T](observer: Observer[T], error: Throwable)(implicit ec: ExecutionContext): Future[Ack] = {
      source match {
        case Continue => observer.onComplete()
        case Done => // do nothing
        case sync if sync.isCompleted && sync.value.get.isSuccess =>
          sync.value.get.get match {
            case Continue => observer.onError(error)
            case Done => // do nothing
          }
        case async =>
          async.onComplete {
            case Success(Continue) => observer.onError(error)
            case Success(Done) => // nothing
            case Failure(ex) => observer.onError(ex)
          }
      }
      Done
    }

    /**
     * On Done, triggers Continue on the given Promise.
     */
    def onDoneContinue(p: Promise[Ack])(implicit ec: ExecutionContext): Future[Ack] = {
      source match {
        case Continue => // do nothing
        case Done => p.success(Continue)
        case sync if sync.isCompleted && sync.value.get.isSuccess =>
          sync.value.get.get match {
            case Continue => // do nothing
            case Done => p.success(Continue)
          }
        case async =>
          async.onComplete {
            case Success(Continue) => // nothing
            case Success(Done) => p.success(Continue)
            case Failure(ex) => p.failure(ex)
          }
      }
      source
    }

    /**
     * On Done, triggers Done on the given Promise.
     */
    def onDoneComplete(p: Promise[Ack])(implicit ec: ExecutionContext): Future[Ack] = {
      source match {
        case Continue => // do nothing
        case Done => p.success(Done)
        case sync if sync.isCompleted && sync.value.get.isSuccess =>
          sync.value.get.get match {
            case Continue => // do nothing
            case Done => p.success(Done)
          }
        case async =>
          async.onComplete {
            case Success(Continue) => // nothing
            case Success(Done) => p.success(Done)
            case Failure(ex) => p.failure(ex)
          }
      }
      source
    }

    /**
     * Unsafe version of `onComplete` that triggers execution synchronously
     * in case the source is already completed.
     */
    def onCompleteNow(f: Try[Ack] => Unit)(implicit ec: ExecutionContext): Future[Ack] =
      source match {
        case sync if sync.isCompleted =>
          f(sync.value.get)
          source
        case async =>
          source.onComplete(f)
          source
      }

    /**
     * Triggers execution of the given callback, once the source terminates either
     * with a `Done` or with a failure.
     */
    def onDone(cb: => Unit)(implicit ec: ExecutionContext): Future[Ack] =
      source match {
        case Continue => source
        case Done =>
          try cb catch { case NonFatal(ex) => ec.reportFailure(ex) }
          source
        case sync if sync.isCompleted && source.value.get.isSuccess =>
          sync.value.get.get match {
            case Continue => source
            case Done =>
              try cb catch { case NonFatal(ex) => ec.reportFailure(ex) }
              source
          }
        case async =>
          source.onComplete {
            case Success(Done) | Failure(_) => cb
            case _ => // nothing
          }
          source
      }
  }
}
