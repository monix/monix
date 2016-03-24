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

package monix.laws

import monix.types.MonadConsError
import scala.language.higherKinds

/** Laws for the [[monix.types.MonadConsError MonadConsError]] type-class.
  *
  * See [[monix.laws.discipline.MonadConsErrorTests MonadConsErrorTests]] for a
  * test specification powered by
  * [[https://github.com/typelevel/discipline/ Discipline]].
  */
trait MonadConsErrorLaws[F[_],E] extends MonadConsLaws[F] with RecoverableLaws[F,E] {
  implicit def F: MonadConsError[F,E]
}

object MonadConsErrorLaws {
  def apply[F[_], E](implicit ev: MonadConsError[F,E]): MonadConsErrorLaws[F,E] =
    new MonadConsErrorLaws[F,E] { def F: MonadConsError[F,E] = ev }
}
