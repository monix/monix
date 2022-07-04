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

package monix.execution

import monix.execution.internal.TypeInfoExtractor
import monix.newtypes.TypeInfo
class Properties private (private val attributes: Map[TypeInfo[_], Any]) {

  def get[A: TypeInfo]: Option[A] = attributes.get(implicitly[TypeInfo[A]]) match {
    case Some(o) =>
      // forced asInstanceOf since the only way to insert in attributes is through withProperty
      Some(o.asInstanceOf[A])
    case _ => None
  }

  def getWithDefault[A: TypeInfo](default: A): A = attributes.getOrElse(implicitly[TypeInfo[A]], default)
    .asInstanceOf[A]

  def withProperty[A]: Properties.ApplyBuilder1[A] = new Properties.ApplyBuilder1(this)

  def canEqual(other: Any): Boolean = other.isInstanceOf[Properties]

  override def equals(other: Any): Boolean = other match {
    case that: Properties =>
      that.canEqual(this) &&
      attributes == that.attributes
    case _ => false
  }

  override def hashCode(): Int = {
    val state = Seq(attributes)
    state.map(_.hashCode()).foldLeft(0)((a, b) => 31 * a + b)
  }
}

object Properties {
  val empty: Properties = new Properties(Map())

  def apply[A]: ApplyBuilder1[A] = new ApplyBuilder1[A](empty)

  class ApplyBuilder1[A](val previousProperties: Properties) {}

  implicit class ApplyBuilder1Ext[A](applyBuilder1: ApplyBuilder1[A])(implicit t: TypeInfoExtractor[A]) {
    def apply(value: A): Properties =
      new Properties(
        applyBuilder1.previousProperties.attributes + (t.getTypeInfo -> value)
      )
  }

}
