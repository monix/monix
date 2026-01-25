# Migration to Cats-Effect 3.x: Comprehensive Analysis and Planning

**Date:** 2026-01-25  
**Monix Version:** 3.4.x (Series 4.x in development)  
**Current Dependency:** Cats-Effect 2.5.5  
**Target:** Cats-Effect 3.x

---

## Executive Summary

Migrating Monix from Cats-Effect 2.x to 3.x is a **significant undertaking** requiring fundamental changes to the cancellation model, typeclass hierarchy integration, and API surface. This document provides a comprehensive analysis and detailed migration plan.

### Key Findings

- **Effort Estimate:** 8-12 weeks for core migration + 4-8 weeks for testing/stabilization
- **Complexity:** **VERY HIGH** - Requires changes to fundamental execution model
- **Breaking Changes:** **MAJOR** - API breakage inevitable, will require Monix 4.0
- **Risk Level:** **HIGH** - Core concurrency primitives affected
- **Most Affected Module:** `monix-eval` (Task implementation)

### Critical Challenges

1. **Interruption Model Redesign** - CE3's `uncancelable` has compositional semantics via `Poll[F]`
2. **Typeclass Hierarchy Changes** - `Effect`/`ConcurrentEffect` removed, hierarchy flattened
3. **Timer/ContextShift Removal** - No direct replacements, requires architectural changes
4. **Resource Management** - `bracketCase` API changes, `Resource` becomes primary
5. **Binary Compatibility** - Complete break with Monix 3.x

---

## Background: Why CE3 Migration is Hard

### The Interruption Model Problem

The core difficulty stems from **fundamentally different cancellation semantics** between CE2 and CE3.

#### Cats-Effect 2.x Model
```scala
def uncancelable[A](fa: F[A]): F[A]
```
- Takes entire effect and makes it uncancelable
- No way to selectively allow cancellation within the block
- Leads to potential deadlocks (e.g., `Semaphore.acquire` in `uncancelable`)
- Resource safety vs. interruptibility tradeoff

#### Cats-Effect 3.x Model  
```scala
def uncancelable[A](body: Poll[F] => F[A]): F[A]

trait Poll[F[_]] {
  def apply[A](fa: F[A]): F[A]
}
```
- Provides `Poll[F]` capability to selectively restore cancellability
- Nested `uncancelable` regions compose properly
- **Restore semantics**: `poll` inherits outer cancellation status
- Enables safer resource management patterns

**Impact on Monix:** Task's internal `uncancelable` implementation (via `TaskCancellation.scala`) must be completely rewritten to support the `Poll[F]` pattern.

---

## Detailed Migration Plan

### Phase 1: Foundation & Dependencies (2-3 weeks)

#### 1.1 Update Dependencies

**File:** `build.sbt`

```diff
- val catsEffect_Version = "2.5.5"
+ val catsEffect_Version = "3.5.0"  // Latest stable CE3

- val cats_Version = "2.7.0"
+ val cats_Version = "2.10.0"  // CE3 requires Cats 2.9+
```

**Additional dependencies to update:**
- Update `fs2_Version` from `2.5.11` to `3.x` (if using FS2)
- Review all transitive dependencies for CE3 compatibility

#### 1.2 Remove Deprecated Implicits

**Files affected:** All modules using CE2

```diff
- implicit cs: ContextShift[F]
- implicit timer: Timer[F]
```

**Replacement strategy:**
- `ContextShift[F]` → Remove completely (CE3 handles internally)
- `Timer[F]` → Replace with `Temporal[F]` where needed
- Audit all method signatures removing these constraints

**Files requiring changes:**
- `monix-catnap/shared/src/main/scala/monix/catnap/*.scala` (~8 files)
- `monix-tail/shared/src/main/scala/monix/tail/Iterant.scala` (~5 files)
- All test files using these constraints

---

### Phase 2: Typeclass Hierarchy Refactoring (3-4 weeks)

#### 2.1 Remove Effect/ConcurrentEffect Instances

**Files to delete/archive:**
```
monix-eval/shared/src/main/scala/monix/eval/instances/CatsEffectForTask.scala
```

**Rationale:** CE3 removed `Effect` and `ConcurrentEffect` typeclasses. These provided unsafe `runAsync` operations.

