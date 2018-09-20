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

package monix.eval
package internal


/** Set of options for customizing the task's behavior.
  *
  * @param autoCancelableRunLoops should be set to `true` in
  *        case you want `flatMap` driven loops to be
  *        auto-cancelable. Defaults to `false`.
  *
  * @param localContextPropagation should be set to `true` in
  *        case you want the [[monix.execution.misc.Local Local]]
  *        variables to be propagated on async boundaries.
  *        Defaults to `false`.
  */
private[eval] final case class TaskOptions(
  autoCancelableRunLoops: Boolean,
  localContextPropagation: Boolean) {

  /** Creates a new set of options from the source, but with
    * the [[autoCancelableRunLoops]] value set to `true`.
    */
  def enableAutoCancelableRunLoops: TaskOptions =
    copy(autoCancelableRunLoops = true)

  /** Creates a new set of options from the source, but with
    * the [[autoCancelableRunLoops]] value set to `false`.
    */
  def disableAutoCancelableRunLoops: TaskOptions =
    copy(autoCancelableRunLoops = false)

  /** Creates a new set of options from the source, but with
    * the [[localContextPropagation]] value set to `true`.
    */
  def enableLocalContextPropagation: TaskOptions =
    copy(localContextPropagation = true)

  /** Creates a new set of options from the source, but with
    * the [[localContextPropagation]] value set to `false`.
    */
  def disableLocalContextPropagation: TaskOptions =
    copy(localContextPropagation = false)
}

private[eval] object TaskOptions {
  /**
    * Default [[TaskOptions]].
    */
  val default = TaskOptions(
    autoCancelableRunLoops = true,
    localContextPropagation = false
  )
}
