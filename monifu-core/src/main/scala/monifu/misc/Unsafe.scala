package monifu.misc

import language.experimental.macros
import java.lang.reflect.Modifier

/**
 * The quasi-standard way for Java code to gain access to and use functionality
 * which, when unsupervised, would allow one to break the pointer/type safety
 * of Java.
 */
object Unsafe {
  /**
   * Gets the raw byte offset from the start of an object's memory to
   * the memory used to store the indicated instance field.
   *
   * @param field non-null; the field in question, which must be an instance field
   * @return the offset to the field
   */
  def objectFieldOffset(field: java.lang.reflect.Field): Long = {
    require(!Modifier.isStatic(field.getModifiers()), "valid for instance fields only")
    instance.objectFieldOffset(field)
  }

  /**
   * Gets the offset from the start of an array object's memory to
   * the memory used to store its initial (zeroeth) element.
   *
   * @param clazz non-null; class in question; must be an array class
   * @return the offset to the initial element
   */
  def arrayBaseOffset(clazz: Class[_]): Int = {
    require(clazz.isArray, "valid for array classes only")
    instance.arrayBaseOffset(clazz)
  }

  /**
   * Gets the size of each element of the given array class.
   *
   * @param clazz non-null; class in question; must be an array class
   * @return &gt; 0; the size of each element of the array
   */
  def arrayIndexScale(clazz: Class[_]): Int = {
    require(clazz.isArray, "valid for array classes only")
    instance.arrayIndexScale(clazz)
  }

  /**
   * Performs a compare-and-set operation on an `AnyRef`
   * field (that is, a reference field) within the given object.
   *
   * @param obj non-null; object containing the field
   * @param offset offset to the field within `obj`
   * @param expect expected value of the field
   * @param update new value to store in the field if the contents are as expected
   * @return `true` if the new value was in fact stored, `false` otherwise
   */
  def compareAndSwapObject(obj: AnyRef, offset: Long, expect: AnyRef, update: AnyRef): Boolean =
    instance.compareAndSwapObject(obj, offset, expect, update)

  /**
   * Performs a compare-and-set operation on an `int`
   * field within the given object.
   *
   * @param obj non-null; object containing the field
   * @param offset offset to the field within `obj`
   * @param expect expected value of the field
   * @param update new value to store in the field if the contents are as expected
   * @return `true` if the new value was in fact stored, `false` otherwise
   */
  def compareAndSwapInt(obj: AnyRef, offset: Long, expect: Int, update: Int): Boolean =
    instance.compareAndSwapInt(obj, offset, expect, update)

  /**
   * Performs a compare-and-set operation on an `long`
   * field within the given object.
   *
   * @param obj non-null; object containing the field
   * @param offset offset to the field within `obj`
   * @param expect expected value of the field
   * @param update new value to store in the field if the contents are as expected
   * @return `true` if the new value was in fact stored, `false` otherwise
   */
  def compareAndSwapLong(obj: AnyRef, offset: Long, expect: Long, update: Long): Boolean =
    instance.compareAndSwapLong(obj, offset, expect, update)

  /**
   * Gets a `long` field from the given object.
   *
   * @param obj non-null; object containing the field
   * @param offset offset to the field within `obj`
   * @return the retrieved value
   */
  def getLong(obj: AnyRef, offset: Long): Long =
    instance.getLong(obj, offset)

  /**
   * Stores a `long` field into the given object.
   *
   * @param obj non-null; object containing the field
   * @param offset offset to the field within `obj`
   * @param update the value to store
   */
  def putLong(obj: AnyRef, offset: Long, update: Long): Unit =
    instance.putLong(obj, offset, update)

  /**
   * Gets an object field from the given object.
   *
   * @param obj non-null; object containing the field
   * @param offset offset to the field within `obj`
   * @return the retrieved value
   */
  def getObject(obj: AnyRef, offset: Long): AnyRef =
    instance.getObject(obj, offset)

  /**
   * Stores a `object` field into the given object.
   *
   * @param obj non-null; object containing the field
   * @param offset offset to the field within `obj`
   * @param update the value to store
   */
  def putObject(obj: AnyRef, offset: Long, update: AnyRef): Unit =
    instance.putObject(obj, offset, update)

  /**
   * Lazy set an AnyRef field.
   */
  def putOrderedObject(obj: AnyRef, offset: Long, update: AnyRef): Unit =
    instance.putOrderedObject(obj, offset, update)

  /**
   * Lazy set an Int field.
   */
  def putOrderedInt(obj: AnyRef, offset: Long, update: Int): Unit =
    instance.putOrderedInt(obj, offset, update)

  /**
   * Lazy set a Long field.
   */
  def putOrderedLong(obj: AnyRef, offset: Long, update: Long): Unit =
    instance.putOrderedLong(obj, offset, update)

  /**
   * Gets an `Int` field from the given object,
   * using `volatile` semantics.
   *
   * @param obj non-null; object containing the field
   * @param offset offset to the field within `obj`
   * @return the retrieved value
   */
  def getIntVolatile(obj: AnyRef, offset: Long): Int =
    instance.getIntVolatile(obj, offset)

  /**
   * Gets a `Long` field from the given object,
   * using `volatile` semantics.
   *
   * @param obj non-null; object containing the field
   * @param offset offset to the field within `obj`
   * @return the retrieved value
   */
  def getLongVolatile(obj: AnyRef, offset: Long): Long =
    instance.getLongVolatile(obj, offset)

  /**
   * Gets an `AnyRef` field from the given object,
   * using `volatile` semantics.
   *
   * @param obj non-null; object containing the field
   * @param offset offset to the field within `obj`
   * @return the retrieved value
   */
  def getObjectVolatile(obj: AnyRef, offset: Long): AnyRef =
    instance.getObjectVolatile(obj, offset)

  /**
   * Stores an `Int` field into the given object,
   * using `volatile` semantics.
   *
   * @param obj non-null; object containing the field
   * @param offset offset to the field within `obj`
   * @param update the value to store
   */
  def putIntVolatile(obj: AnyRef, offset: Long, update: Int): Unit =
    instance.putIntVolatile(obj, offset, update)

  /**
   * Stores an `Long` field into the given object,
   * using `volatile` semantics.
   *
   * @param obj non-null; object containing the field
   * @param offset offset to the field within `obj`
   * @param update the value to store
   */
  def putLongVolatile(obj: AnyRef, offset: Long, update: Long): Unit =
    instance.putLongVolatile(obj, offset, update)

  /**
   * Stores an `AnyRef` field into the given object,
   * using `volatile` semantics.
   *
   * @param obj non-null; object containing the field
   * @param offset offset to the field within `obj`
   * @param update the value to store
   */
  def putObjectVolatile(obj: AnyRef, offset: Long, update: AnyRef): Unit =
    instance.putObjectVolatile(obj, offset, update)

  private[this] val instance: sun.misc.Unsafe = {
    val f = classOf[sun.misc.Unsafe].getDeclaredConstructor()
    f.setAccessible(true)
    f.newInstance()
  }
}