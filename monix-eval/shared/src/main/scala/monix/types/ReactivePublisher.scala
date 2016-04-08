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

package monix.types

import monix.execution.Scheduler

/** Type-class for data-sources that can be converted to
  * a reactive publisher from the Reactive Streams specification.
  *
  * See: [[http://www.reactive-streams.org/ reactive-streams.org]]
  */
trait ReactivePublisher[F[_]] {
  /** Convert the instance to a reactive publisher. */
  def toReactivePublisher[A](fa: F[A])(implicit s: Scheduler): org.reactivestreams.Publisher[A]
}

object ReactivePublisher {
  @inline def apply[F[_]](implicit F: ReactivePublisher[F]): ReactivePublisher[F] = F
}