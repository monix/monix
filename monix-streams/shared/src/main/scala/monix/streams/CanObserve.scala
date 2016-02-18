/*
 * Copyright (c) 2014-2016 by its authors. Some rights reserved.
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

package monix.streams

import java.util.concurrent.Callable
import monix.streams.internal.builders._
import monix.tasks.Task
import simulacrum.typeclass
import org.reactivestreams.{Publisher => RPublisher}
import scala.concurrent.Future
import scala.language.higherKinds
import scala.language.implicitConversions

@typeclass trait CanObserve[-F[_]] {
  def observable[A](fa: F[A]): Observable[A]
}

private[streams] trait CanObserveLevel0 {
  /** Default conversion for `Iterator` */
  implicit final val iterator: CanObserve[Iterator] =
    new CanObserve[Iterator] {
      def observable[A](fa: Iterator[A]): Observable[A] =
        new IteratorAsObservable[A](fa)
    }
}

private[streams] trait CanObserveLevel1 extends CanObserveLevel0 {
  /** Default conversion for `Iterable` */
  implicit final val iterableInstance: CanObserve[Iterable] =
    new CanObserve[Iterable] {
      def observable[A](fa: Iterable[A]): Observable[A] =
        new IterableAsObservable[A](fa)
    }
}

private[streams] trait CanObserveLevel2 extends CanObserveLevel1 {
  /** Default conversion for `org.reactivestreams.Publisher` */
  implicit final val reactivePublisherInstance: CanObserve[RPublisher] =
    new CanObserve[RPublisher] {
      def observable[A](fa: RPublisher[A]): Observable[A] =
        new ReactiveObservable[A](fa)
    }

  /** Default conversion for `Future` */
  implicit final val futureInstance: CanObserve[Future] =
    new CanObserve[Future] {
      def observable[A](fa: Future[A]): Observable[A] =
        new FutureAsObservable(fa)
    }

  /** Default conversion for `Callable` */
  implicit final val callableInstance: CanObserve[Callable] =
    new CanObserve[Callable] {
      def observable[A](fa: Callable[A]): Observable[A] =
        new CallableAsObservable[A](fa)
    }
}

object CanObserve extends CanObserveLevel2 {
  /** Default conversion for `Observable`.
    * Obviously this will be the identity function.
    */
  implicit final val observableInstance: CanObserve[Observable] =
    new CanObserve[Observable] {
      def observable[A](fa: Observable[A]): Observable[A] = fa
    }

  /** Default conversion for `Task` */
  implicit final val taskInstance: CanObserve[Task] =
    new CanObserve[Task] {
      def observable[A](fa: Task[A]): Observable[A] =
        new TaskAsObservable(fa)
    }
}
