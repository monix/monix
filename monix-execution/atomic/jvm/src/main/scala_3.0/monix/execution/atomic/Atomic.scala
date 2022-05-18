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

package monix.execution.atomic

/**
  * Base trait of all atomic references, no matter the type.
  */
abstract class Atomic[A] extends Serializable with internal.AtomicDocs {
  /** $atomicGetDesc */
  def get(): A

  /** $atomicSetDesc */
  def set(update: A): Unit

  /** $compareAndSetDesc
    * 
    * $atomicBestPractices 
    *
    * @param expect $atomicCASExpectParam
    * @param update $atomicCASUpdateParam
    * @return $atomicCASReturn $atomicCASReturn
    */
  def compareAndSet(expect: A, update: A): Boolean

  /** $atomicGetAndSetDesc 
    * 
    * @param update $atomicGetAndSetParam
    * @return $atomicGetAndSetReturn
    */
  def getAndSet(update: A): A

  /** $atomicLazySetDesc */
  def lazySet(update: A): Unit

  /** $atomicTransformExtractDesc
    *
    * $atomicTransformBestPractices 
    *
    * @param f $atomicTransformExtractParamF
    * 
    * @return $atomicTransformExtractReturn   
    */
  inline final def transformAndExtract[U](inline f: A => (U, A)): U = {
    var current = get()
    var result = null.asInstanceOf[U]
    var continue = true

    while (continue) {
      val (resultTmp, update) = f(current)
      if (compareAndSet(current, update)) {
        result = resultTmp
        continue = false
      } else {
        current = get()
      }
    }
    result
  }

  /** $atomicTransformAndGetDesc
    * 
    * $atomicTransformBestPractices 
    *
    * @param f $atomicTransformParam
    * 
    * @return $atomicTransformAndGetReturn
    */
  inline final def transformAndGet(inline cb: (A) => A): A = {
    var current = get()
    var update = null.asInstanceOf[A]
    var continue = true

    while (continue) {
      update = cb(current)
      if (compareAndSet(current, update))
        continue = false
      else
        current = get()
    }
    update
  }

  /** $atomicGetAndTransformDesc
    *
    * $atomicTransformBestPractices 
    *
    * @param f $atomicTransformParam
    * 
    * @return $atomicGetAndTransformReturn
    */
  inline final def getAndTransform(inline f: A => A): A = {
    var current = get()
    var continue = true

    while (continue) {
      val update = f(current)
      if (compareAndSet(current, update))
        continue = false
      else
        current = get()
    }
    current
  }

  /** $atomicTransformDesc
    *
    * $atomicTransformBestPractices 
    * 
    * @param f $atomicTransformParam
    */
  inline final def transform(inline f: A => A): Unit = {
    var continue = true

    while (continue) {
      val current = get()
      val update = f(current)
      continue = !compareAndSet(current, update)
    }
  }
}

object Atomic {
  /** Constructs an `Atomic[A]` reference.
    *
    * Based on the `initialValue`, it will return the best, most
    * specific type. E.g. you give it a number, it will return
    * something inheriting from `AtomicNumber[A]`. That's why it takes
    * an `AtomicBuilder[T, R]` as an implicit parameter - but worry
    * not about such details as it just works.
    *
    * @param initialValue is the initial value with which to
    *        initialize the Atomic reference
    *
    * @param builder is the builder that helps us to build the
    *        best reference possible, based on our `initialValue`
    */
  inline def apply[A, R <: Atomic[A]](initialValue: A)(implicit builder: AtomicBuilder[A, R]): R =
    builder.buildInstance(initialValue, PaddingStrategy.NoPadding, allowPlatformIntrinsics = true)

  /** Constructs an `Atomic[A]` reference, applying the provided
    * [[PaddingStrategy]] in order to counter the "false sharing"
    * problem.
    *
    * Based on the `initialValue`, it will return the best, most
    * specific type. E.g. you give it a number, it will return
    * something inheriting from `AtomicNumber[A]`. That's why it takes
    * an `AtomicBuilder[A, R]` as an implicit parameter - but worry
    * not about such details as it just works.
    *
    * Note that for ''Scala.js'' we aren't applying any padding, as it
    * doesn't make much sense, since Javascript execution is single
    * threaded, but this builder is provided for syntax compatibility
    * anyway across the JVM and Javascript and we never know how
    * Javascript engines will evolve.
    *
    * @param initialValue is the initial value with which to
    *        initialize the Atomic reference
    *
    * @param padding is the [[PaddingStrategy]] to apply
    *
    * @param builder is the builder that helps us to build the
    *        best reference possible, based on our `initialValue`
    */
  inline def withPadding[A, R <: Atomic[A]](initialValue: A, padding: PaddingStrategy)(implicit
    builder: AtomicBuilder[A, R]
  ): R =
    builder.buildInstance(initialValue, padding, allowPlatformIntrinsics = true)

  /** Returns the builder that would be chosen to construct Atomic
    * references for the given `initialValue`.
    */
  def builderFor[A, R <: Atomic[A]](initialValue: A)(implicit builder: AtomicBuilder[A, R]): AtomicBuilder[A, R] =
    builder

  extension [A](self: Atomic[A]) {
    /** DEPRECATED - switch to [[Atomic.get]]. */
    @deprecated("Switch to .get()", "4.0.0")
    def apply(): A = {
      // $COVERAGE-OFF$
      self.get()
      // $COVERAGE-ON$
    }

    /** DEPRECATED — switch to [[Atomic.set]]. */
    @deprecated("Switch to .set()", "4.0.0")
    def update(value: A): Unit = {
      // $COVERAGE-OFF$
      self.set(value)
      // $COVERAGE-ON$
    }

    /** DEPRECATED — switch to [[Atomic.set]]. */
    @deprecated("Switch to .set()", "4.0.0")
    def `:=`(value: A): Unit = {
      // $COVERAGE-OFF$
      self.set(value)
      // $COVERAGE-ON$
    }
  }
}
