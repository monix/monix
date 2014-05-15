package monifu.concurrent.misc

import language.experimental.macros
import scala.util.control.NonFatal

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
    instance.objectFieldOffset(field)
  }

  /**
   * Report the location of a given static field, in conjunction with [[staticFieldOffset]].
   *
   * Fetch the base "Object", if any, with which static fields of the
   * given class can be accessed via methods like [[getIntVolatile]].
   * This value may be null.  This value may refer to an object
   * which is a "cookie", not guaranteed to be a real Object, and it should
   * not be used in any way except as argument to the get and put routines in
   * this class.
   */
  def staticFieldBase(field: java.lang.reflect.Field): AnyRef = {
    instance.staticFieldBase(field)
  }

  /**
   * Gets the raw byte offset from the start of an object's memory to
   * the memory used to store the indicated static field.
   *
   * @param field non-null; the field in question, which must be a static field
   * @return the offset to the field
   */
  def staticFieldOffset(field: java.lang.reflect.Field): Long = {
    instance.staticFieldOffset(field)
  }

  /**
   * Gets the offset from the start of an array object's memory to
   * the memory used to store its initial (zeroeth) element.
   *
   * @param clazz non-null; class in question; must be an array class
   * @return the offset to the initial element
   */
  def arrayBaseOffset(clazz: Class[_]): Int = {
    instance.arrayBaseOffset(clazz)
  }

  /**
   * Gets the size of each element of the given array class.
   *
   * @param clazz non-null; class in question; must be an array class
   * @return &gt; 0; the size of each element of the array
   */
  def arrayIndexScale(clazz: Class[_ <: Array[_]]): Int = {
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

  /**
   * Parks the calling thread for the specified amount of time,
   * unless the "permit" for the thread is already available (due to
   * a previous call to [[unpark]]. This method may also return
   * spuriously (that is, without the thread being told to unpark
   * and without the indicated amount of time elapsing).
   *
   * @param absolute whether the given time value is absolute
   *                 milliseconds-since-the-epoch (<code>true</code>) or relative
   *                 nanoseconds-from-now (<code>false</code>)
   * @param time the (absolute millis or relative nanos) time value
   */
  def park(absolute: Boolean, time: Long): Unit =
    instance.park(absolute, time)

  /**
   * Unparks the given object, which must be a `Thread`.
   *
   * See `java.util.concurrent.locks.LockSupport` for more
   * in-depth information of the behavior of this method.
   *
   * @param thread non-null; the thread to unpark
   */
  def unpark(thread: Thread): Unit =
    instance.unpark(thread)

  private[this] val instance: sun.misc.Unsafe =
    try {
      val field = classOf[sun.misc.Unsafe].getDeclaredField("theUnsafe")
      field.setAccessible(true)
      field.get(null).asInstanceOf[sun.misc.Unsafe]
    }
    catch {
      case NonFatal(ex) =>
        // the above does not work on Android, so trying to instantiate
        // a new instance
        val constructor = classOf[sun.misc.Unsafe].getDeclaredConstructor()
        constructor.setAccessible(true)
        constructor.newInstance()
    }
}