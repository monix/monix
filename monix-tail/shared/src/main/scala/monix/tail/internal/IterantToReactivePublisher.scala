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
import monix.execution.misc.NonFatal
import monix.execution.rstreams.Subscription
import monix.tail.Iterant.{Halt, Last, Next, NextBatch, NextCursor, Suspend}
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

      // The `reduceToNext` call evaluates the source Iterant until
      // we hit an element to stream. Useful because we want to stream
      // `onComplete` for empty streams or `onError` for empty streams
      // that complete in error, without the subscriber having to
      // call `request(n)` (since we shouldn't back-pressure end events)
      runAsync(reduceToNext(out)(source), new Callback[Iterant[F, A]] {
        def onSuccess(start: Iterant[F, A]): Unit =
          if (start ne null) {
            val sub: Subscription = new IterantSubscription[F, A](start, out)
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
    private def reduceToNext(out: Subscriber[_ >: A])(self: Iterant[F, A]): F[Iterant[F, A]] =
      self match {
        case Suspend(rest, _) =>
          rest.flatMap(reduceToNext(out))

        case Halt(opt) =>
          out.onSubscribe(Subscription.empty)
          opt match {
            case None => out.onComplete()
            case Some(e) => out.onError(e)
          }

          // Returning `null` means that we've already signaled an
          // end event and thus there's nothing left to do
          F.pure(null)

        case other =>
          // We know we have an element to stream
          F.pure(other)
      }

    // Function for starting the run-loop of `F[_]`. This is a small
    // optimization, to avoid going through `Effect` for `Task` and
    // `IO` and thus avoid some boxing.
    private val runAsync: (F[Iterant[F, A]], Callback[Iterant[F, A]]) => Unit = {
      import cats.effect.IO.ioEffect

      F.asInstanceOf[Any] match {
        case _: CatsBaseForTask =>
          (fa, cb) => fa.asInstanceOf[Task[Iterant[F, A]]].runAsync(cb)
        case `ioEffect` =>
          (fa, cb) => fa.asInstanceOf[IO[Iterant[F, A]]].unsafeRunAsync(r => cb(r))
        case _ =>
          val cb = Callback.empty[Unit]
          val ecb = (e: Either[Throwable, Unit]) => cb(e)
          (fa, cb) => F.runAsync(fa) { r => cb(r); IO.unit }.unsafeRunAsync(ecb)
      }
    }
  }

  private final class IterantSubscription[F[_], A](source: Iterant[F, A], out: Subscriber[_ >: A])
    (implicit F: Effect[F], ec: Scheduler)
    extends Subscription {

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
      import cats.effect.IO.ioEffect

      F.asInstanceOf[Any] match {
        case `ioEffect` =>
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

    private def goNext(rest: F[Iterant[F, A]], stop: F[Unit], requested: Long, processed: Int): F[Unit] = {
      // Fast-path, avoids doing any volatile operations
      val isInfinite = requested == Long.MaxValue
      if (isInfinite || processed < requested) {
        val n2 = if (!isInfinite) processed else 0
        rest.flatMap(loop(requested, n2))
      } else {
        // Happens-before relationship with the `requested` decrement!
        cursor = rest
        // Remaining items to process
        val n2 = this.requested.decrementAndGet(processed)

        // In case of zero, the loop needs to stop
        // due to no more requests from downstream
        if (n2 > 0)
          rest.flatMap(loop(n2, 0))
        else
          F.unit // pauses loop until next request
      }
    }

    /** Processes the elements of a `BatchCursor`.
      *
      * Invariant: requested > processed!
      */
    private def processCursor(source: NextCursor[F, A], requested: Long, processed: Int): F[Unit] = {
      val NextCursor(ref, rest, stop) = source
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
        if ((i & modulus) == 0) isActive = this.concurrentEndSignal ne null
      }

      val next = if (ref.hasNext()) F.pure(source : Iterant[F, A]) else rest
      goNext(next, stop, requested, processed + i)
    }

    /** Run-loop.
      *
      * Invariant: requested > processed!
      */
    private def loop(requested: Long, processed: Int)(source: Iterant[F, A]): F[Unit] = {
      // Checking `concurrentEndSignal` immediately, as due to the
      // way `cancel()` works, we might end up pushing an extra
      // element downstream, violating the protocol; but this value
      // should be visible due to it being set before incrementing
      // the requested counter (happens-before relationship)
      val cancelSignal = this.concurrentEndSignal

      if (cancelSignal eq null) {
        // Guard for protocol violations. In case we've sent a final
        // `onComplete` or `onError`, then we can no longer stream
        // errors by means of `onError`, since that would violate
        // the protocol.
        var streamErrors = true
        try source match {
          case Next(a, rest, stop) =>
            out.onNext(a)
            goNext(rest, stop, requested, processed + 1)

          case ref @ NextCursor(_, _, _) =>
            processCursor(ref, requested, processed)

          case NextBatch(ref, rest, stop) =>
            processCursor(NextCursor(ref.cursor(), rest, stop), requested, processed)

          case Suspend(rest, stop) =>
            goNext(rest, stop, requested, processed)

          case Last(a) =>
            out.onNext(a)
            streamErrors = false
            out.onComplete()
            F.unit

          case Halt(errorOpt) =>
            streamErrors = false
            errorOpt match {
              case None => out.onComplete()
              case Some(e) => out.onError(e)
            }
            F.unit
        } catch {
          case e if NonFatal(e) =>
            source.earlyStop.map { _ =>
              if (streamErrors) out.onError(e)
              else ec.reportFailure(e)
            }
        }
      } else {
        // Subscription was cancelled, triggering early stop
        cursor = F.pure(Halt(cancelSignal))
        this.requested.set(0)
        val next = source.earlyStop

        cancelSignal match {
          case None => next
          case Some(e) =>
            next.map(_ => out.onError(e))
        }
      }
    }

    private val startLoop: (Long => Unit) =
      ec.executionModel match {
        case SynchronousExecution =>
          n => ec.executeTrampolined(() => runAsync(cursor.flatMap(loop(n, 0))))
        case _ =>
          n => ec.executeAsync(() => runAsync(cursor.flatMap(loop(n, 0))))
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
