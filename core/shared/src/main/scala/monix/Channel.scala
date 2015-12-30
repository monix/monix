/*
 * Copyright (c) 2014-2015 by its authors. Some rights reserved.
 * See the project homepage at: http://www.monix.io
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

/**
 * A channel is meant for imperative style feeding of events.
 *
 * When emitting events, one doesn't need to follow the back-pressure contract.
 * On the other hand the grammar must still be respected:
 *
 *     (pushNext)* (pushComplete | pushError)
 */
trait Channel[-I] { self =>
  /**
   * Push the given events down the stream.
   */
  def pushNext(elem: I*): Unit

  /**
   * End the stream.
   */
  def pushComplete(): Unit

  /**
   * Ends the stream with an error.
   */
  def pushError(ex: Throwable): Unit
}
