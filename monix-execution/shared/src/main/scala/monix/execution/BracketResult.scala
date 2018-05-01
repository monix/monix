/*
 * Copyright (c) 2014-2018 by The Monix Project Developers.
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

package monix.execution

/** An ADT representing the way evaluation of a stream
  * had completed, used in [[Iterant$.bracket]] and [[Observable$.bracket]]
  *
  * The following values of `BracketResult` exist:
  *
  *  - [[BracketResult$.Completed Completed]], for a stream that was fully consumed
  *  - [[BracketResult$.EarlyStop EarlyStop]], for a stream that was partially consumed
  * e.g. by `take` operation
  *  - [[BracketResult$.Error Error]], for a stream which evaluation
  * resulted in an error
  *
  * [[BracketResult]] may be superseded by ADT in cats-effect#113,
  * this method is private until then
  */
private[monix] sealed abstract class BracketResult

private[monix] object BracketResult {
  case object Completed extends BracketResult
  case object EarlyStop extends BracketResult
  final case class Error(e: Throwable) extends BracketResult
}