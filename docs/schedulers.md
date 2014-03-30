# Schedulers & Cancelables

<img src="assets/monifu.png" align="right" />

Monifu's Schedulers and Cancelables are inspired by .NET's
[Reactive Extensions](https://rx.codeplex.com/) and Netflix's
[RxJava](https://github.com/Netflix/RxJava) and are cross-compiled to
[Scala.js](scala-js.org) for also targeting Javascript runtimes.

## Status Quo

Sometimes you need to schedule units of computation to execute
asynchronously, like on a different thread, or in a different
thread-pool. Towards this purpose we can use an
[ExecutionContext](http://www.scala-lang.org/api/current/#scala.concurrent.ExecutionContext)
from Scala's standard library, as in:

```scala
import scala.concurrent.ExecutionContext.Implicits.global

global.execute(new Runnable {
  def run(): Unit = {
    println("Executing asynchronously ...")
  }
})
```

An `ExecutionContext` is too limited, having the following problems:

1. It cannot execute things with a given delay
2. It cannot execute units of work periodically (e.g. once every
   second)
3. The `execute()` method doesn't return a token you could use to
   cancel the pending execution of a task

Developers using Akka do have a
[nicer interface](http://doc.akka.io/docs/akka/current/scala/scheduler.html)
that solve the above problems in the form of
[akka.actor.Scheduler](http://doc.akka.io/api/akka/current/index.html#akka.actor.Scheduler),
so you can do this:

```scala
val task: akka.actor.Cancellable =
  ActorSystem("default").scheduler.scheduleOnce(1.second) {
    println("Executing asynchronously ...")
  }

// canceling it before execution happens
task.cancel()
```

There are problems with the above approach - Akka's Scheduler is an
integral part of Akka's actors system and their usage implies a
dependency on Akka, which is a pretty heavy dependency and there's no
good reason for that, Cancelables are useful outside the context of
Schedulers or Akka and in terms of the API, as you'll see, we can do better.

Another approach is to use a
[ScheduledExecutorService](http://docs.oracle.com/javase/7/docs/api/java/util/concurrent/ScheduledExecutorService.html)
from Java's standard library and is fairly capable and standard, however the API is not idiomatic Scala,
with the results returned being of type
[j.u.c.ScheduledFuture](http://docs.oracle.com/javase/7/docs/api/java/util/concurrent/ScheduledFuture.html),
which are pretty heavy and have nothing to do with Scala's Futures and
again, this API can surely use improvement.


## Monifu's Scheduler

A
[monifu.concurrent.Scheduler](../monifu-core/src/shared/scala/monifu/concurrent/Scheduler.scala)
is a super-set of a
[scala.concurrent.ExecutionContext](http://www.scala-lang.org/api/current/#scala.concurrent.ExecutionContext),
that is as I've said, inspired by Rx.Scheduler.

### Initialization

On top of the JVM, to get an instance is easy:
```scala
val s = monifu.concurrent.Scheduler.computation
```

This is in fact an instance of
[ConcurrentScheduler](../monifu-core/src/main/scala/monifu/concurrent/schedulers/ConcurrentScheduler.scala),
the only provided implementation at the moment of writing, with
`Scheduler.computation` being a default instance that internally uses
[ExecutionContext.Implicits.global](http://www.scala-lang.org/api/current/#scala.concurrent.ExecutionContext$$Implicits$)
for executing tasks and a
[j.u.c.ScheduledThreadPoolExecutor](http://docs.oracle.com/javase/7/docs/api/java/util/concurrent/ScheduledThreadPoolExecutor.html) instance
for scheduling delayed or periodic tasks.

You can construct your own instances like so:

```scala
import monifu.concurrent.Scheduler
import monifu.concurrent.schedulers._

// from an ExecutionContext ...
val s = Scheduler.concurrent(ExecutionContext.Implicits.global)

// or from a Java ExecutorService
import java.util.concurrent.Executors
val s = Scheduler.fromExecutorService(Executors.newCachedThreadPool())
```

`ConcurrentScheduler` uses by default a single, global
`j.u.c.ScheduledThreadPoolExecutor` instance for all created
instances, since this executor does nothing else but to do scheduling
and is not in charge of actually executing the tasks. But if you don't
like this default and want to use your own configured scheduled
executor, you can do so like this:

```scala
import monifu.concurrent.schedulers._
import java.util.concurrent.Executors
import concurrent.ExecutionContext

val s = ConcurrentScheduler(
  schedulerService = Executors.newSingleThreadScheduledExecutor(),
  ec = ExecutionContext.Implicits.global
)
```

### Usage 

#### The Basics

To schedule a unit of work for immediate execution:

```scala
import monifu.concurrent.Scheduler.{computation => s}

val task = s.scheduleOnce {
  println("Hello world")
}

// in case we want to cancel the pending execution, in case
// it hasn't executed yet
task.cancel()
```

As you can see, the returned token is a
[Cancelable](../monifu-core/src/shared/scala/monifu/concurrent/Cancelable.scala)
that can be used to cancel the pending execution.

You can also schedule units of computation with a delay, specified as
a `concurrent.duration.FiniteDuration`:

```scala
import monifu.concurrent.Scheduler.{computation => s}

val task = s.scheduleOnce(1.second, {
  println("Hello world")
})

// in case we want to cancel the pending execution
task.cancel()
```

The above schedules a computation to run after 1 second since now. The
returned token is also a
[Cancelable](../monifu-core/src/shared/scala/monifu/concurrent/Cancelable.scala)
that can be used to cancel the pending execution.

We can also schedule things to run periodically:

```scala
import monifu.concurrent.Scheduler.{computation => s}

val task = s.schedulePeriodically(1.second, 2.second, {
  println("Hello world")
})

// in case we want to cancel the execution of this periodic task
task.cancel()
```

The above will print `"Hello world"` after 1 second (the initial
delay) and then every 2 seconds after that. Successive executions of
a task scheduled with `schedulePeriodically()` do NOT overlap. The
implementation tries its best to schedule the first execution at
`initialDelay` and then at `initialDelay + period` and then at
`initialDelay + 2 * period` and so on.

#### Scheduling tasks that re-schedule themselves

The `Scheduler` also provides a pair of methods that is more general.
First is the `scheduleRecursive()`, my favorite:

```scala
import monifu.concurrent.Scheduler.{computation => s}
import monifu.concurrent.atomic.Atomic

val counter = Atomic(0)

val task = s.scheduleRecursive(1.second, 2.seconds, { reschedule =>
  val c = counter.incrementAndGet
  println(s"Counter: $c")

  if (c < 10)
    reschedule()
})


// in case you want to cancel it prematurely:
// task.cancel()
```

When you run the above it will print:

```
Counter: 1
Counter: 2
Counter: 3
Counter: 4
Counter: 5
Counter: 6
Counter: 7
Counter: 8
Counter: 9
Counter: 10
```

And then it will stop, by itself. This is a more general variant of
`schedulePeriodically` and in fact if you look at
[its implementation](/monifu-core/src/shared/scala/monifu/concurrent/Scheduler.scala),
`schedulePeriodically` is implemented in terms of `scheduleRecursive`,
something like this:

```scala
def schedulePeriodically(initialDelay: FiniteDuration, period: FiniteDuration, action: => Unit): Cancelable =
  scheduleRecursive(initialDelay, period, { reschedule =>
    action
    reschedule()
  })
```

The callback passed to `scheduleRecursive` takes one parameter which
is a function that allows you to schedule the next periodic execution
in the chain and you can stop doing so at any time.

But that's not general enough. The `Scheduler` also provides a pair of
methods, that take as parameter the current `Scheduler` instance and
that must return a `Cancelable`. Here's how the above example would
look like:

```scala
import monifu.concurrent.Scheduler
import monifu.concurrent.Scheduler.{computation => s}
import monifu.concurrent.atomic.Atomic

val counter = Atomic(0)

def loop(scheduler: Scheduler): Cancelable = {
  val c = counter.incrementAndGet
  println(s"Counter: $c")

  if (c < 10)
    scheduler.schedule(2.seconds, loop)
  else
    Cancelable.empty
}

val task = s.schedule(1.seconds, loop)

// in case you want to cancel it prematurely:
// task.cancel()
```

The effect of the above would be more or less the same as with
`scheduleRecursive` example. There are implementation details
(`scheduleRecursive` tries to execute at fixed rates, whereas this
exemple executes things with a fixed delay for simplicity).

### Cancelables

You may have noticed in the above examples the
[Cancelable](/monifu-core/src/shared/scala/monifu/concurrent/Cancelable.scala)
interface.

......
