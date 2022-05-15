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

import cats.effect.Async
import monix.catnap.ConsumerF
import monix.tail.Iterant
import monix.tail.Iterant.{haltS, suspendS, Next, NextBatch}
import monix.tail.batches.Batch

private[tail] object IterantFromConsumer {
  /**
    * Implementation for [[Iterant.fromConsumer]].
    */
  def apply[F[_], A](consumer: ConsumerF[F, Option[Throwable], A], maxBatchSize: Int)(implicit
    F: Async[F]): Iterant[F, A] = {

    suspendS(
      if (maxBatchSize > 1)
        loopMany(consumer, maxBatchSize)
      else
        loopOne(consumer)
    )
  }

  private def loopOne[F[_], A](consumer: ConsumerF[F, Option[Throwable], A])(implicit F: Async[F]): F[Iterant[F, A]] = {

    F.map(consumer.pull) {
      case Left(e) => haltS(e)
      case Right(a) => Next(a, loopOne(consumer))
    }
  }

  private def loopMany[F[_], A](consumer: ConsumerF[F, Option[Throwable], A], maxBatchSize: Int)(implicit
    F: Async[F]): F[Iterant[F, A]] = {

    F.map(consumer.pullMany(1, maxBatchSize)) {
      case Left(e) => haltS(e)
      case Right(seq) =>
        NextBatch(Batch.fromSeq(seq), loopMany(consumer, maxBatchSize))
    }
  }
}
