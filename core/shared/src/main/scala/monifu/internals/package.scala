/*
 * Copyright (c) 2014-2015 by its authors. Some rights reserved.
 * See the project homepage at: http://www.monifu.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package monifu

import monifu.concurrent.{Cancelable, Scheduler}
import monifu.Ack.{Cancel, Continue}

import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.language.implicitConversions
import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}


package object internals {
  /**
   * Internal extensions to Future[Ack] used in the implementation of Observable.
   */
  private[monifu] implicit class FutureAckExtensions(val source: Future[Ack]) extends AnyVal {
    /**
     * If the result of this `Future` is a `Continue`, then completes
     * the stream with an `onComplete`.
     *
     * Note that it doesn't do exception handling - if the future is a failure
     * then the behavior is undefined.
     */
    def onContinueSignalComplete[T](observer: Observer[T])(implicit s: Scheduler): Unit =
      source match {
        case sync if sync.isCompleted =>
          sync.value.get match {
            case Continue.IsSuccess =>
              observer.onComplete()
            case _ =>
              () // do nothing
          }

        case async =>
          async.onComplete {
            case Continue.IsSuccess =>
              observer.onComplete()
            case _ =>
              () // do nothing
          }
      }

    /**
     * If the result of this `Future` is a `Continue`, then completes
     * the stream with an `onError`.
     *
     * Note that it doesn't do exception handling - if the future is a failure
     * then the behavior is undefined.
     */
    def onContinueSignalError[T](observer: Observer[T], ex: Throwable)(implicit s: Scheduler): Unit =
      source match {
        case sync if sync.isCompleted =>
          sync.value.get match {
            case Continue.IsSuccess =>
              observer.onError(ex)
            case _ =>
              () // do nothing
          }
        case async =>
          async.onComplete {
            case Continue.IsSuccess =>
              observer.onError(ex)
            case _ =>
              () // do nothing
          }
      }

    /**
     * On Continue, triggers the execution of the given callback.
     */
    def onContinue(callback: => Unit)(implicit s: Scheduler): Unit =
      source match {
        case sync if sync.isCompleted =>
          if (sync == Continue || (sync != Cancel && sync.value.get == Continue.IsSuccess))
            try callback catch {
              case NonFatal(ex) =>
                s.reportFailure(ex)
            }

        case async =>
          async.onComplete {
            case Continue.IsSuccess =>
              try callback catch {
                case NonFatal(ex) =>
                  s.reportFailure(ex)
              }
            case _ =>
              () // do nothing
          }
      }

    /**
     * An implementation of `flatMap` that executes
     * synchronously if the source is already completed.
     */
    def fastFlatMap[R](f: Ack => Future[R])(implicit ec: ExecutionContext): Future[R] =
      source.value match {
        case Some(sync) =>
          sync match {
            case Success(ack) =>
              try f(ack) catch {
                case NonFatal(ex) =>
                  Future.failed(ex)
              }
            case Failure(_) =>
              source.asInstanceOf[Future[R]]
          }
        case None =>
          source.flatMap(f)
      }

    /**
     * Helper that triggers [[Cancelable.cancel]] on the given reference,
     * in case our source is a [[Cancel]] or a failure.
     */
    def ifCanceledDoCancel(cancelable: Cancelable)(implicit s: Scheduler): Future[Ack] = {
      if (source.isCompleted) {
        if (source == Continue || source.value.get == Continue.IsSuccess) {
          source // do nothing
        }
        else try {
          cancelable.cancel()
          source
        }
        catch {
          case NonFatal(ex) =>
            Future.failed(ex)
        }
      }
      else {
        val p = Promise[Ack]()
        source.onComplete {
          case Continue.IsSuccess =>
            p.success(Continue)

          case result@(Cancel.IsSuccess | Failure(_)) =>
            try {
              cancelable.cancel()
              p.complete(result)
            }
            catch {
              case NonFatal(ex) =>
                p.failure(ex)
            }

          case other =>
            p.complete(other)
        }

        p.future
      }
    }

    // -----

    def onContinueStreamOnNext[T](observer: Observer[T], nextElem: T)(implicit s: Scheduler) =
      if (source.isCompleted) {
        if (source == Continue || source.value.get == Continue.IsSuccess)
          observer.onNext(nextElem)
        else
          Cancel
      }
      else source.flatMap {
        case Continue =>
          observer.onNext(nextElem)
        case Cancel =>
          Cancel
      }

    def mapToContinue(implicit s: Scheduler): Future[Continue] =
      source match {
        case sync if sync.isCompleted && sync.value.get.isSuccess =>
          Continue

        case async =>
          async.map(_ => Continue)
      }

    def onCancelStreamOnNext[T](observer: Observer[T], nextElem: T)(implicit s: Scheduler) =
      source match {
        case sync if sync.isCompleted =>
          if (sync == Continue || sync.value.get == Continue.IsSuccess)
            Continue
          else
            observer.onNext(nextElem)

        case async =>
          async.flatMap {
            case Continue => Continue
            case Cancel =>
              observer.onNext(nextElem)
          }
      }

    def onContinueCompleteWith[T](observer: Observer[T], lastElem: T)(implicit s: Scheduler): Unit =
      source match {
        case sync if sync.isCompleted =>
          if (sync == Continue || ((sync != Cancel) && sync.value.get == Continue.IsSuccess)) {
            try {
              observer.onNext(lastElem)
              observer.onComplete()
            }
            catch {
              case NonFatal(err) =>
                observer.onError(err)
            }
          }
        case async =>
          async.onSuccess {
            case Continue =>
              try {
                observer.onNext(lastElem)
                observer.onComplete()
              }
              catch {
                case NonFatal(err) =>
                  observer.onError(err)
              }
          }
      }

    /**
     * On Cancel, triggers Continue on the given Promise.
     */
    def onCancelContinue(p: Promise[Ack])(implicit s: Scheduler): Future[Ack] = {
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
              s.reportFailure(new MatchError(other.toString))
          }
      }
      source
    }

    /**
     * On Cancel, try to trigger Cancel on the given Promise.
     */
    def ifCancelTryCanceling(p: Promise[Ack])(implicit s: Scheduler): Future[Ack] = {
      if (source.isCompleted) {
        // do
        if (source == Continue)
          () // do nothing
        else if (source == Cancel)
          p.trySuccess(Cancel)
        else source.value.get match {
          case Continue.IsSuccess =>
            () // do nothing
          case Cancel.IsSuccess =>
            p.trySuccess(Cancel)
          case Failure(ex) =>
            p.tryFailure(ex)
          case other =>
            // branch not necessary, but the Scala
            // compiler emits warnings if missing
            s.reportFailure(new MatchError(other.toString))
        }
      }
      else source.onComplete {
        case Continue.IsSuccess =>
          () // do nothing
        case Cancel.IsSuccess =>
          p.trySuccess(Cancel)
        case Failure(ex) =>
          p.tryFailure(ex)
        case other =>
          // branch not necessary, but the Scala
          // compiler emits warnings if missing
          s.reportFailure(new MatchError(other.toString))
      }

      source
    }

    /**
     * On Cancel, try to trigger Cancel on the given Promise.
     */
    def ifCanceledDoCancel(p: Promise[Ack])(implicit s: Scheduler): Future[Ack] = {
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
              s.reportFailure(new MatchError(other.toString))
          }

        case async =>
          async.onComplete {
            case Continue.IsSuccess => // nothing
            case Cancel.IsSuccess => p.success(Cancel)
            case Failure(ex) => p.failure(ex)
            case other =>
              // branch not necessary, but Scala's compiler emits warnings if missing
              s.reportFailure(new MatchError(other.toString))
          }
      }
      source
    }

    /**
     * Unsafe version of `onComplete` that triggers execution synchronously
     * in case the source is already completed.
     */
    def onCompleteNow(f: Try[Ack] => Unit)(implicit s: Scheduler): Future[Ack] =
      source match {
        case sync if sync.isCompleted =>
          try f(sync.value.get) catch {
            case NonFatal(ex) =>
              s.reportFailure(ex)
          }
          source

        case async =>
          source.onComplete(f)
          source
      }
  }
}
