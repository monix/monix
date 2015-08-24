/*
 * Copyright (c) 2014-2015 by its authors. Some rights reserved.
 * See the project homepage at: http://www.monifu.org
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

package monifu.reactive

/**
 * Used by [[monifu.reactive.Observable.materialize Observable.materialize]].
 */
trait Notification[+T]

object Notification {
  case class OnNext[+T](elem: T) extends Notification[T]

  case class OnError(ex: Throwable) extends Notification[Nothing]

  case object OnComplete extends Notification[Nothing]
}