#### 2.2 Create New CE3 Typeclass Instances

**New instance hierarchy:**

```
CatsBaseForTask (MonadError[Task, Throwable])
    ├── CatsSyncForTask (Sync[Task])
    │       └── CatsClockForTask (Clock[Task])
    ├── CatsAsyncForTask (Async[Task])
    │       └── CatsTemporalForTask (Temporal[Task])
    └── CatsConcurrentForTask (Concurrent[Task])
            └── CatsSpawnForTask (Spawn[Task])
```

**File:** `monix-eval/shared/src/main/scala/monix/eval/instances/CatsAsyncForTask.scala`

**Key changes:**

##### A. Update Async Instance

```scala
class CatsAsyncForTask extends CatsSyncForTask with Async[Task] {
  // CE3: async callback signature changed
  override def async[A](cb: (Either[Throwable, A] => Unit) => F[Option[F[Unit]]]): Task[A] = {
    Task.async { (scheduler, callback) =>
      cb(callback).fold(Cancelable.empty) { cancelToken =>
        Cancelable(() => cancelToken.runAsyncAndForget(scheduler))
      }
    }
  }
  
  override def async_[A](cb: (Either[Throwable, A] => Unit) => Unit): Task[A] =
    TaskCreate.async(cb)
  
  // NEW in CE3: replaces asyncF
  override def evalOn[A](fa: Task[A], ec: ExecutionContext): Task[A] =
    fa.executeOn(ec)  // Use Task's existing executeOn
  
  // CE3: Never fails, unlike CE2
  override def never[A]: Task[A] = 
    Task.never
  
  // Remove: asyncF (doesn't exist in CE3)
  // Remove: shift/shift (replaced by evalOn)
}
```

##### B. Update Concurrent Instance  

```scala
class CatsConcurrentForTask extends CatsAsyncForTask with Concurrent[Task] {
  // CE3: NEW SIGNATURE with Poll[Task]
  override def uncancelable[A](body: Poll[Task] => Task[A]): Task[A] = {
    // Implementation strategy:
    // 1. Create Poll[Task] instance that tracks cancellation state
    // 2. Call body with Poll, wrapping in TaskCancellation
    // 3. Handle nested uncancelable regions
    
    TaskCancellation.uncancelableWithPoll(body)  // NEW implementation needed
  }
  
  override def canceled: Task[Unit] = 
    Task.raiseError(new CancellationException)  // CE3: canceled is an effect
  
  override def onCancel[A](fa: Task[A], fin: Task[Unit]): Task[A] =
    fa.doOnCancel(fin)
  
  // CE3: ref, deferred are in Concurrent now (moved from Spawn)
  override def ref[A](a: A): Task[Ref[Task, A]] = 
    Ref.of[Task, A](a)  // Use CE3's Ref
  
  override def deferred[A]: Task[Deferred[Task, A]] =
    Deferred[Task, A]  // Use CE3's Deferred
}
```

##### C. Add Temporal Instance (NEW in CE3)

```scala
class CatsTemporalForTask(implicit scheduler: Scheduler) 
  extends CatsConcurrentForTask with Temporal[Task] {
  
  override def sleep(duration: FiniteDuration): Task[Unit] =
    Task.sleep(duration)
  
  override def monotonic: Task[FiniteDuration] =
    Task.eval(scheduler.clockMonotonic(NANOSECONDS).nanos)
  
  override def realTime: Task[FiniteDuration] =
    Task.eval(scheduler.clockRealTime(MILLISECONDS).millis)
  
  // CE3: timeout replaces Timer's timeout
  override def timeout[A](fa: Task[A], duration: FiniteDuration): Task[A] =
    fa.timeout(duration)
  
  override def timeoutTo[A](fa: Task[A], duration: FiniteDuration, fallback: Task[A]): Task[A] =
    fa.timeoutTo(duration, fallback)
}
```

#### 2.3 Update Task Companion Object

**File:** `monix-eval/shared/src/main/scala/monix/eval/Task.scala`

```diff
  /** Cats-Effect typeclass instances */
- implicit def catsEffect(implicit s: Scheduler, opts: Task.Options): ConcurrentEffect[Task] =
-   new CatsConcurrentEffectForTask()
+ implicit def catsAsync: Async[Task] =
+   CatsAsyncForTask
  
+ implicit def catsTemporal(implicit s: Scheduler): Temporal[Task] =
+   new CatsTemporalForTask()

+ implicit def catsConcurrent: Concurrent[Task] =
+   CatsConcurrentForTask
```

