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

private[monix] object Platform {
  /**
    * Returns `true` in case Monix is running on top of Scala.js,
    * or `false` otherwise.
    */
  final val isJS = false

  /**
    * Returns `true` in case Monix is running on top of the JVM,
    * or `false` otherwise.
    */
  final val isJVM = true

  /**
    * Reads environment variable in a platform-specific way.
    */
  def getEnv(key: String): Option[String] =
    Option(System.getenv(key)).map(_.trim).filter(_.nonEmpty)

  /**
    * Reads a Monix system property, either from the JVM's system
    * properties, or from environment values (also available on Node.js).
    */
  def getMonixSystemProperty(
    propertyKey: String,
    orEnvironmentKey: String,
  ): Option[String] = {
    Option(System.getProperty(propertyKey, ""))
      .map(_.trim())
      .filter(s => s != null && s.nonEmpty)
      .orElse(getEnv(orEnvironmentKey))
  }
}
