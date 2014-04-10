# Cancelables

<img src="assets/monifu.png" align="right" />

Monifu's Cancelables and [Schedulers](./schedulers.md) are inspired by .NET's
[Reactive Extensions](https://rx.codeplex.com/) and Netflix's
[RxJava](https://github.com/Netflix/RxJava) and are cross-compiled to
[Scala.js](scala-js.org) for also targeting JavaScript runtimes.

Rough equivalents of Monifu's
[Cancelable](../monifu-core/src/shared/scala/monifu/concurrent/Cancelable.scala)
interface:

- [java.io.Closable](http://docs.oracle.com/javase/7/docs/api/java/io/Closeable.html),
  pity that this standard interface has improper naming ("cancel" is
  more general than "close") and is about dealing with I/O (since it
  specifies a `throws IOException` in its API)
- [akka.actor.Cancellable](http://doc.akka.io/api/akka/current/index.html#akka.actor.Cancellable),
  pity that this Akka interface is part of Akka (instead of Scala's standard library)
  and doesn't provide any guarantees (e.g. idempotence)
- [System.IDisposable](http://msdn.microsoft.com/en-us/library/system.idisposable.aspx) from .NET
- [RxJava Subscription](http://netflix.github.io/RxJava/javadoc/rx/subscriptions/package-summary.html)

Monifu's `Cancelable` is inspired by RxJava's `Subscription` /
Rx.NET's `IDisposable` and provides equivalents for the most useful
implementations. Currently used by Monifu's
[Scheduler](./schedulers.md), but will also be a part of `monifu-rx`
(work pending) and as you'll see, the provided implementations are
pretty useful on their own for your own custom logic.

## Base Interface

```scala
trait Cancelable {
  def isCanceled: Boolean
  def cancel(): Unit
}
```

[Cancelable](../monifu-core/src/shared/scala/monifu/concurrent/Cancelable.scala) objects have a `cancel()` method
that can be used for resource release / cleanup and an `isCanceled` method that can be used to query
the status. The design rules when implementing cancelables:

1. The `close()` method should be idempotent and thread-safe and all `Cancelable` implementations should
   make sure that it is. Idempotence means that calling it multiple times has the same effect as calling it
   only once.
2. After `close()` is invoked, `isCanceled` should imediately become `true` - as in, you can trigger 
   asynchronous computations in `close()` that could execute on another thread, but after `close()` 
   is finished, then on the same thread `isCanceled` needs to reflect that `close()` was invoked

The companion object of `Cancelable` provides handy helpers for building references:

```scala
import monifu.concurrent._

val s = Cancelable {
  println("Canceling unit of work ...")
}
```

Usage is pretty straightforward:

```
scala> s.isCanceled
res: Boolean = false

scala> s.cancel()
Canceling unit of work ...

scala> s.isCanceled
res2: Boolean = true

scala> s.cancel() // idempotence guaranteed (nothing happens here)

```

## CompositeCancelable

A
[CompositeCancelable](../monifu-core/src/shared/scala/monifu/concurrent/cancelables/CompositeCancelable.scala)
is an aggregate of `Cancelable` references (to which you can add new
references or remove existing ones) and that are handled in aggregate
when doing a `cancel()`.

```scala
val composite = CompositeCancelable()

composite += Cancelable {
  println("Canceling unit of work 1")
}
composite += Cancelable {
  println("Canceling unit of work 2")
}
```

Canceling, as mentioned, works in aggregate, with idempotence preserved:

```
scala> composite.cancel()
Canceling unit of work 1
Canceling unit of work 2

scala> composite.cancel() // is idempotent so nothing happens here

```

However, if the composite is already canceled when adding a new
reference to it, then the added Cancelable will also get canceled:

```
scala> composite += Cancelable { println("Ooops, canceling this one too...") }
Ooops, canceling this one too...
```

This is used for example in
[ConcurrentScheduler](../monifu-core/src/main/scala/monifu/concurrent/schedulers/ConcurrentScheduler.scala)
within `scheduleOnce()`. Lets analyze that method for a bit, shall we?
Checkout the comments:

```scala
def scheduleOnce(initialDelay: FiniteDuration, action: => Unit): Cancelable = {
  // boolean indicating whether the unit of computation was canceled or not
  val isCancelled = Atomic(false)
  // we need one Cancelable for manipulating the above boolean
  val sub = CompositeCancelable(Cancelable {
    isCancelled := true
  })

  val runnable = new Runnable {
    def run(): Unit =
      ec.execute(new Runnable {
        def run(): Unit =
          // checking our atomic boolean, if false then don't execute
          if (!isCancelled.get) action
      })
  }

  // schedule the task for execution
  val task = s.schedule(runnable, initialDelay.toMillis, TimeUnit.MILLISECONDS)
  
  // adding to our composite another cancelable that removes
  // the task from our queue of pending tasks ... we couldn't have added this
  // above, because the `task` reference wasn't available yet
  sub += Cancelable {
    task.cancel(true)
  }

  // returning the composite, that on cancel() should both set isCanceled to false
  // and cancel the pending task
  sub
}
```

## MultiAssignmentCancelable

Represents a `Cancelable` that can hold another `Cancelable` reference
and that can also swap this reference.

```scala
val s = MultiAssignmentCancelable()

// sets the initial value
s() = Cancelable { println("Terminating first unit") }
// swaps the value with another one
s() = Cancelable { println("Terminating second unit") }
```

On cancel, the boxed reference gets canceled too:

```
scala> s.cancel()
Terminating second unit

scala> s.cancel() // idempotent, so calling it a second time does nothing

```

As with the composite, if the `MultiAssignmentCancelable` is already canceled, then on
assignment it cancels the reference being assigned.

```
scala> s() = Cancelable { println("Ooops, canceling this one too...") }
Ooops, canceling this one too...
```

## SingleAssignmentCancelable

A
[SingleAssignmentCancelable](../monifu-core/src/shared/scala/monifu/concurrent/cancelables/SingleAssignmentCancelable.scala)
behaves just like a `MultiAssignmentCancelable`, except that it can be
assigned only once. On a second assignment, it throws an
`IllegalStateException`.

And as with the `MultiAssignmentCancelable`, assigning it after it has
already been canceled, will cancel the reference being assigned.

## RefCountCancelable

A [RefCountCancelable](../monifu-core/src/shared/scala/monifu/concurrent/cancelables/RefCountCancelable.scala)
is for those instances in which you need some sort of composite that `onCancel` waits
after all children cancelables to be canceled too, before executing its callback.

```scala
import monifu.concurrent.cancelables.RefCountCancelable

val refs = RefCountCancelable { println("Everything was canceled") }

// acquiring two cancelable references
val ref1 = refs.acquireCancelable()
val ref2 = refs.acquireCancelable()

refs.cancel() // <-- starting the cancelation process
refs.isCanceled // <-- true, but the callback hasn't been invoked yet

// after our RefCountCancelable was canceled, this will return
// an already canceled reference
val ref3 = refs.acquireCancelable() 
ref3.isCanceled // <-- true

ref1.isCanceled // <-- still active, so it's false
ref1.cancel() // <-- release reference, nothing happens here

// finally getting rid of the last acquired reference
ref2.cancel() // <-- prints "Everything was canceled"
```

## Further Reading

See [Schedulers](./schedulers.md).