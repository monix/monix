---
layout: page
title: Atomic References
permalink: /tut/base/atomics.html
---

Scala is awesome at handling concurrency and parallelism, providing
high-level tools for handling it, however sometimes you need to go
lower level. Java's library provides all the multi-threading
primitives required, however the interfaces of these primitives
sometime leave something to be desired.

One such example are the atomic references provided in
[java.util.concurrent.atomic](http://docs.oracle.com/javase/6/docs/api/java/util/concurrent/atomic/package-summary.html)
package. This project is an attempt at improving these types for daily
usage.

## Providing a Common interface

So you have
[j.u.c.a.AtomicReference<V>](http://docs.oracle.com/javase/6/docs/api/java/util/concurrent/atomic/AtomicReference.html),
[j.u.c.a.AtomicInteger](http://docs.oracle.com/javase/6/docs/api/java/util/concurrent/atomic/AtomicInteger.html),
[j.u.c.a.AtomicLong](http://docs.oracle.com/javase/6/docs/api/java/util/concurrent/atomic/AtomicLong.html)
and
[j.u.c.a.AtomicBoolean](http://docs.oracle.com/javase/6/docs/api/java/util/concurrent/atomic/AtomicLong.html).
The reason why `AtomicReference<V>` does not suffice is because
compare-and-set works with reference equality, not structural equality
like it happens with primitives. So you cannot simply box an integer
and use it safely, plus you've got the whole boxing/unboxing overhead.

One problem is that all of these classes do not share a common
interface and there's no reason for why they shouldn't.

```tut:silent
import monix.base.atomic._

val refInt1: Atomic[Int] = Atomic(0)
val refInt2: AtomicInt = Atomic(0)

val refLong1: Atomic[Long] = Atomic(0L)
val refLong2: AtomicLong = Atomic(0L)

val refString1: Atomic[String] = Atomic("hello")
val refString2: AtomicAny[String] = Atomic("hello")
```

### Working with Numbers

One really common use-case for atomic references are for numbers to
which you need to add or subtract. To this purpose
`j.u.c.a.AtomicInteger` and `j.u.c.a.AtomicLong` have an
`incrementAndGet` helper. However Ints and Longs aren't the only types
you normally need. How about `Float` and `Double` and `Short`? How about
`BigDecimal` and `BigInt`?

In Scala, thanks to the
[Numeric[T]](http://www.scala-lang.org/api/current/index.html#scala.math.Numeric)
type-class, we can do this:

```tut:book
val ref = Atomic(BigInt(1))

// now we can increment a BigInt
ref.incrementAndGet()

// or adding to it another value
ref.addAndGet(BigInt("329084291234234"))
```

But then if we have a type that isn't a number:
```tut:silent
val string = Atomic("hello")
```

Trying to apply numeric operations will of course fail:
```tut:fail
string.incrementAndGet()
```

### Support for Other Primitives (Float, Double, Short, Char, Byte)

Here's a common gotcha with Java's `AtomicReference<V>`. Suppose
we've got this atomic:

```tut:book
import java.util.concurrent.atomic.AtomicReference

val ref = new AtomicReference(0.0)
```

The unexpected happens on `compareAndSet`:

```tut:book
val isSuccess = ref.compareAndSet(0.0, 100.0)
```
```tut:invisible:fail
assert(isSuccess)
```

Calling `compareAndSet` fails because when using `AtomicReference<V>`
the equality comparison is done by reference and it doesn't work for
primitives because the process of
[Autoboxing/Unboxing](http://docs.oracle.com/javase/tutorial/java/data/autoboxing.html)
is involved. And then there's the efficiency issue. By using an
AtomicReference, you'll end up with extra boxing/unboxing going on.

`Float` can be stored inside an `AtomicInteger` by using Java's
`Float.floatToIntBits` and `Float.intBitstoFloat`. `Double` can be
stored inside an `AtomicLong` by using Java's
`Double.doubleToLongBits` and `Double.longBitsToDouble`. `Char`,
`Byte` and `Short` can be stored inside an `AtomicInteger` as well,
with special care to handle overflows correctly. All this is done to avoid boxing
for performance reasons.

```tut:book
val ref = Atomic(0.0)

ref.compareAndSet(0.0, 100.0)

ref.incrementAndGet()

val ref = Atomic('a')

ref.incrementAndGet()

ref.incrementAndGet()
```

Even when boxing a number inside a generic `AtomicNumberAny` (that makes use of
the `Numeric[T]` type-class from Scala's standard library, using a plain `j.u.c.a.AtomicReference[T]`
internally), the above gotcha doesn't happen, as the implementation of `AtomicNumberAny` is built
to encounter the auto-boxing effects:

```tut:book
val ref = AtomicNumberAny(0.0)

ref.compareAndSet(0, 100)
```

### Common Pattern: Loops for Transforming the Value

`incrementAndGet` represents just one use-case of a simple and more
general pattern. To push items in a queue for example, one would
normally do something like this in Java:

```tut:silent
import collection.immutable.Queue
import java.util.concurrent.atomic.AtomicReference

def pushElementAndGet[T <: AnyRef, U <: T](ref: AtomicReference[Queue[T]], elem: U): Queue[T] = {
  var continue = true
  var update = null

  while (continue) {
    var current: Queue[T] = ref.get()
    var update = current.enqueue(elem)
    continue = !ref.compareAndSet(current, update)
  }
  update
}
```

This is such a common pattern. Taking a page from the wonderful
[ScalaSTM](http://nbronson.github.io/scala-stm/), with `Atomic` you
can simply do this:

```tut:book
val ref = Atomic(Queue.empty[String])
ref.transformAndGet(_.enqueue("hello"))
ref.transformAndGet(_.enqueue("world"))

ref.transformAndExtract(_.dequeue)

ref.transformAndExtract(_.dequeue)
```

Voilà, you now have a concurrent, thread-safe and non-blocking Queue. You can do this
for whatever persistent data-structure you want.

### Common-pattern: Block the thread until progress is possible

This line of code blocks the thread until the `compareAndSet` operation succeeds.
```tut:invisible
import concurrent.duration._
val ref = Atomic("")
ref.set("hello")
```
```tut:book
ref.waitForCompareAndSet("hello", "world")
```

You can also specify a timeout:
```tut:fail
ref.waitForCompareAndSet("hello", "world", 10.millis)
```

You can also block the thread waiting for a certain value:
```tut:silent
ref.waitForValue("world")
```

And of course you can specify a timeout:
```tut:fail
ref.waitForValue("hello", 10.millis)
```

You can block the thread waiting for a callback receiving the
persisted value to become true:
```tut:book
ref.waitForCondition(_.contains("wor"))
```

And of course you can specify a timeout:
```tut:fail
ref.waitForCondition(10.millis, _.contains("hell"))
```

All these blocking calls are also interruptible, throwing
an `InterruptedException` in case that happened.

## Scala.js support for targeting Javascript

These atomic references are also cross-compiled to [Scala.js](http://www.scala-js.org/)
for targeting Javascript engines, because:

- it's a useful way of boxing mutable variables, in case you need to box
- it's a building block for doing synchronization, so useful for code that you want cross-compiled
- because mutability doesn't take *time* into account and `compareAndSet` does, atomic references and
  `compareAndSet` in particular is also useful in a non-multi-threaded / asynchronous environment

What isn't supported on top of Scala.js / Javascript:

- blocking methods aren't supported since the semantics aren't possible (fret not, the compiler will not
  let you use them since they are missing)
- cache padded versions, since they make no sense in Javascript

## Efficiency

Atomic references are low-level primitives for concurrency and because
of that any extra overhead is unacceptable.

### Boxing / Unboxing

Working with a common `Atomic[T]` interface implies
boxing/unboxing of primitives. This is why the constructor for atomic references always returns the most
specialized version, as to avoid boxing and unboxing:

```tut:book
val ref = Atomic(1)
val ref = Atomic(1L)
val ref = Atomic(true)
val ref = Atomic("")
```

Increments/decrements are done by going through the
[Numeric[T]](http://www.scala-lang.org/api/current/index.html#scala.math.Numeric)
provided implicit, but only for `AnyRef` types, such as BigInt and
BigDecimal. For Scala's primitives the logic has been optimized to bypass
`Numeric[T]`.

### Cache-padded versions for avoiding the false sharing problem

In order to reduce cache contention, cache-padded versions for all Atomic
classes are provided. For reference on what that means, see:

- http://mail.openjdk.java.net/pipermail/hotspot-dev/2012-November/007309.html
- http://openjdk.java.net/jeps/142

To use the cache-padded versions, you need to import stuff
from the `padded` sub-package:

```tut:silent
import monix.base.atomic.padded.Atomic

val ref = Atomic(1)
```
