package monifu.reactive

import scala.concurrent.{ExecutionContext, Promise, Future}
import monifu.reactive.api.Ack
import monifu.reactive.api.Ack.{Cancel, Continue}
import scala.util.{Try, Failure}
import scala.util.control.NonFatal
import monifu.syntax.TypeSafeEquals

package object internals {
  /**
   * Internal extensions to Future[Ack] used in the implementation of Observable.
   */
  implicit class FutureAckExtensions(val source: Future[Ack]) extends AnyVal {
    /**
     * On Continue, triggers the execution of the given callback.
     */
    def onContinue(cb: => Unit)(implicit ec: ExecutionContext): Unit =
      source match {
        case sync if sync.isCompleted =>
          if (sync === Continue || ((sync !== Cancel) && sync.value.get === Continue.IsSuccess))
            try cb catch {
              case NonFatal(ex) =>
                ec.reportFailure(ex)
            }
        case async =>
          async.onSuccess {
            case Continue => cb
          }
      }

    def onContinueComplete[T](observer: Observer[T], ex: Throwable = null)(implicit ec: ExecutionContext): Unit =
      source match {
        case sync if sync.isCompleted =>
          if (sync === Continue || ((sync !== Cancel) && sync.value.get === Continue.IsSuccess)) {
            var streamError = true
            try {
              if (ex eq null)
                observer.onComplete()
              else {
                streamError = false
                observer.onError(ex)
              }
            }
            catch {
              case NonFatal(err) =>
                if (streamError) observer.onError(ex) else ec.reportFailure(err)
            }
          }
        case async =>
          async.onSuccess {
            case Continue =>
              var streamError = true
              try {
                if (ex eq null)
                  observer.onComplete()
                else {
                  streamError = false
                  observer.onError(ex)
                }
              }
              catch {
                case NonFatal(err) =>
                  if (streamError) observer.onError(ex) else ec.reportFailure(err)
              }
          }
      }

    /**
     * On Cancel, triggers Continue on the given Promise.
     */
    def onCancelContinue(p: Promise[Ack])(implicit ec: ExecutionContext): Future[Ack] = {
      source match {
        case Continue => // do nothing
        case Cancel => p.success(Continue)

        case sync if sync.isCompleted && sync.value.get.isSuccess =>
          sync.value.get.get match {
            case Continue => // do nothing
            case Cancel => p.success(Continue)
          }

        case async =>
          async.onComplete {
            case Continue.IsSuccess => // nothing
            case Cancel.IsSuccess => p.success(Continue)
            case Failure(ex) => p.failure(ex)
            case other =>
              // branch not necessary, but Scala's compiler emits warnings if missing
              ec.reportFailure(new MatchError(other.toString))
          }
      }
      source
    }

    /**
     * On Cancel, try to trigger Cancel on the given Promise.
     */
    def ifCancelTryCanceling(p: Promise[Ack])(implicit ec: ExecutionContext): Future[Ack] = {
      source match {
        case Continue => // do nothing
        case Cancel => p.trySuccess(Cancel)

        case sync if sync.isCompleted =>
          sync.value.get match {
            case Continue.IsSuccess => // do nothing
            case Cancel.IsSuccess => p.trySuccess(Cancel)
            case Failure(ex) => p.tryFailure(ex)
            case other =>
              // branch not necessary, but Scala's compiler emits warnings if missing
              ec.reportFailure(new MatchError(other.toString))
          }

        case async =>
          async.onComplete {
            case Continue.IsSuccess => // nothing
            case Cancel.IsSuccess => p.trySuccess(Cancel)
            case Failure(ex) => p.tryFailure(ex)
            case other =>
              // branch not necessary, but Scala's compiler emits warnings if missing
              ec.reportFailure(new MatchError(other.toString))
          }
      }
      source
    }

    /**
     * On Cancel, try to trigger Cancel on the given Promise.
     */
    def ifCanceledDoCancel(p: Promise[Ack])(implicit ec: ExecutionContext): Future[Ack] = {
      source match {
        case Continue => // do nothing
        case Cancel => p.success(Cancel)

        case sync if sync.isCompleted =>
          sync.value.get match {
            case Continue.IsSuccess => // do nothing
            case Cancel.IsSuccess => p.success(Cancel)
            case Failure(ex) => p.failure(ex)
            case other =>
              // branch not necessary, but Scala's compiler emits warnings if missing
              ec.reportFailure(new MatchError(other.toString))
          }

        case async =>
          async.onComplete {
            case Continue.IsSuccess => // nothing
            case Cancel.IsSuccess => p.success(Cancel)
            case Failure(ex) => p.failure(ex)
            case other =>
              // branch not necessary, but Scala's compiler emits warnings if missing
              ec.reportFailure(new MatchError(other.toString))
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
          try f(sync.value.get) catch {
            case NonFatal(ex) =>
              ec.reportFailure(ex)
          }
          source
        case async =>
          source.onComplete(f)
          source
      }

    /**
     * Triggers execution of the given callback, once the source terminates either
     * with a `Cancel` or with a failure.
     */
    def onCancel(cb: => Unit)(implicit ec: ExecutionContext): Future[Ack] =
      source match {
        case Continue => source
        case Cancel =>
          try cb catch { case NonFatal(ex) => ec.reportFailure(ex) }
          source
        case sync if sync.isCompleted =>
          sync.value.get match {
            case Continue.IsSuccess => source
            case Cancel.IsSuccess | Failure(_) =>
              try cb catch { case NonFatal(ex) => ec.reportFailure(ex) }
              source
            case other =>
              // branch not necessary, but Scala's compiler emits warnings if missing
              ec.reportFailure(new MatchError(other.toString))
              source
          }
        case async =>
          source.onComplete {
            case Cancel.IsSuccess | Failure(_) => cb
            case _ => // nothing
          }
          source
      }
  }
}
