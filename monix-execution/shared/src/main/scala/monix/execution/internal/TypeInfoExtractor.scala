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

import monix.newtypes.TypeInfo

import scala.annotation.implicitAmbiguous

import scala.reflect.ClassTag

trait TypeInfoExtractor[A] {
  def getTypeInfo: TypeInfo[A]
}

object TypeInfoExtractor extends TypeInfoExtractor0 {
  @implicitAmbiguous(
    "You have not specified a type-parameter when calling withProperties."
  )
  implicit val typeInfoExtractorNothing: TypeInfoExtractor[Nothing] =
    new TypeInfoExtractor[Nothing] {
      override def getTypeInfo: TypeInfo[Nothing] =
        throw new RuntimeException(s"Should not use")
    }

  @implicitAmbiguous(
    "You have not specified a type-parameter when calling withProperties."
  )
  implicit val typeInfoExtractorAny: TypeInfoExtractor[Null] =
    new TypeInfoExtractor[Null] {
      override def getTypeInfo: TypeInfo[Null] =
        throw new RuntimeException(s"Should not use")
    }

}

sealed trait TypeInfoExtractor0 { TypeInfoExtractor =>
  implicit def typeInfoExtractor[A](implicit aux: ClassTag[A]): TypeInfoExtractor[A] = new TypeInfoExtractor[A] {
    override def getTypeInfo: TypeInfo[A] = implicitly[TypeInfo[A]]
  }
}
