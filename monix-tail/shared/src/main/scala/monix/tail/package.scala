/*
 * Copyright (c) 2014-2017 by its authors. Some rights reserved.
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

package monix

import cats.Applicative

/** The `monix.tail` batches exposes the [[monix.tail.Iterant Iterant]]
  * type, a pull-based streaming abstraction that can work lazily
  * and over asynchronous boundaries.
  */
package object tail {
  /** Missing utils for `cats.Applicative`. */
  private[tail] implicit class ApplicativeUtils[F[_]](val self: Applicative[F])
    extends AnyVal {

    /** Shortcut for `Applicative.pure(())`. */
    def unit: F[Unit] = self.pure(())
  }
}