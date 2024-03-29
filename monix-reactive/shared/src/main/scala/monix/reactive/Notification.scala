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

package monix.reactive

/** Used by [[Observable.materialize]]. */
sealed abstract class Notification[+A] extends Serializable

object Notification {
  final case class OnNext[+A](elem: A) extends Notification[A]

  final case class OnError(ex: Throwable) extends Notification[Nothing]

  case object OnComplete extends Notification[Nothing]
}