---

### Phase 3: Core Task Implementation Changes (4-5 weeks)

#### 3.1 Implement Poll[Task] Support

**New file:** `monix-eval/shared/src/main/scala/monix/eval/internal/TaskPoll.scala`

```scala
private[eval] final class TaskPoll extends Poll[Task] {
  def apply[A](fa: Task[A]): Task[A] = {
    // Restore cancellation state from outer scope
    // This is the core of CE3's compositional semantics
    Task.ContextSwitch(
      fa,
      restoreCancellationContext,  // NEW context restoration logic
      clearCancellationContext
    )
  }
}

private[eval] object TaskPoll {
  def create(): TaskPoll = new TaskPoll()
}
```

#### 3.2 Rewrite TaskCancellation.uncancelable

**File:** `monix-eval/shared/src/main/scala/monix/eval/internal/TaskCancellation.scala`

```diff
- def uncancelable[A](fa: Task[A]): Task[A] =
-   Task.ContextSwitch(fa, withConnectionUncancelable, restoreConnection)

+ def uncancelableWithPoll[A](body: Poll[Task] => Task[A]): Task[A] = {
+   Task.ContextSwitch(
+     Task.suspend {
+       val poll = TaskPoll.create()
+       body(poll)
+     },
+     enterUncancelableRegion,   // Mark as uncancelable
+     exitUncancelableRegion     // Restore parent state
+   )
+ }
```

**New context management functions:**

```scala
private def enterUncancelableRegion(
  ctx: Context,
  old: Context
): Context = {
  // Track nesting level
  // Store parent cancellation state
  ctx.withUncancelableNesting(ctx.uncancelableNesting + 1)
}

private def exitUncancelableRegion(
  old: Context,
  ctx: Context
): Context = {
  // Restore parent nesting level
  old.withUncancelableNesting(old.uncancelableNesting)
}
```

#### 3.3 Update Context to Track Uncancelable State

**File:** `monix-eval/shared/src/main/scala/monix/eval/internal/TaskRunLoop.scala` (Context class)

```diff
  final case class Context(
    scheduler: Scheduler,
    options: Task.Options,
    connection: TaskConnection,
+   uncancelableNesting: Int = 0,  // NEW: track nesting depth
    frameRef: FrameIndexRef,
    stackTracedContext: StackTracedContext
  ) {
+   def isUncancelable: Boolean = uncancelableNesting > 0
+   
+   def withUncancelableNesting(n: Int): Context =
+     copy(uncancelableNesting = n)
  }
```

#### 3.4 Update Bracket Implementation

**File:** `monix-eval/shared/src/main/scala/monix/eval/internal/TaskBracket.scala`

CE3 changes `bracketCase` parameter order and uses `Outcome` instead of `ExitCase`:

```diff
- def bracketCase[A, B](acquire: Task[A])(use: A => Task[B])(
-   release: (A, ExitCase[Throwable]) => Task[Unit]
- ): Task[B]

+ def bracketCase[A, B](acquire: Task[A])(use: A => Task[B])(
+   release: (A, Outcome[Task, Throwable, B]) => Task[Unit]  // NEW: Outcome
+ ): Task[B]
```

**Outcome mapping:**

```scala
sealed trait Outcome[F[_], E, A]
case class Succeeded[F[_], E, A](fa: F[A]) extends Outcome[F, E, A]
case class Errored[F[_], E, A](e: E) extends Outcome[F, E, A]
case class Canceled[F[_], E, A]() extends Outcome[F, E, A]

// Migration helper
def exitCaseToOutcome[A](exitCase: ExitCase[Throwable], result: => A): Outcome[Task, Throwable, A] =
  exitCase match {
    case ExitCase.Completed => Outcome.Succeeded(Task.pure(result))
    case ExitCase.Error(e)  => Outcome.Errored(e)
    case ExitCase.Canceled  => Outcome.Canceled()
  }
```

**Update bracketCase implementation:**

