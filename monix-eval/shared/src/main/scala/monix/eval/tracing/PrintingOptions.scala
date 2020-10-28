/*
 * Copyright (c) 2014-2020 by The Monix Project Developers.
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

package monix.eval.tracing

/**
  * @param showFullStackTraces Whether or not to show the entire stack trace
  * @param maxStackTraceLines When `showFullStackTraces` is `true`, the maximum number of stack trace
  *                           elements to print
  * @param ignoreStackTraceLines When `showFullStackTraces` is `true`, the number of stack trace elements
  *                              to ignore from the start
  *
  * All Credits to https://github.com/typelevel/cats-effect and https://github.com/RaasAhsan
  */
final case class PrintingOptions private (showFullStackTraces: Boolean,
                                          maxStackTraceLines: Int,
                                          ignoreStackTraceLines: Int) {
  def withShowFullStackTraces(showFullStackTraces: Boolean): PrintingOptions =
    copy(showFullStackTraces = showFullStackTraces)

  def withMaxStackTraceLines(maxStackTraceLines: Int): PrintingOptions =
    copy(maxStackTraceLines = maxStackTraceLines)

  def withIgnoreStackTraceLines(ignoreStackTraceLines: Int): PrintingOptions =
    copy(ignoreStackTraceLines = ignoreStackTraceLines)
}

object PrintingOptions {
  val Default = PrintingOptions(
    showFullStackTraces = false,
    maxStackTraceLines = Int.MaxValue,
    ignoreStackTraceLines = 3 // the number of frames to ignore because of TaskTracing
  )

  def apply(): PrintingOptions = Default
}