package monifu.concurrent.atomic.padded

trait Atomic[T] extends monifu.concurrent.atomic.Atomic[T]

object Atomic {
  /**
   * Constructs an `Atomic[T]` reference. Based on the `initialValue`, it will return the best, most specific
   * type. E.g. you give it a number, it will return something inheriting from `AtomicNumber[T]`. That's why
   * it takes an `AtomicBuilder[T, R]` as an implicit parameter - but worry not about such details as it just works.
   *
   * @param initialValue is the initial value with which to initialize the Atomic reference
   * @param builder is the builder that helps us to build the best reference possible, based on our `initialValue`
   */
  def apply[T, R <: Atomic[T]](initialValue: T)(implicit builder: PaddedAtomicBuilder[T, R]): R =
    builder.buildInstance(initialValue)

  /**
   * Returns the builder that would be chosen to construct Atomic references
   * for the given `initialValue`.
   */
  def builderFor[T, R <: Atomic[T]](initialValue: T)(implicit builder: PaddedAtomicBuilder[T, R]): PaddedAtomicBuilder[T, R] =
    builder
}