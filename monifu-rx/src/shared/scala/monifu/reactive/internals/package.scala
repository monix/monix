package monifu.reactive

import scala.concurrent.{ExecutionContext, Promise, Future}
import monifu.reactive.api.Ack
import monifu.reactive.api.Ack.{Cancel, Continue}
import scala.util.{Try, Failure}
import scala.util.control.NonFatal

package object internals {
  /**
   * Internal extensions to Future[Ack] used in the implementation of Observable.
   */
  implicit class FutureAckExtensions(val source: Future[Ack]) extends AnyVal {
    /**
     * On Continue, triggers onComplete on the given Observer.
     */
    def onContinueTriggerComplete[T](observer: Observer[T])(implicit ec: ExecutionContext): Cancel =
      source match {
        case Continue =>
          try observer.onComplete() catch {
            case NonFatal(ex) => try observer.onError(ex) catch {
              case NonFatal(err) =>
                ec.reportFailure(ex)
                ec.reportFailure(err)
            }
          }
          Cancel
        case Cancel =>
          Cancel// do nothing
        case sync if sync.isCompleted && sync.value.get.isSuccess =>
          var streamError = true
          try sync.value.get match {
            case Continue.IsSuccess =>
              observer.onComplete()
              Cancel
            case Cancel.IsSuccess =>
              // do nothing
              Cancel
            case Failure(ex) =>
              streamError = false
              observer.onError(ex)
              Cancel
            case other =>
              // branch not necessary, but Scala's compiler emits warnings if missing
              ec.reportFailure(new MatchError(other.toString))
              Cancel
          }
          catch {
            case NonFatal(ex) =>
              if (streamError)
                try observer.onError(ex) catch {
                  case NonFatal(err) =>
                    ec.reportFailure(ex)
                    ec.reportFailure(err)
                }
              else
                ec.reportFailure(ex)
              Cancel
          }
        case async =>
          async.onComplete {
            case Continue.IsSuccess => observer.onComplete()
            case Cancel.IsSuccess => // nothing
            case Failure(ex) => observer.onError(ex)
            case other =>
              // branch not necessary, but Scala's compiler emits warnings if missing
              ec.reportFailure(new MatchError(other.toString))
          }
          Cancel
      }

    /**
     * On Continue, triggers onComplete on the given Observer.
     */
    def onContinueTriggerError[T](observer: Observer[T], error: Throwable)(implicit ec: ExecutionContext): Future[Ack] = {
      source match {
        case Continue =>
          try observer.onError(error) catch {
            case NonFatal(err2) =>
              ec.reportFailure(error)
              ec.reportFailure(err2)
          }
        case Cancel => // do nothing
        case sync if sync.isCompleted  =>
          sync.value.get match {
            case Continue.IsSuccess =>
              try observer.onError(error) catch {
                case NonFatal(err2) =>
                  ec.reportFailure(error)
                  ec.reportFailure(err2)
              }
            case Cancel.IsSuccess => // do nothing
            case Failure(ex) =>
              ec.reportFailure(ex)
              try observer.onError(error) catch {
                case NonFatal(err2) =>
                  ec.reportFailure(error)
                  ec.reportFailure(err2)
              }
            case other =>
              // branch not necessary, but Scala's compiler emits warnings if missing
              ec.reportFailure(new MatchError(other.toString))
          }
        case async =>
          async.onComplete {
            case Continue.IsSuccess =>
              try observer.onError(error) catch {
                case NonFatal(err2) =>
                  ec.reportFailure(error)
                  ec.reportFailure(err2)
              }
            case Cancel.IsSuccess => // nothing
            case Failure(ex) =>
              ec.reportFailure(ex)
              try observer.onError(error) catch {
                case NonFatal(err2) =>
                  ec.reportFailure(error)
                  ec.reportFailure(err2)
              }
            case other =>
              // branch not necessary, but Scala's compiler emits warnings if missing
              ec.reportFailure(new MatchError(other.toString))
          }
      }
      Cancel
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
     * On Cancel, triggers Cancel on the given Promise.
     */
    def onCancelComplete(p: Promise[Ack])(implicit ec: ExecutionContext): Future[Ack] = {
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
