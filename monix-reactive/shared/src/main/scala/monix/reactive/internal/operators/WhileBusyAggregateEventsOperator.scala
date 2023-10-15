/*
 * Copyright (c) 2014-2022 Monix Contributors.
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

package monix.reactive.internal.operators

import monix.execution.Ack
import monix.execution.Ack.{ Continue, Stop }
import monix.execution.Scheduler
import monix.reactive.Observable.Operator
import monix.reactive.observers.Subscriber

import scala.concurrent.Future
import scala.util.{ Failure, Success }
import scala.util.control.NonFatal

private[reactive] final class WhileBusyAggregateEventsOperator[A, S](seed: A => S, aggregate: (S, A) => S)
  extends Operator[A, S] {

  def apply(downstream: Subscriber[S]): Subscriber.Sync[A] = {
    new Subscriber.Sync[A] {
      upstreamSubscriber =>
      implicit val scheduler: Scheduler = downstream.scheduler

      private[this] var aggregated: Option[S] = None
      private[this] var lastAck: Future[Ack] = Continue
      private[this] var pendingAck: Boolean = false
      private[this] var downstreamIsDone = false

      override def onNext(elem: A): Ack = {
        upstreamSubscriber.synchronized {
          if (downstreamIsDone) Stop
          else {
            if (!pendingAck) {
              val downstreamAck =
                try {
                  downstream.onNext(seed(elem))
                } catch {
                  case ex if NonFatal(ex) =>
                    downstream.onError(ex)
                    Stop
                }
              lastAck = downstreamAck

              if (downstreamAck == Continue) Continue
              else if (downstreamAck == Stop) {
                downstreamIsDone = true
                Stop
              } else {
                pendingAck = true
                emitAggregatedOnAckContinue(downstreamAck)
                Continue
              }
            } else {
              try {
                aggregated = Some(aggregated match {
                  case Some(agg) => aggregate(agg, elem)
                  case None => seed(elem)
                })
                Continue
              } catch {
                case ex if NonFatal(ex) =>
                  downstream.onError(ex)
                  Stop
              }
            }
          }
        }
      }

      override def onError(ex: Throwable): Unit =
        upstreamSubscriber.synchronized {
          if (!downstreamIsDone) {
            downstreamIsDone = true
            downstream.onError(ex)
          }
        }

      override def onComplete(): Unit =
        // Can't emit the aggregated element immediately as the previous `onNext` may not yet have been acked.
        upstreamSubscriber.synchronized {
          lastAck.map {
            case Continue =>
              upstreamSubscriber.synchronized {
                emitAggregated()
                downstream.onComplete()
              }
            case _ =>
          }
          ()
        }

      private def emitAggregated(): Unit = {
        aggregated match {
          case Some(agg) =>
            aggregated = None
            if (!downstreamIsDone) {
              lastAck = downstream.onNext(agg)
              emitAggregatedOnAckContinue(lastAck)
            }
            ()
          case None =>
            pendingAck = false
        }
      }

      private def emitAggregatedOnAckContinue(ack: Future[Ack]): Unit =
        ack.onComplete {
          case Failure(ex) =>
            onError(ex)
          case Success(Stop) =>
            downstreamIsDone = true
          case Success(Continue) =>
            upstreamSubscriber.synchronized { emitAggregated() }
        }
    }
  }
}