```scala
def bracketCase[A, B](acquire: Task[A])(use: A => Task[B])(
  release: (A, Outcome[Task, Throwable, B]) => Task[Unit]
): Task[B] = {
  Task.Async(
    new StartCase(acquire, use, release),  // Update StartCase
    trampolineBefore = false,
    trampolineAfter = false,
    restoreLocals = true
  )
}

private final class StartCase[A, B](
  acquire: Task[A],
  use: A => Task[B],
  release: (A, Outcome[Task, Throwable, B]) => Task[Unit]
) extends BaseStart[B] {
  
  def apply(ctx: Context, cb: Callback[Throwable, B]): Unit = {
    // Execute acquire in uncancelable region
    Task.unsafeStartNow(
      acquire,
      ctx,
      new Callback[Throwable, A] {
        def onSuccess(a: A): Unit = {
          val useTask = use(a)
          val releasingTask = Task.Async(
            new ReleaseFrame(a, release),
            trampolineBefore = false,
            trampolineAfter = true,
            restoreLocals = true
          )
          
          // Execute use(a) with release attached
          Task.unsafeStartNow(useTask.guarantee(releasingTask), ctx, cb)
        }
        
        def onError(ex: Throwable): Unit = cb.onError(ex)
      }
    )
  }
}

private final class ReleaseFrame[A, B](
  a: A,
  release: (A, Outcome[Task, Throwable, B]) => Task[Unit]
) extends StackFrame[B, Unit] {
  
  def apply(b: B): Task[Unit] =
    release(a, Outcome.Succeeded(Task.pure(b)))
  
  def recover(e: Throwable): Task[Unit] =
    release(a, Outcome.Errored(e))
  
  // NEW in CE3: handle cancelation
  def onCancel(): Task[Unit] =
    release(a, Outcome.Canceled())
}
```

---

### Phase 4: Monix-Catnap Migration (2-3 weeks)

#### 4.1 Update MVar

**File:** `monix-catnap/shared/src/main/scala/monix/catnap/MVar.scala`

**Key changes:**

```diff
  def apply[F[_], A](initial: A)(implicit
-   F: Concurrent[F] OrElse Async[F],
-   cs: ContextShift[F]
+   F: Concurrent[F]  // CE3: no ContextShift needed
  ): F[MVar[F, A]]
```

**ContextShift removal:**
- Remove all `cs.shift` calls
- CE3's `Async` handles fairness internally
- Replace `cs.shift >> fa` with just `fa`

**guaranteeCase → onCancel:**

```diff
- F.guaranteeCase(wait) {
-   case ExitCase.Canceled => cleanup
-   case _ => F.unit
- }

+ F.onCancel(wait, cleanup)
```

#### 4.2 Update Semaphore  

**File:** `monix-catnap/shared/src/main/scala/monix/catnap/Semaphore.scala`

Similar changes:
- Remove `ContextShift[F]`
- Replace `guaranteeCase` with `onCancel`
- Update `withPermit` to use `Resource`:

```diff
- def withPermit[B](fb: F[B]): F[B] =
-   acquire.bracket(_ => fb)(_ => release)

+ def permit: Resource[F, Unit] =
+   Resource.make(acquire)(_ => release)
```

Users migrate from:
```scala
sem.withPermit(doWork)
```

To:
```scala
sem.permit.use(_ => doWork)
```

#### 4.3 Update SchedulerEffect

**File:** `monix-catnap/shared/src/main/scala/monix/catnap/SchedulerEffect.scala`

```diff
  /** Creates Timer[F] from Scheduler */
- implicit def timer[F[_]](implicit F: Sync[F]): Timer[F]
+ implicit def temporal[F[_]](implicit F: Async[F]): Temporal[F]
```

**Implementation:**

```scala
implicit def temporal[F[_]](implicit F: Async[F], scheduler: Scheduler): Temporal[F] = {
  new Temporal[F] {
    def sleep(duration: FiniteDuration): F[Unit] =
      F.async_ { cb =>
        val task = scheduler.scheduleOnce(duration.length, duration.unit, () => cb(Right(())))
        // Return cancelation token
      }
    
    def monotonic: F[FiniteDuration] =
      F.delay(scheduler.clockMonotonic(NANOSECONDS).nanos)
    
    def realTime: F[FiniteDuration] =
      F.delay(scheduler.clockRealTime(MILLISECONDS).millis)
    
    // ... other Temporal methods
  }
}
```

