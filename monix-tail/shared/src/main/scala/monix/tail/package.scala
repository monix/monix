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

import monix.eval.{Coeval, Task}

package object tail {
  /** The `AsyncStream[+A]` type is a type alias for
    * `Iterant[Task,A]`, thus using [[monix.eval.Task Task]] for
    * controlling the evaluation.
    *
    * @see the defined builders and the description on the
    *      [[AsyncStream$ AsyncStream companion]].
    */
  type AsyncStream[+A] = Iterant[Task, A]

  /** The `LazyStream[+A]` type is a type alias for
    * `Iterant[Coeval,A]`, thus using [[monix.eval.Coeval Coeval]] for
    * controlling the evaluation.
    *
    * @see the defined builders and the description on the
    *      [[LazyStream$ LazyStream companion]].
    */
  type LazyStream[+A] = Iterant[Coeval, A]
}
