/*
 * Copyright (c) 2014-2021 by The Monix Project Developers.
 * See the project homepage at: https://monix.io
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

package monix.tail.internal

import cats.effect.Effect
import cats.implicits._
import monix.execution.UncaughtExceptionReporter.{default => Logger}
import monix.execution.atomic.Atomic
import monix.execution.atomic.PaddingStrategy.LeftRight128
import monix.execution.cancelables.SingleAssignCancelable
import monix.execution.internal.{AttemptCallback, Platform}
import monix.execution.internal.collection.ChunkedArrayStack
import monix.execution.rstreams.Subscription
import monix.execution.{Cancelable, UncaughtExceptionReporter}
import monix.tail.Iterant
import monix.tail.Iterant.Halt
import org.reactivestreams.{Publisher, Subscriber}

import scala.annotation.tailrec
import scala.util.control.NonFatal

private[tail] object IterantToReactivePublisher {
  /**
    * Implementation for `toReactivePublisher`
    */
  def apply[F[_], A](self: Iterant[F, A])(implicit F: Effect[F]): Publisher[A] = {

    new IterantPublisher(self)
  }

  private final class IterantPublisher[F[_], A](source: Iterant[F, A])(implicit F: Effect[F]) extends Publisher[A] {

    def subscribe(out: Subscriber[_ >: A]): Unit = {
      // Reactive Streams requirement
      if (out == null) throw null

      source match {
        case Halt(e) =>
          e match {
            case None =>
              out.onSubscribe(Subscription.empty)
              out.onComplete()
            case Some(err) =>
              out.onSubscribe(Subscription.empty)
              out.onError(err)
          }
        case _ =>
          out.onSubscribe(new IterantSubscription[F, A](source, out))
      }
    }
  }

  private final class IterantSubscription[F[_], A](source: Iterant[F, A], out: Subscriber[_ >: A])(implicit
    F: Effect[F])
    extends Subscription { parent =>

    private[this] val cancelable =
      SingleAssignCancelable()
    private[this] val state =
      Atomic.withPadding(null: RequestState, LeftRight128)

    def request(n: Long): Unit = {
      // Tail-recursive function modifying `requested`
      @tailrec def loop(n: Long): Unit =
        state.get() match {
          case null =>
            if (!state.compareAndSet(null, Request(n)))
              loop(n)
            else
              startLoop()

          case current @ Request(nr) =>
            val n2 = nr + n
            // Checking for overflow
            val update = if (nr > 0 && n2 < 0) Long.MaxValue else n2
            if (!state.compareAndSet(current, Request(update)))
              loop(n)

          case current @ Await(cb) =>
            if (!state.compareAndSet(current, Request(n)))
              loop(n)
            else
              cb(Right(()))

          case Interrupt(_) =>
            () // do nothing
        }

      if (n <= 0) {
        cancelWithSignal(
          Some(
            new IllegalArgumentException(
              "n must be strictly positive, according to " +
                "the Reactive Streams contract, rule 3.9"
            )))
      } else {
        loop(n)
      }
    }

    def cancel(): Unit =
      cancelWithSignal(None)

    @tailrec def cancelWithSignal(signal: Option[Throwable]): Unit = {
      state.get() match {
        case current @ (null | Request(_)) =>
          if (!state.compareAndSet(current, Interrupt(signal)))
            cancelWithSignal(signal)
          else {
            if (signal == None)
              cancelable.cancel()
            if (current == null)
              startLoop()
          }

        case Interrupt(_) =>
          signal.foreach(Logger.reportFailure)

        case current @ Await(cb) =>
          if (!state.compareAndSet(current, Interrupt(signal)))
            cancelWithSignal(signal)
          else {
            if (signal == None) cancelable.cancel()
            cb(rightUnit)
          }
      }
    }

    def startLoop(): Unit = {
      val loop = new Loop
      val token = F
        .toIO(loop(source))
        .unsafeRunCancelable({
          case Left(error) => Logger.reportFailure(error)
          case _ => ()
        })
      cancelable := Cancelable(() =>
        token.unsafeRunAsync(
          AttemptCallback.empty(UncaughtExceptionReporter.default)
        ))
      ()
    }

    private final class Loop extends Iterant.Visitor[F, A, F[Unit]] {
      private[this] var requested = 0L
      private[this] var haltSignal = Option.empty[Option[Throwable]]
      private[this] var streamErrors = true

      // -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=
      // Used in visit(Concat)
      private[this] var _stack: ChunkedArrayStack[F[Iterant[F, A]]] = _

      private def stackPush(item: F[Iterant[F, A]]): Unit = {
        if (_stack == null) _stack = ChunkedArrayStack()
        _stack.push(item)
      }

      private def stackPop(): F[Iterant[F, A]] = {
        if (_stack != null) _stack.pop()
        else null.asInstanceOf[F[Iterant[F, A]]]
      }

      private def isStackEmpty(): Boolean =
        _stack == null || _stack.isEmpty

      private[this] val concatContinue: (Unit => F[Unit]) =
        state =>
          stackPop() match {
            case null => F.pure(state)
            case xs => xs.flatMap(this)
          }
      // -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=

      private def poll(cb: Either[Throwable, Unit] => Unit = null): F[Unit] = {
        val ref = parent.state
        var result = F.unit
        var continue = true
        requested = 0

        while (continue) {
          continue = false

          ref.get() match {
            case current @ Request(n) =>
              if (n > 0) {
                if (n < Long.MaxValue) {
                  // Processing requests in batches
                  val toProcess = math.min(Platform.recommendedBatchSize.toLong, n)
                  val update = n - toProcess
                  if (!parent.state.compareAndSet(current, Request(update)))
                    continue = true
                  else {
                    requested = toProcess
                    if (cb ne null) cb(rightUnit)
                  }
                } else {
                  requested = Platform.recommendedBatchSize.toLong
                  if (cb ne null) cb(rightUnit)
                }
              } else if (cb ne null) {
                continue = !parent.state.compareAndSet(current, Await(cb))
              } else {
                result = F.asyncF(poll)
              }

            case Interrupt(signal) =>
              haltSignal = Some(signal)
              if (cb ne null) cb(rightUnit)

            case Await(_) =>
              throw new IllegalStateException("Await in pool")
          }
        }
        result
      }

      def visit(ref: Iterant.Next[F, A]): F[Unit] = {
        requested -= 1
        out.onNext(ref.item)
        ref.rest.flatMap(this)
      }

      def visit(ref: Iterant.NextBatch[F, A]): F[Unit] =
        visit(ref.toNextCursor())

      def visit(ref: Iterant.NextCursor[F, A]): F[Unit] = {
        val cursor = ref.cursor
        while (requested > 0 && cursor.hasNext()) {
          requested -= 1
          out.onNext(cursor.next())
        }

        if (cursor.hasNext())
          F.pure(ref).flatMap(this)
        else
          ref.rest.flatMap(this)
      }

      def visit(ref: Iterant.Suspend[F, A]): F[Unit] =
        ref.rest.flatMap(this)

      def visit(ref: Iterant.Concat[F, A]): F[Unit] = {
        stackPush(ref.rh)
        ref.lh.flatMap(this).flatMap(concatContinue)
      }

      def visit[S](ref: Iterant.Scope[F, S, A]): F[Unit] =
        ref.runFold(this)

      def visit(ref: Iterant.Last[F, A]): F[Unit] = {
        requested -= 1
        out.onNext(ref.item)
        if (isStackEmpty()) {
          streamErrors = false
          out.onComplete()
        }
        F.unit
      }

      def visit(ref: Iterant.Halt[F, A]): F[Unit] =
        ref.e match {
          case Some(e) =>
            streamErrors = false
            _stack = null
            out.onError(e)
            F.unit
          case None =>
            if (isStackEmpty()) {
              streamErrors = false
              out.onComplete()
            }
            F.unit
        }

      def fail(e: Throwable): F[Unit] =
        F.delay {
          if (streamErrors) {
            streamErrors = false
            out.onError(e)
          } else {
            Logger.reportFailure(e)
          }
        }

      private def process(fa: Iterant[F, A]): F[Unit] = {
        try {
          super.apply(fa)
        } catch {
          case e if NonFatal(e) =>
            if (streamErrors) F.delay(out.onError(e))
            else F.delay(Logger.reportFailure(e))
        }
      }

      override def apply(fa: Iterant[F, A]): F[Unit] = {
        if (requested > 0)
          process(fa)
        else {
          suspendedRef = fa
          poll().flatMap(afterPoll)
        }
      }

      private[this] var suspendedRef: Iterant[F, A] = _
      private[this] val afterPoll: Unit => F[Unit] = _ => {
        haltSignal match {
          case None =>
            if (requested == 0)
              poll().flatMap(afterPoll)
            else
              process(suspendedRef)
          case Some(None) =>
            F.unit
          case Some(Some(e)) =>
            F.delay(out.onError(e))
        }
      }
    }
  }

  private sealed abstract class RequestState

  private final case class Request(n: Long) extends RequestState

  private final case class Await(cb: Either[Throwable, Unit] => Unit) extends RequestState

  private final case class Interrupt(err: Option[Throwable]) extends RequestState

  private[this] val rightUnit = Right(())
}