#### 4.4 Update CircuitBreaker

**File:** `monix-catnap/shared/src/main/scala/monix/catnap/CircuitBreaker.scala`

```diff
- def protect[A](fa: F[A])(implicit F: Sync[F], clock: Clock[F]): F[A]
+ def protect[A](fa: F[A])(implicit F: Temporal[F]): F[A]  // Clock is part of Temporal
```

Update ExitCase handling to use Outcome.

---

### Phase 5: Monix-Tail Migration (3-4 weeks)

#### 5.1 Update Iterant Core

**File:** `monix-tail/shared/src/main/scala/monix/tail/Iterant.scala`

**Constraint changes:**

```diff
  /** Create Iterant from async source */
- def fromChannel[F[_], A](channel: ConsumerF[F, Option[A]])(implicit
-   F: Sync[F],
-   cs: ContextShift[F]
- ): Iterant[F, A]

+ def fromChannel[F[_], A](channel: ConsumerF[F, Option[A]])(implicit
+   F: Async[F]  // CE3: Async includes ContextShift functionality
+ ): Iterant[F, A]
```

#### 5.2 Timer-Based Operations

**Files:**
- `monix-tail/shared/src/main/scala/monix/tail/internal/IterantIntervalAtFixedRate.scala`
- `monix-tail/shared/src/main/scala/monix/tail/internal/IterantIntervalWithFixedDelay.scala`

```diff
- def intervalAtFixedRate[F[_]](period: FiniteDuration)(implicit
-   F: Sync[F],
-   timer: Timer[F]
- ): Iterant[F, Long]

+ def intervalAtFixedRate[F[_]](period: FiniteDuration)(implicit
+   F: Temporal[F]  // CE3: Temporal replaces Timer
+ ): Iterant[F, Long]
```

**Implementation changes:**

```diff
- timer.sleep(period).flatMap(_ => tick)
+ F.sleep(period).flatMap(_ => tick)
```

#### 5.3 Effect Typeclass Removal

**File:** `monix-tail/shared/src/main/scala/monix/tail/internal/IterantToReactivePublisher.scala`

```diff
  def toReactivePublisher[F[_], A](source: Iterant[F, A])(implicit
-   F: Effect[F]
+   F: Async[F],
+   runtime: IORuntime  // CE3: need runtime for unsafe operations
  ): Publisher[A]
```

**Challenge:** CE3 removed `Effect` which provided `runAsync`. 

**Solution:** Use `Dispatcher`:

```scala
def toReactivePublisher[F[_], A](source: Iterant[F, A])(implicit
  F: Async[F]
): Resource[F, Publisher[A]] = {
  Dispatcher.parallel[F].map { dispatcher =>
    new Publisher[A] {
      def subscribe(subscriber: Subscriber[_ >: A]): Unit = {
        // Use dispatcher to run effects
        dispatcher.unsafeRunAndForget(
          source.consume.use { consumer =>
            // Publish to subscriber
            streamToSubscriber(consumer, subscriber)
          }
        )
      }
    }
  }
}
```

**Note:** This is a breaking API change - returns `Resource[F, Publisher[A]]` instead of `Publisher[A]`.

---

### Phase 6: Testing & Validation (4-6 weeks)

#### 6.1 Update Test Dependencies

**File:** `build.sbt`

```diff
  lazy val testDependencies = Seq(
    testFrameworks := Seq(new TestFramework("minitest.runner.Framework")),
    libraryDependencies ++= Seq(
      minitestLib.value       % Test,
      catsLawsLib.value       % Test,
-     catsEffectLawsLib.value % Test
+     "org.typelevel" %%% "cats-effect-laws" % catsEffect_Version % Test,
+     "org.typelevel" %%% "cats-effect-testkit" % catsEffect_Version % Test
    )
  )
```

#### 6.2 Update Law Tests

CE3 introduces new laws and changes existing ones:

**New laws to test:**
- `ConcurrentLaws.uncancelablePollIsIdentity`
- `ConcurrentLaws.uncancelableEliminatesOnCancel`
- `ConcurrentLaws.uncancelablePollNested`
- `TemporalLaws.sleepIsNonNegative`

