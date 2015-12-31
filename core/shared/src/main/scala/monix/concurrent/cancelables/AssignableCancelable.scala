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

package monix.concurrent.cancelables

import monix.concurrent.Cancelable

/** Represents a class of cancelables that can hold
  * an internal reference to another cancelable (and thus
  * has to support the assignment operator).
  *
  * Examples are the [[MultiAssignmentCancelable]] and the
  * [[SingleAssignmentCancelable]].
  *
  * NOTE that on assignment, if this cancelable is already
  * canceled, then no assignment should happen and the update
  * reference should be canceled as well.
  */
trait AssignableCancelable extends BooleanCancelable {
  /** Updates the internal reference of this assignable cancelable
    * to the given value.
    *
    * If this cancelable is already canceled, then `value` is
    * going to be canceled on assigned as well.
    *
    * @return `this`
    */
  def `:=`(value: Cancelable): this.type
}
