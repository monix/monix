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

package monix.types.internal

/** Modeling state for usage with `scan`. */
private[types] sealed trait DistinctState[+S] {
  def state: S
}

private[types] object DistinctState {
  private[types] final case class Wait[+S](state: S) extends DistinctState[S]
  private[types] final case class Emit[+S](state: S) extends DistinctState[S]
}

/** Modeling state for usage with `scan`. */
private[types] sealed trait DistinctByKeyState[+S,+K] {
  def key: K
  def state: S
}

private[types] object DistinctByKeyState {
  private[types] final case class Wait[+S,+K](state: S, key: K) extends DistinctByKeyState[S,K]
  private[types] final case class Emit[+S,+K](state: S, key: K) extends DistinctByKeyState[S,K]
}