**Files to update:**
- `monix-eval/shared/src/test/scala/monix/eval/TaskLawsSuite.scala`

```scala
class TaskLawsSuite extends BaseLawsSuite {
  checkAllAsync("Task with Temporal") { implicit ec =>
    implicit val cs = ec.contextShift[Task]
    implicit val timer = ec.timer[Task]
    
    // CE3: Use TestControl for time testing
    TestControl.execute {
      TemporalTests[Task].temporal[Int, Int, Int]
    }
  }
}
```

#### 6.3 Performance Regression Testing

**Benchmarks to update:**
- `benchmarks/vnext/src/main/scala/monix/benchmarks/TaskBracketBenchmark.scala`
- Test CE2 vs CE3 overhead for:
  - `uncancelable` nesting (CE3 may be slower due to Poll overhead)
  - `bracket` operations (Outcome vs ExitCase)
  - `onCancel` handlers

**Expected regressions:**
- `uncancelable` may be 5-15% slower (acceptable tradeoff for safety)
- `bracketCase` similar performance (Outcome is similar to ExitCase)

---

### Phase 7: Documentation & Migration Guide (2-3 weeks)

#### 7.1 Update API Documentation

**Files to update:**
- All ScalaDoc in `monix-eval/shared/src/main/scala/monix/eval/Task.scala`
- Update examples showing:
  - New `uncancelable` with Poll
  - Migration from `bracket` to `Resource`
  - Temporal-based timing

#### 7.2 Create Migration Guide

**File:** `docs/migration-guide-4.0.md`

```markdown
# Monix 4.0 Migration Guide

## Breaking Changes from 3.x

### Cats-Effect 3 Upgrade

Monix 4.0 migrates from Cats-Effect 2.x to 3.x. This brings:

#### 1. Uncancelable Changes

**Before (Monix 3.x):**
```scala
val task = Task.sleep(1.second).uncancelable
```

**After (Monix 4.x):**
```scala
val task = Task.uncancelable { poll =>
  // Explicitly opt into cancellation with poll
  poll(Task.sleep(1.second))
}
```

#### 2. Timer Removal

**Before:**
```scala
def delayed[F[_]: Sync: Timer](fa: F[A]): F[A] =
  Timer[F].sleep(1.second) >> fa
```

**After:**
```scala
def delayed[F[_]: Temporal](fa: F[A]): F[A] =
  Temporal[F].sleep(1.second) >> fa
```

#### 3. ContextShift Removal

**Before:**
```scala
def shifted[F[_]: Sync: ContextShift](fa: F[A]): F[A] =
  ContextShift[F].shift >> fa
```

**After:**
```scala
// CE3 handles fairness automatically, just use:
def shifted[F[_]: Async](fa: F[A]): F[A] = fa
```

... (more examples)
```

---

## Risk Assessment & Mitigation

### High-Risk Areas

| Area | Risk Level | Mitigation Strategy |
|------|-----------|---------------------|
| **uncancelable semantics** | VERY HIGH | Extensive property-based testing, manual verification of critical paths |
| **bracketCase changes** | HIGH | Comprehensive resource leak tests, use scalafix for automated migration |
| **Timer → Temporal** | HIGH | Thorough integration testing with real schedulers |
| **Binary compatibility** | CRITICAL | Accept as Monix 4.0, provide clear migration guide |
| **Performance regressions** | MEDIUM | Benchmark suite, accept minor regressions for safety |
| **Ecosystem compatibility** | HIGH | Coordinate with FS2, Http4s, Doobie for joint migration |

### Mitigation Strategies

#### 1. Scalafix Rules

Create automated migration rules:

```scala
// rules/CatsEffect3Migration.scala
rule CatsEffect3Migration
rule NoContextShift
rule TimerToTemporal
rule BracketCaseToOutcome
```

#### 2. Parallel Development

- Maintain CE2 support in `series/3.x` branch
- Develop CE3 support in `series/4.x` branch
- Cross-publish both for 6-12 months

#### 3. Incremental Migration

Allow users to migrate gradually:

```scala
// Compatibility shims
object Compat {
  type ContextShift[F[_]] = Unit  // No-op
  implicit def contextShiftUnit[F[_]]: ContextShift[F] = ()
  
  type Timer[F[_]] = Temporal[F]
  implicit def timerFromTemporal[F[_]: Temporal]: Timer[F] = 
    Temporal[F]
}
```

