package monifu.concurrent.atomic

/**
 * Atomic classes that are cache-padded for reducing cache contention,
 * until JEP 142 and `@Contended` happens. See:
 *
 * http://mail.openjdk.java.net/pipermail/hotspot-dev/2012-November/007309.html
 */
package object padded {
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
}