/*
 * Copyright (c) 2014-2018 by The Monix Project Developers.
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

package monix.tail
package internal

import cats.effect.{Effect, IO}
import cats.syntax.all._
import monix.eval.instances.CatsBaseForTask
import monix.eval.{Callback, Task}
import monix.execution.ExecutionModel.SynchronousExecution
import monix.execution.Scheduler
import monix.execution.atomic.Atomic
import monix.execution.atomic.PaddingStrategy.LeftRight128
import monix.execution.internal.Platform
import monix.execution.internal.collection.ArrayStack
import monix.execution.rstreams.Subscription
import monix.tail.Iterant.{Halt, Last, Next, NextBatch, NextCursor, Scope, Suspend}
import org.reactivestreams.{Publisher, Subscriber}

private[tail] object IterantToReactivePublisher {
  /**
    * Implementation of `Iterant.toReactivePublisher`
    */
  def apply[F[_], A](self: Iterant[F, A])
    (implicit F: Effect[F], ec: Scheduler): Publisher[A] =
    new IterantPublisher[F, A](self)

  private final class IterantPublisher[F[_], A](source: Iterant[F, A])
    (implicit F: Effect[F], ec: Scheduler)
    extends Publisher[A] {

    override def subscribe(out: Subscriber[_ >: A]): Unit = {
      // Reactive Streams requirement
      if (out == null) throw null

      // For dealing with Concat
      val stack = new ArrayStack[F[Iterant[F, A]]]()

      // The `reduceToNext` call evaluates the source Iterant until
      // we hit an element to stream. Useful because we want to stream
      // `onComplete` for empty streams or `onError` for empty streams
      // that complete in error, without the subscriber having to
      // call `request(n)` (since we shouldn't back-pressure end events)
      runAsync(reduceToNext(out, stack)(source), new Callback[Iterant[F, A]] {
        def onSuccess(start: Iterant[F, A]): Unit =
          if (start ne null) {
            val sub: Subscription = new IterantSubscription[F, A](start, out, stack)
            out.onSubscribe(sub)
          }

        def onError(ex: Throwable): Unit =
          out.onError(ex)
      })
    }

    // Evaluates the given `Iterant`, getting rid of any `Suspend`
    // states until we hit one of the states with an element to
    // stream (e.g. `Next`, `NextBatch`, `NextCursor` or `Last`),
    // or until we hit the end of the stream.
    private def reduceToNext(out: Subscriber[_ >: A], stack: ArrayStack[F[Iterant[F, A]]]) =
      new Iterant.Visitor[F, A, F[Iterant[F, A]]] {
        def visit(ref: Next[F, A]): F[Iterant[F, A]] =
          F.pure(ref)
        def visit(ref: NextBatch[F, A]): F[Iterant[F, A]] =
          F.pure(ref)
        def visit(ref: NextCursor[F, A]): F[Iterant[F, A]] =
          F.pure(ref)

        def visit(ref: Suspend[F, A]): F[Iterant[F, A]] =
          ref.rest.flatMap(this)

        def visit(ref: Iterant.Concat[F, A]): F[Iterant[F, A]] = {
          stack.push(ref.rh)
          ref.lh.flatMap(this)
        }

        def visit[S](ref: Scope[F, S, A]): F[Iterant[F, A]] =
          ref.runFold(this)

        def visit(ref: Last[F, A]): F[Iterant[F, A]] =
          F.pure(ref)

        def visit(ref: Halt[F, A]): F[Iterant[F, A]] =
          ref.e match {
            case None =>
              stack.pop() match {
                case null =>
                  out.onSubscribe(Subscription.empty)
                  out.onComplete()
                  F.pure(null)
                case xs =>
                  xs.flatMap(this)
              }
            case Some(e) =>
              out.onSubscribe(Subscription.empty)
              out.onError(e)
              F.pure(null)
          }

        def fail(e: Throwable): F[Iterant[F, A]] =
          F.raiseError(e)
      }

    // Function for starting the run-loop of `F[_]`. This is a small
    // optimization, to avoid going through `Effect` for `Task` and
    // `IO` and thus avoid some boxing.
    private val runAsync: (F[Iterant[F, A]], Callback[Iterant[F, A]]) => Unit = {
      F.asInstanceOf[Any] match {
        case _: CatsBaseForTask =>
          (fa, cb) => fa.asInstanceOf[Task[Iterant[F, A]]].runAsync(cb)
        case IO.ioEffect =>
          (fa, cb) => fa.asInstanceOf[IO[Iterant[F, A]]].unsafeRunAsync(r => cb(r))
        case _ =>
          val cb = Callback.empty[Unit]
          val ecb = (e: Either[Throwable, Unit]) => cb(e)
          (fa, cb) => F.runAsync(fa) { r => cb(r); IO.unit }.unsafeRunAsync(ecb)
      }
    }
  }

  private final class IterantSubscription[F[_], A](
    source: Iterant[F, A],
    out: Subscriber[_ >: A],
    stack: ArrayStack[F[Iterant[F, A]]])
    (implicit F: Effect[F], ec: Scheduler)
    extends Subscription { subscription =>

    // Keeps track of the currently requested items
    private[this] val requested = Atomic.withPadding(0L, LeftRight128)

    // Reference to what remains to be processed on the next
    // `Subscription.request(n)` call, being fed in the `loop`.
    //
    // MUST BE set before `requested` gets decremented!
    // (happens-before relationship for addressing multi-threading)
    private[this] var cursor: F[Iterant[F, A]] = F.pure(source)

    // For as long as this reference is `null`, the stream is still
    // active (i.e. it wasn't cancelled by means of the `Subscription`).
    // Otherwise it can contain a `Throwable` reference that can be
    // signaled downstream, when the change is observed in the `loop`.
    //
    // MUST BE set before `requested` gets incremented!
    // (happens-before relationship for addressing multi-threading)
    private[this] var concurrentEndSignal: Option[Throwable] = _

    // Function for starting the run-loop of `F[_]`. This is a small
    // optimization, to avoid going through `Effect` for `Task` and
    // `IO` and thus avoid some boxing.
    private val runAsync: F[Unit] => Unit = {
      F.asInstanceOf[Any] match {
        case IO.ioEffect =>
          val cb = Callback.empty[Unit]
          val ecb: Either[Throwable, Unit] => Unit = r => cb(r)
          fa => fa.asInstanceOf[IO[Unit]].unsafeRunAsync(ecb)

        case _: CatsBaseForTask =>
          val cb = Callback.empty[Unit]
          fa => fa.asInstanceOf[Task[Unit]].runAsync(cb)

        case _ =>
          val cb = Callback.empty[Unit]
          val ecbIO: Either[Throwable, Unit] => IO[Unit] = r => { cb(r); IO.unit }
          val ecb: Either[Throwable, Unit] => Unit = r => cb(r)
          fa => F.runAsync(fa)(ecbIO).unsafeRunAsync(ecb)
      }
    }

    private[this] val runLoop = new Loop

    private final class Loop extends Iterant.Visitor[F, A, F[Unit]] {
      private[this] var requested: Long = 0
      private[this] var processed: Int = 0
      private[this] var streamErrors = true
      private[this] val cancel = new RuntimeException("loop cancelled")

      def start(requested: Long): this.type = {
        this.requested = requested
        this.processed = 0
        this
      }

      private def checkCancelSignal(): Unit = {
        if (concurrentEndSignal ne null)
          throw cancel
      }

      def visit(ref: Next[F, A]): F[Unit] = {
        checkCancelSignal()
        out.onNext(ref.item)
        goNext(ref.rest, 1)
      }

      def visit(ref: NextBatch[F, A]): F[Unit] = {
        checkCancelSignal()
        processCursor(ref.toNextCursor())
      }

      def visit(ref: NextCursor[F, A]): F[Unit] = {
        checkCancelSignal()
        processCursor(ref)
      }

      def visit(ref: Suspend[F, A]): F[Unit] = {
        checkCancelSignal()
        goNext(ref.rest, 0)
      }

      def visit(ref: Iterant.Concat[F, A]): F[Unit] = {
        checkCancelSignal()
        stack.push(ref.rh)
        goNext(ref.lh, 0)
      }

      def visit[S](ref: Scope[F, S, A]): F[Unit] = {
        checkCancelSignal()
        ref.runFold(this)
      }

      def visit(ref: Last[F, A]): F[Unit] = {
        checkCancelSignal()
        out.onNext(ref.item)
        stack.pop() match {
          case null =>
            streamErrors = false
            out.onComplete()
            F.unit
          case xs =>
            goNext(xs, 1)
        }
      }

      def visit(ref: Halt[F, A]): F[Unit] = {
        checkCancelSignal()
        ref.e match {
          case None =>
            stack.pop() match {
              case null =>
                streamErrors = false
                out.onComplete()
                F.unit
              case xs =>
                goNext(xs, 0)
            }
          case Some(e) =>
            streamErrors = false
            out.onError(e)
            F.unit
        }
      }

      def fail(e: Throwable): F[Unit] =
        e match {
          case `cancel` =>
            val cancelSignal = concurrentEndSignal
            // Subscription was cancelled, triggering early stop
            cursor = F.pure(Halt(cancelSignal))
            subscription.requested.set(0)

            cancelSignal match {
              case None => F.unit
              case Some(e2) =>
                F.delay(out.onError(e2))
            }
          case _ =>
            F.delay {
              if (streamErrors) out.onError(e)
              else ec.reportFailure(e)
            }
        }

      private def goNext(rest: F[Iterant[F, A]], processedNow: Int): F[Unit] = {
        val processed = this.processed + processedNow

        // Fast-path, avoids doing any volatile operations
        val isInfinite = requested == Long.MaxValue
        if (isInfinite || processed < requested) {
          this.processed = if (!isInfinite) processed else 0
          rest.flatMap(this)
        } else {
          // Happens-before relationship with the `requested` decrement!
          cursor = rest
          // Remaining items to process
          val n2 = subscription.requested.decrementAndGet(processed)

          // In case of zero, the loop needs to stop
          // due to no more requests from downstream
          if (n2 > 0) {
            this.requested = n2
            this.processed = 0
            rest.flatMap(this)
          } else {
            F.unit // pauses loop until next request
          }
        }
      }

      private def processCursor(source: NextCursor[F, A]): F[Unit] = {
        val NextCursor(ref, rest) = source
        val toTake = math.min(ref.recommendedBatchSize, requested - processed).toInt
        val modulus = math.min(ec.executionModel.batchedExecutionModulus, Platform.recommendedBatchSize - 1)
        var isActive = true
        var i = 0

        while (isActive && i < toTake && ref.hasNext()) {
          val a = ref.next()
          i += 1
          out.onNext(a)

          // Check if still active, but in batches, because reading
          // from this shared variable can be an expensive operation
          if ((i & modulus) == 0)
            isActive = subscription.concurrentEndSignal ne null
        }

        val next = if (ref.hasNext()) F.pure(source : Iterant[F, A]) else rest
        goNext(next, i)
      }
    }

    private val startLoop: (Long => Unit) =
      ec.executionModel match {
        case SynchronousExecution =>
          n => ec.executeTrampolined(() => runAsync(cursor.flatMap(runLoop.start(n))))
        case _ =>
          n => ec.executeAsync(() => runAsync(cursor.flatMap(runLoop.start(n))))
      }

    def request(n: Long): Unit = {
      if (n <= 0) {
        cancelWithSignal(Some(
          new IllegalArgumentException(
            "n must be strictly positive, according to " +
            "the Reactive Streams contract, rule 3.9"
          )))
      } else {
        // Incrementing the current request count w/ overflow check
        val prev = requested.getAndTransform { nr =>
          val n2 = nr + n
          // Checking for overflow
          if (nr > 0 && n2 < 0) Long.MaxValue else n2
        }
        // Guard against starting a concurrent loop
        if (prev == 0) {
          startLoop(n)
        }
      }
    }

    def cancel(): Unit = {
      cancelWithSignal(None)
    }

    private def cancelWithSignal(e: Option[Throwable]): Unit = {
      // Must be set before incrementing `requests` in `startLoop`
      // (happens-before relationship)
      concurrentEndSignal = e

      // Faking a `request(1)` is fine because we check the
      // `concurrentEndSignal` after we notice that we have
      // new requests (the combo of `goNext` + `loop`)
      request(1)
    }
  }
}
