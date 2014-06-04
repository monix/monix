package monifu.reactive

import scala.concurrent.{ExecutionContext, Promise, Future}
import monifu.reactive.api.Ack
import monifu.reactive.api.Ack.{Done, Continue}
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
    def onContinueTriggerComplete[T](observer: Observer[T])(implicit ec: ExecutionContext): Done =
      source match {
        case Continue =>
          try observer.onComplete() catch {
            case NonFatal(ex) => try observer.onError(ex) catch {
              case NonFatal(err) =>
                ec.reportFailure(ex)
                ec.reportFailure(err)
            }
          }
          Done
        case Done =>
          Done// do nothing
        case sync if sync.isCompleted && sync.value.get.isSuccess =>
          var streamError = true
          try sync.value.get match {
            case Continue.IsSuccess =>
              observer.onComplete()
              Done
            case Done.IsSuccess =>
              // do nothing
              Done
            case Failure(ex) =>
              streamError = false
              observer.onError(ex)
              Done
            case other =>
              // branch not necessary, but Scala's compiler emits warnings if missing
              ec.reportFailure(new MatchError(other.toString))
              Done
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
              Done
          }
        case async =>
          async.onComplete {
            case Continue.IsSuccess => observer.onComplete()
            case Done.IsSuccess => // nothing
            case Failure(ex) => observer.onError(ex)
            case other =>
              // branch not necessary, but Scala's compiler emits warnings if missing
              ec.reportFailure(new MatchError(other.toString))
          }
          Done
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
        case Done => // do nothing
        case sync if sync.isCompleted  =>
          sync.value.get match {
            case Continue.IsSuccess =>
              try observer.onError(error) catch {
                case NonFatal(err2) =>
                  ec.reportFailure(error)
                  ec.reportFailure(err2)
              }
            case Done.IsSuccess => // do nothing
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
            case Done.IsSuccess => // nothing
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
            case Continue.IsSuccess => // nothing
            case Done.IsSuccess => p.success(Continue)
            case Failure(ex) => p.failure(ex)
            case other =>
              // branch not necessary, but Scala's compiler emits warnings if missing
              ec.reportFailure(new MatchError(other.toString))
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

        case sync if sync.isCompleted =>
          sync.value.get match {
            case Continue.IsSuccess => // do nothing
            case Done.IsSuccess => p.success(Done)
            case Failure(ex) => p.failure(ex)
            case other =>
              // branch not necessary, but Scala's compiler emits warnings if missing
              ec.reportFailure(new MatchError(other.toString))
          }

        case async =>
          async.onComplete {
            case Continue.IsSuccess => // nothing
            case Done.IsSuccess => p.success(Done)
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
     * with a `Done` or with a failure.
     */
    def onDone(cb: => Unit)(implicit ec: ExecutionContext): Future[Ack] =
      source match {
        case Continue => source
        case Done =>
          try cb catch { case NonFatal(ex) => ec.reportFailure(ex) }
          source
        case sync if sync.isCompleted =>
          sync.value.get match {
            case Continue.IsSuccess => source
            case Done.IsSuccess | Failure(_) =>
              try cb catch { case NonFatal(ex) => ec.reportFailure(ex) }
              source
            case other =>
              // branch not necessary, but Scala's compiler emits warnings if missing
              ec.reportFailure(new MatchError(other.toString))
              source
          }
        case async =>
          source.onComplete {
            case Done.IsSuccess | Failure(_) => cb
            case _ => // nothing
          }
          source
      }
  }
}
