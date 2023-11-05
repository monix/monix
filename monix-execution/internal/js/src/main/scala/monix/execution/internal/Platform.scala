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

package monix.execution.internal

import scala.scalajs.js
import scala.util.control.NonFatal
import scala.annotation.unused

private[monix] object Platform {
  /**
    * Returns `true` in case Monix is running on top of Scala.js,
    * or `false` otherwise.
    */
  final val isJS = true

  /**
    * Returns `true` in case Monix is running on top of the JVM,
    * or `false` otherwise.
    */
  final val isJVM = false

  /**
    * Reads environment variable in a platform-specific way.
    */
  def getEnv(key: String): Option[String] = {
    import js.Dynamic.global
    try {
      // Node.js specific API, could fail
      if (js.typeOf(global.process) == "object" && js.typeOf(global.process.env) == "object")
        global.process.env.selectDynamic(key).asInstanceOf[js.UndefOr[String]]
          .toOption
          .collect { case s: String => s.trim }
          .filter(_.nonEmpty)
      else
        None
    } catch {
      case NonFatal(_) => None
    }
  }

  /**
    * Reads a Monix system property.
    * 
    * For JavaScript engines, this can only be read on top of Node.js,
    * and only from environment values (since Node.js doesn't have 
    * Java's system properties).
    */
  def getMonixSystemProperty(
    @unused propertyKey: String,
    orEnvironmentKey: String,
  ): Option[String] =
    getEnv(orEnvironmentKey)
}
