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

package monix.execution.internal;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.CLASS;

/**
 * Marks APIs that are considered internal to Monix and may change at
 * any point in time without any warning.
 *
 * For example, this annotation should be used when the Scala
 * `private[monix]` access restriction is used, as Java has no way of
 * representing this package restricted access and such methods and
 * classes are represented as public in byte-code.
 *
 * If a method/class annotated with this method has a javadoc/scaladoc
 * comment, the first line MUST include INTERNAL API in order to be
 * easily identifiable from generated documentation. Additional
 * information may be put on the same line as the INTERNAL API comment
 * in order to clarify further.
 *
 * Copied from the [[https://akka.io/ Akka project]].
 */
@Documented
@Retention(value=CLASS)
@Target(value={METHOD,CONSTRUCTOR,FIELD,TYPE,PACKAGE})
public @interface InternalApi {}