---

## Timeline & Milestones

### Optimistic Timeline (16 weeks)

| Week | Phase | Deliverable |
|------|-------|-------------|
| 1-2  | Phase 1 | Dependencies updated, compiles with CE3 |
| 3-6  | Phase 2 | Typeclass instances updated, basic tests pass |
| 7-11 | Phase 3 | Task internals migrated, uncancelable working |
| 12-13| Phase 4 | Monix-catnap migrated |
| 14-15| Phase 5 | Monix-tail migrated |
| 16-20| Phase 6 | All tests passing, benchmarks stable |
| 21-23| Phase 7 | Documentation complete, migration guide |
| 24   | Release | Monix 4.0.0-M1 milestone release |

### Conservative Timeline (24 weeks)

Add 50% buffer for:
- Unforeseen semantic issues with CE3
- Community feedback on milestones
- Performance optimization
- Ecosystem coordination

---

## Success Criteria

### Must Have

- [ ] All Cats-Effect 3 typeclass instances implemented
- [ ] `uncancelable` with Poll semantics working correctly
- [ ] All existing tests passing (updated for CE3)
- [ ] No resource leaks in bracket/guarantee operations
- [ ] Law tests passing for all typeclasses
- [ ] Binary artifacts published for Scala 2.12, 2.13, 3.x
- [ ] Migration guide published

### Should Have

- [ ] Performance within 10% of CE2 version
- [ ] Scalafix migration rules available
- [ ] Example applications migrated
- [ ] FS2/Http4s integration examples
- [ ] Coordinated release with ecosystem

### Nice to Have

- [ ] Performance improvements in specific areas
- [ ] Additional CE3-only features (Dispatcher, etc.)
- [ ] Enhanced tracing with CE3's built-in support
- [ ] Improved IDE experience with simpler constraints

---

## Alternative Approaches Considered

### 1. Shim Layer Approach

**Description:** Create a compatibility layer that translates CE2 to CE3

**Pros:**
- Faster initial migration
- Less code changes

**Cons:**
- Hidden complexity and bugs
- Performance overhead
- Doesn't leverage CE3 improvements
- Tech debt

**Decision:** Rejected - not sustainable long-term

### 2. Fork CE2 Support

**Description:** Maintain internal fork of CE2 with security updates

**Pros:**
- No migration needed
- Binary compatibility maintained

**Cons:**
- Diverges from ecosystem
- Maintenance burden
- Blocks adoption of CE3-based libraries
- Community fragmentation

**Decision:** Rejected - isolates Monix from ecosystem

### 3. Dual Publication

**Description:** Publish both CE2 and CE3 versions simultaneously

**Pros:**
- Users can migrate at their own pace
- Maintains compatibility

**Cons:**
- Double maintenance burden
- Version confusion
- Resource intensive

**Decision:** Use for transition period only (6-12 months)

---

## Ecosystem Impact

### Downstream Libraries Affected

Libraries depending on Monix that will need updates:

- **monix-bio** - Uses Task extensively
- **monix-connect** - Kafka, S3, Redis connectors
- **monix-reactive** - May need reactive-streams updates
- Any library using `ConcurrentEffect[Task]`

### Coordination Needed

- **Typelevel ecosystem**: Align with FS2 3.x, Http4s 1.x migration timelines
- **Lightbend ecosystem**: Coordinate with Akka users migrating to FS2/Monix
- **Community**: Early access builds, feedback cycles

---

## Conclusion

Migrating to Cats-Effect 3.x is **feasible but challenging**. The primary obstacles are:

1. **Interruption model redesign** - Requires deep changes to Task internals
2. **API surface changes** - Breaks binary compatibility, necessitates major version bump
3. **Ecosystem coordination** - Requires alignment with broader Typelevel ecosystem

**Recommendation:** Proceed with migration as Monix 4.0, with:
- 4-6 month development cycle
- Milestone releases for community feedback
- Comprehensive migration guide and tooling
- 6-12 month parallel support for Monix 3.x (CE2)

The benefits of CE3 (safer cancellation, better composition, ecosystem alignment) outweigh the migration costs, but this should be a well-planned, deliberate process with extensive testing and user communication.
