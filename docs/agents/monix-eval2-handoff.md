# monix-eval2 Cats Effect 3 handoff

Date: 2026-08-23

## Fresh-context review follow-up (2026-11-04)

The fresh reviewer examined commit `c2db1488` against `ee5f939c`, with no prior
conversation context. The review found no standards issues and one specification
issue. The implementation changes below address that finding.

### Standards

No findings introduced by `c2db1488`.

### Spec

#### 1. [Medium] Inline `evalOn` executes outside the target context's scope

**Location at reviewed commit:**
`monix-eval2/shared/src/main/scala/monix/eval/internal/TaskFiber.scala:323-326`.

When an inline target execution context installs thread-local state around
`Runnable.run()`, the run-entry trampoline queues the interpreter and returns before
evaluating the effect. The target then restores its state. The trampoline subsequently
evaluates the effect outside that scope, breaking contexts which install tracing or
other thread-local state. Cats Effect's `evalOn` contract requires execution of the
effect on the supplied context.

A probe installed `"target"` in a `ThreadLocal[String]` in the target's execution wrapper
and evaluated `evalOn(Task.delay(local.get()), target)`. Commit `c2db1488` returned
`null`; the same runtime with direct interpreter entry returned `"target"`. Neither
run reported scheduler errors.

The repair trampolines scheduler submissions instead of task bodies. Accepted work
executes inside its scheduler's wrapper. Child startup and cancellation acknowledge
submission success or rejection before continuing; callback wakeups also use the
submission trampoline. This bounds recursive internal submissions on inline schedulers.
Deferred batch/context submissions own rejection recovery, re-entering the origin
context when it accepts work and falling back to local recovery when it refuses.

The atomic ownership protocol is unchanged, and no locks were added. Submission
runnables are allocated per handoff.

The readability pass keeps interpreter execution in `runLoop(executionModel)` and
submission/rejection handling outside it. Recovery selects `SynchronousExecution`
instead of passing a numeric limit. `Start` uses the existing suspension callback
directly, without an intermediate task or registration closure. A context restoration
which already matches the current scheduler and context continues without a handoff.
That last simplification fixes a measured recovery failure: when target and origin
both rejected, a redundant third submission replaced the target error with the origin
error. The regression now requires two submissions, the original error, and exactly
one finalizer execution.

### Spec sources and verification

Read: the user's concurrency, performance, maintainability, and Cats Effect 3
requirements; `AGENTS.md`; `CONTRIBUTING.md`; the code-reviewing skill's smell baseline;
this handoff; the changed implementation and tests; local
`monix-execution/shared/src/main/scala/monix/execution/internal/Trampoline.scala:41-46,83-93`;
and Cats Effect 3.7.1
[`Async.scala`](https://github.com/typelevel/cats-effect/blob/v3.7.1/kernel/shared/src/main/scala/cats/effect/kernel/Async.scala#L174-L180),
[`IO.scala`](https://github.com/typelevel/cats-effect/blob/v3.7.1/core/shared/src/main/scala/cats/effect/IO.scala#L375-L384),
and [`IOFiber.scala`](https://github.com/typelevel/cats-effect/blob/v3.7.1/core/shared/src/main/scala/cats/effect/IOFiber.scala#L663-L694).
Historical upstream references were not independently re-reviewed.

| Check | Result |
| --- | --- |
| `sbt --client '+eval2JVM/test'` | 169/169 on Scala 2.13.18 and 3.3.7 |
| `sbt --client '+eval2JS/test'` | 157/157 on Scala 2.13.18 and 3.3.7 |
| Focused regressions and Cats Effect laws after removing benchmark overrides | 128/128 |
| `sbt --client scalafmtCheckAll` | Passed |
| `git diff --check` | Passed |

A temporary real-executor harness compared the final runtime with `c2db1488` using
fixed two-thread scheduler and producer executors. Three warmups and nine samples
gave the following median sample durations; these are diagnostic measurements, not
JMH results or per-operation latency guarantees.

| Workload | `c2db1488` | Final runtime |
| --- | ---: | ---: |
| Pure/flatMap, 10,000 steps | 2.352 ms | 2.053 ms |
| Async callbacks, 128 steps | 12.596 ms | 13.387 ms |
| Start/join, 128 steps | 1.577 ms | 1.485 ms |

The async samples showed a warmup trend. A bounded follow-up used 20 warmups and
15 samples, measuring 14.292 ms for `c2db1488` and 13.856 ms for the final runtime.
The ranges overlapped (13.697–15.692 ms and 13.715–14.412 ms respectively), so the
results do not establish a speedup or a consistent regression. Temporary source
overrides were removed before the final focused rerun. Logs and harnesses are under
`/tmp/codex/eval2-concurrency-audit/`.

The fresh reviewer also inspected the final repair and found no further concrete
issue. `HEAD` remains `c2db1488f61af263dc8f9a953eaba513b95ae39b`; the index is empty.

## Independent review follow-up (2026-11-04)

Review target: `monix-eval2` at baseline HEAD `ee5f939cab91dc0558f1696a069bb4117bf013db`.
This section records the independent review dispositions and fixes in the current
worktree. The review report remains at `monix-eval2-review.md`.

### Standards

All four low-severity findings were accepted and fixed:

1. Deleted the unused `monix-eval2/shared/src/main/scala/monix/eval/internal/StackFrame.scala`.
2. Removed the unused `TaskFiber` constructor parameters `initCallStack` and
   `initIsCancelled`.
3. Removed unreachable async boundary-policy machinery. No caller used non-synchronous
   `AsyncSimple` policies; the run-loop's Boolean reschedule decision remains.
4. Added `@tailrec` to `TaskCallbackIndirection.register`.

### Spec

1. **Async callback scheduling — rejected as stated.** A callback that finds a fiber
   active publishes into its run loop without a scheduler submission. A callback that
   finds it inactive schedules on the fiber's current execution context. Cats Effect 3.7.1
   also schedules a fiber on its current context for that path. Making all resumptions
   inline would risk `evalOn` context affinity. See pinned
   [`IOFiber.scala`](https://github.com/typelevel/cats-effect/blob/v3.7.1/core/shared/src/main/scala/cats/effect/IOFiber.scala#L663-L694)
   and
   [`IO.scala`](https://github.com/typelevel/cats-effect/blob/v3.7.1/core/shared/src/main/scala/cats/effect/IO.scala#L375-L384).

2. **Dedicated `Map` node — allocation concern accepted; implementation deferred.**
   `map` currently creates a closure and encodes it as `flatMap` plus `Pure`. That is a
   real representation cost. The reported approximate twofold runtime cost was not
   measured in this implementation. Defer a `Map` node and continuation-frame change
   until workload-specific allocation benchmarks establish its benefit.

3. **Batch resubmission on inline execution contexts — reproduced and fixed.**
   A 10,000-map chain on a direct inline execution context with
   `AlwaysAsyncExecution` overflowed the stack; the same chain passed on
   `ExecutionContext.parasitic`. The run-loop now uses the cached
   `TrampolineExecutionContext.immediate` to enter its batch loop iteratively after
   scheduler dispatch. This adds one cached `Runnable` per fiber and preserves scheduler
   selection and batch yields. The throughput impact has not been benchmarked. The
   focused adversarial suite passed 10/10; the strengthened affinity test passed 1/1 on
   each Scala version.

4. **Stale `IO` naming — fixed.** Changed implementation comments, law helper names,
   and test labels to identify this implementation as `Task`.

5. **`monix.eval.Task` classpath collision — accepted as an experiment constraint.**
   Both eval2 and legacy eval publish this class name. This review leaves the package,
   public API, and publication settings unchanged. Downstream users must select one
   implementation on a classpath.

### Sources and verification

Sources read for this review: the user requirements in the conversation; `AGENTS.md`;
`CONTRIBUTING.md`; `monix-eval2-review.md`; the runtime and its tests; and Cats Effect
3.7.1 source. The scheduler and cancellation comparisons use pinned
[`IOFiber.scala`](https://github.com/typelevel/cats-effect/blob/v3.7.1/core/shared/src/main/scala/cats/effect/IOFiber.scala#L1346-L1375)
as well as the links above.

Final verification:

| Command or check | Result |
| --- | --- |
| `sbt --client '+eval2JVM/test'` | 164/164 on each of Scala 2.13.18 and 3.3.7 |
| `sbt --client '+eval2JS/test'` | 154/154 on each of Scala 2.13.18 and 3.3.7 |
| `TaskAsyncResumeThreadJVMSuite` | 1/1 on each Scala version |
| `sbt --client scalafmtCheckAll` | Passed |
| `git diff --check` | Passed |

The full JVM matrix ran before the final test-only affinity strengthening; the
strengthened suite was then rerun on both Scala versions. HEAD remains
`ee5f939cab91dc0558f1696a069bb4117bf013db`, and the index is empty.

## Concurrency audit follow-up (2026-11-04)

Audit baseline: `HEAD 5a5979a991cf2efebc7c9fab2638e86f96d1793f`; the worktree was clean
at the start of the audit.

### Standards

No new locks or warning suppressions were introduced. `TaskFiber` retains its single
atomic ownership protocol.

### Spec

#### 1. [High] Rejected run-loop handoff could strand an active fiber

**Location:** `monix-eval2/shared/src/main/scala/monix/eval/internal/TaskFiber.scala:257-272,334-414`.

A rejected scheduler submission could leave run-loop ownership active without a runnable.
A rejected child `Start` submission could also leave the parent pending without running
its finalizer. Rejected child submission now becomes an error in the parent, and the child
does not run. Rejected run-loop handoff restores the prior scheduler and execution context
before recovery. Recovery also avoids repeatedly resubmitting error and finalizer work to
the same rejecting queue.

#### 2. [High] Callback failures could interrupt join and `Deferred` notification

**Location:** `monix-eval2/shared/src/main/scala/monix/eval/internal/TaskRestartCallback.scala:56-79`;
`monix-eval2/shared/src/main/scala/monix/eval/internal/TaskFiber.scala:485-508`.

A rejected callback resumption could escape into sequential `Deferred.complete` waiter
notification after the value was published, preventing later healthy waiters from
resuming. Callback delivery now reports non-fatal scheduling failures to its scheduler.
Fiber completion catches failures from each join listener and continues notifying the
remaining listeners.

Five deterministic regressions are in
`monix-eval2/shared/src/test/scala/monix/eval/TaskCancellationAdversarialSuite.scala`:
rejected joiner, rejected `Deferred` waiter, rejected `evalOn` shift, and rejected batch
resubmission, plus rejected child submission with parent finalization. Two JVM contention
scenarios are in
`monix-eval2/jvm/src/test/scala/monix/eval/TaskMaskedContentionJVMSuite.scala`. They race
a masked callback with four cancelers while a gated finalizer is pending, and exercise
both same-scheduler and cross-scheduler `evalOn` paths. The suite runs 1,000 local rounds
and 100 rounds in CI.

### Scope and limits

The rejection path assumes `RejectedExecutionException` means the runnable was not
accepted, and scheduler reporters return normally. Callback isolation does not make a
receiver runnable when its own executor refuses resumption; it keeps that failure from
blocking healthy consumers and producers.

A captured-`Poll` cancellation-finalizer probe against Cats Effect 3.7.1 skipped the
finalizer. This case remains ambiguous, so no masking change was made. The probe source
and logs are under `/tmp/codex/eval2-concurrency-audit`; they are not production tests.
No lost-wakeup failure was observed in the tested races with functioning schedulers.
This is not proof of race freedom.

### Sources

- User concurrency requirements and the prior handoff in this document. Read.
- Repository standards in `AGENTS.md` and `CONTRIBUTING.md`. Read.
- Legacy rejection and callback conventions in
  `monix-eval/jvm/src/test/scala/monix/eval/TaskRejectedExecutionSuite.scala` and
  `monix-eval/shared/src/main/scala/monix/eval/internal/TaskCreate.scala`. Read.
- Cats Effect 3.7.1 [`Deferred.scala`](https://github.com/typelevel/cats-effect/blob/v3.7.1/kernel/shared/src/main/scala/cats/effect/kernel/Deferred.scala),
  [`IOFiber.scala`](https://github.com/typelevel/cats-effect/blob/v3.7.1/core/shared/src/main/scala/cats/effect/IOFiber.scala),
  [`MonadCancel.scala`](https://github.com/typelevel/cats-effect/blob/v3.7.1/kernel/shared/src/main/scala/cats/effect/kernel/MonadCancel.scala),
  [`GenSpawn.scala`](https://github.com/typelevel/cats-effect/blob/v3.7.1/kernel/shared/src/main/scala/cats/effect/kernel/GenSpawn.scala),
  [`CallbackStack.scala`](https://github.com/typelevel/cats-effect/blob/v3.7.1/core/jvm-native/src/main/scala/cats/effect/CallbackStack.scala),
  and the corresponding `DeferredSuite`, `IOSuite`, and `CallbackStackSuite`. Read.

### Verification

| Command | Result |
| --- | --- |
| `sbt --client '+eval2JVM/test'` | 161/161 passed on Scala 2.13.18 and 3.3.7 |
| `sbt --client '+eval2JS/test'` | 152/152 passed on Scala 2.13.18 and 3.3.7 |
| `sbt --client scalafmtCheckAll` | Passed |
| `git diff --check` | Passed |

The focused regression suites passed 7/7. `HEAD` remains
`5a5979a991cf2efebc7c9fab2638e86f96d1793f`; the index is empty.

## Review follow-up (2026-11-04)

## Standards

No outstanding standards findings were identified. The review used repository rules
in `AGENTS.md` and `CONTRIBUTING.md`, plus the code-reviewing skill's smell baseline.

## Spec

Cats Effect pins in eval2 moved from 3.7.0 to 3.7.1 in `build.sbt:711-715`. The shared
Cats Effect 2 dependency remains unchanged. The Task algebra also gained dedicated
`Delay` and `Defer` nodes in `Task.scala:88-92,279-287`. All seven findings below were
addressed and verified.

### 1. [High] Stale async callbacks could resume cancellation finalizers

**Location:** `monix-eval2/shared/src/main/scala/monix/eval/internal/TaskFiber.scala:147-159,307-325`; `monix-eval2/shared/src/main/scala/monix/eval/internal/TaskRestartCallback.scala:27-67`

An async callback from an abandoned suspension could publish into a later suspended
cancellation finalizer, causing premature completion or corrupting its result. Each
suspension now has a distinct callback identity in the same atomic `Active` snapshot.
The fiber rejects stale callback identities, and registration runs under run-loop
ownership. **Status: addressed and verified.**

### 2. [High] `evalOn` could lose self-cancellation and `Poll` identity

**Location:** `monix-eval2/shared/src/main/scala/monix/eval/internal/TaskFiber.scala:269-298`

The child-based shift could make cancellation or a `Poll` token apply to a different
fiber. `evalOn` now shifts the same fiber and restores its scheduler after success,
error, or cancellation. **Status: addressed and verified.**

### 3. [High] Sequential `racePair` cancellation could deadlock

**Location:** `monix-eval2/shared/src/main/scala/monix/eval/instances/CatsAsyncForTask.scala:121-126`

If child A's cancellation finalizer waits for child B to be canceled, awaiting A's
cancellation before requesting B's cancellation can deadlock. The implementation now
requests cancellation of both children concurrently and awaits both. **Status:
addressed and verified.**

### 4. [Medium] A canceled `Cont.get` prevented a later legal get

**Location:** `monix-eval2/shared/src/main/scala/monix/eval/internal/TaskCallbackIndirection.scala:61-70`; `monix-eval2/shared/src/main/scala/monix/eval/internal/TaskFiber.scala:162-167`

Cancellation left the continuation state in `Waiting`, so a later sequential `get`
failed registration. The cancellation cleanup now resets `Waiting` to `Init`; an
already delivered result remains available to the next get. **Status: addressed and
verified.**

### 5. [Medium] Unbounded synchronous work could starve other fibers

**Location:** `monix-eval2/shared/src/main/scala/monix/eval/internal/TaskFiber.scala:328-389`

On constrained schedulers, a computation that did not suspend could delay queued fibers
and cancelers indefinitely. The run loop now yields after a bounded batch based on the
scheduler's execution model. **Status: addressed and verified.**

### 6. [Medium] Popped continuation frames retained captured references

**Location:** `monix-eval2/shared/src/main/scala/monix/eval/internal/TaskCallStack.scala:42-57`; `monix-eval2/shared/src/main/scala/monix/eval/internal/TaskFiber.scala:460-471`

Popped frames could retain captured objects until the array slots were overwritten or
the fiber was collected. The stack now clears popped slots, and completion clears the
fiber's interpreter references. **Status: addressed and verified.**

### 7. [Medium] `evalOn` did not preserve a plain execution context's identity

**Location:** `monix-eval2/shared/src/main/scala/monix/eval/internal/TaskFiber.scala:54-57,102-103,256-298`

For a plain `ExecutionContext`, `evalOnLocalPure` requires
`executionContext` to return the exact supplied context. The scheduler adapter was
reported instead, and child fibers started inside `evalOn` inherited that wrapper. The
implementation now retains the original context alongside its scheduler adapter and
passes it to child fibers. **Status: addressed and verified.**

The selected callback-boundary optimization makes the default `AsyncSimple`
before/after policies `Synchronous`. Registration already runs under fiber ownership,
and atomic callback publication prevents reentrant interpretation. A callback arriving
after the fiber becomes inactive still schedules it on its owning scheduler. Explicit
boundary policies remain available (`Task.scala:181-192`).

Deterministic regression cases are in
`monix-eval2/shared/src/test/scala/monix/eval/TaskContRegressionSuite.scala`,
`monix-eval2/shared/src/test/scala/monix/eval/TaskRuntimeRegressionSuite.scala`, and
`monix-eval2/shared/src/test/scala/monix/eval/internal/TaskCallStackSuite.scala`.
JVM race regression cases are in
`monix-eval2/jvm/src/test/scala/monix/eval/TaskCallbackSafetyJVMSuite.scala`.

Final verification used these commands:

| Command | Result |
| --- | --- |
| `sbt --client '+eval2JVM/test'` | 154/154 passed on each of Scala 2.13.18 and 3.3.7 |
| `sbt --client '+eval2JS/test'` | 147/147 passed on each of Scala 2.13.18 and 3.3.7 |
| `sbt --client scalafmtCheckAll` | Passed |
| `git diff --check` | Passed |

The temporary benchmark harness was removed from the build, the normal build was
restored, and the final normal-source compile passed. No `Eval2PerfHarness` class
remains in the project compile output.

### Exploratory allocation measurements

Measurements used 10,000 operations, at least 2 seconds and 15 warmups, 5 samples,
Scala 2.13.18, JDK 21, and `TestScheduler`. The HEAD baseline was
`a01a0de3a54217152c616343cb7fa0ea308829c6` plus only the temporary intentional
discarded-token binding.

| Workload | Median B/op, baseline → final |
| --- | ---: |
| pure / flatMap / delay | 188.27 → 151.03 |
| synchronous public async | 247.60 → 251.92 |
| start / join | 1929.65 → 1850.20 |
| same-scheduler `evalOn` | 2377.65 → 116.49 |

These are exploratory single-thread `TestScheduler` allocation results, not JMH or
production-throughput measurements. Timing variance across profiles is substantial,
so these results do not support a general speedup claim.

### Remaining opportunities

Canceled join listeners remain retained until the target fiber completes
(`TaskFiber.scala:400-403`), and the losing `racePair` observer remains joined after a
winner (`CatsAsyncForTask.scala:116-118`). These are documented existing behaviors, not
new regressions. Real-executor and JMH benchmarks are still needed to measure
performance under production-like scheduling and load.

## Spec sources

- User requirements from this session: Cats Effect 3 compatibility, performance,
  non-blocking concurrency without `synchronized`, race safety, and maintainability;
  plus the request to use the latest Cats Effect 3.x dependency. Read.
- `docs/agents/monix-eval2-handoff.md` original 2026-08-23 handoff. Read.
- Cats Effect 3.7.1 [`Async.scala`](https://github.com/typelevel/cats-effect/blob/v3.7.1/kernel/shared/src/main/scala/cats/effect/kernel/Async.scala),
  [`Cont.scala`](https://github.com/typelevel/cats-effect/blob/v3.7.1/kernel/shared/src/main/scala/cats/effect/kernel/Cont.scala),
  [`IOFiber.scala`](https://github.com/typelevel/cats-effect/blob/v3.7.1/core/shared/src/main/scala/cats/effect/IOFiber.scala),
  and [`AsyncLaws.scala`](https://github.com/typelevel/cats-effect/blob/v3.7.1/laws/shared/src/main/scala/cats/effect/laws/AsyncLaws.scala). Read.
- Cats Effect [v3.7.1 release](https://github.com/typelevel/cats-effect/releases/tag/v3.7.1)
  and Maven Central metadata for
  [`cats-effect-kernel_2.13`](https://repo.maven.apache.org/maven2/org/typelevel/cats-effect-kernel_2.13/maven-metadata.xml),
  which report 3.7.1 as the latest stable release. Read.

The URLs in the historical handoff below remain a reference inventory; they were not
re-reviewed for this follow-up. No external issue or pull request was supplied.

Review summary: 0 outstanding standards findings; 7 spec findings addressed and
verified (3 High, 4 Medium). This is not an exhaustive proof of race freedom. The
historical verification table below applies only to the original handoff snapshot.

> **Historical snapshot (2026-08-23):** The original handoff content starts below and
> is retained as recorded on that date.

## Scope

`monix-eval2` is an experimental `Task` implementation for Cats Effect 3. The main sbt
project is `eval2JVM`; `eval2JS` exercises the same shared implementation on Scala.js.
It is separate from the existing Cats Effect 2-based `monix-eval` and `Task`.

The implementation, Cats Effect law integration, explanatory comments, deterministic
async tests, and JVM contention tests are complete and passing. There is no known
failing test or unfinished source change at this handoff.

The worktree was clean immediately before this document was added.

## Current verification

Always use sbt client mode:

```text
sbt --client scalafmtCheckAll
sbt --client '+eval2JVM/test'
sbt --client '+eval2JS/test'
```

Last results:

| Target | Scala | Passed | Failed | Errors |
| --- | --- | ---: | ---: | ---: |
| JVM | 2.13.18 | 135 | 0 | 0 |
| JVM | 3.3.7 | 135 | 0 | 0 |
| Scala.js | 2.13.18 | 130 | 0 | 0 |
| Scala.js | 3.3.7 | 130 | 0 | 0 |

`scalafmtCheckAll` also passed. No compiler warnings were emitted.

## Source map

- `monix-eval2/shared/src/main/scala/monix/eval/Task.scala`
  - Public `Task` algebra and constructors.
  - Visitor interface and node tags.
  - Unsafe runners and Cats Effect `Async[Task]` instance export.
- `monix-eval2/shared/src/main/scala/monix/eval/internal/TaskFiber.scala`
  - Run-loop, cancellation masks, finalizer traversal, fiber lifecycle, join, and the
    atomic scheduling protocol.
- `monix-eval2/shared/src/main/scala/monix/eval/internal/TaskRestartCallback.scala`
  - Reusable async callback used to publish success/error nodes back to a fiber.
- `monix-eval2/shared/src/main/scala/monix/eval/internal/TaskCallbackIndirection.scala`
  - `AsyncCont` callback/`get` handshake when either side can arrive first.
- `monix-eval2/shared/src/main/scala/monix/eval/internal/TaskCallStack.scala`
  - Compact continuation stack with frame tags for binds, error handlers, and
    cancellation finalizers.
- `monix-eval2/shared/src/main/scala/monix/eval/internal/StackFrame.scala`
  - Mapping/error-recovery frame abstractions retained in the experiment; they are not
    currently referenced by the eval2 runtime.
- `monix-eval2/shared/src/main/scala/monix/eval/instances/CatsAsyncForTask.scala`
  - Cats Effect 3 `Async`, `Temporal`, `Spawn`, and `Sync` operations implemented in
  terms of the local `Task` algebra.
- `monix-eval2/shared/src/test/scala/monix/eval/TaskAsyncLawsSuite.scala`
  - Official Cats Effect `AsyncTests[Task]` integration.
- `monix-eval2/shared/src/test/scala/monix/eval/TaskSimpleTest.scala`
  - Deterministic behavior, async, cancellation, race, scheduler, and finalizer tests.
- `monix-eval2/jvm/src/test/scala/monix/eval/TaskCallbackSafetyJVMSuite.scala`
  - Repeated real-thread contention tests.

## Task representation and interpretation

Constructors and combinators create lazy nodes. `TaskFiber` interprets them with a
visitor. `Pure`, `RaiseError`, and `FlatMap` use direct tag dispatch in the hot path;
the remaining nodes use visitor dispatch.

`Task.delay` is currently encoded with a bind instead of a dedicated delay node. Bind,
error-handler, uncancelable-body, and `AsyncCont` functions are evaluated by the
run-loop, and thrown `NonFatal` exceptions are converted into `RaiseError` nodes.

The continuation stack stores parallel arrays of function references and integer tags.
Outcome-specific searches discard frames that do not apply:

- success searches for the next flatMap;
- error searches for the next error handler;
- cancellation searches for the next `onCancel` finalizer.

This gives the required error and LIFO cancellation behavior without allocating a
linked continuation object for each frame.

## Fiber ownership and atomic state

`TaskFiber` contains no `synchronized`, monitor, or `@volatile` state. It directly
declares one reference-valued Monix `Atomic`, `stateRef`; on the JVM this is backed by
Monix's `AtomicAny`/atomic-reference implementation.

The shared state is immutable:

```scala
Active(
  listeners: List[Callback[Throwable, Outcome[Task, Throwable, A]]],
  runActive: Boolean,
  isCanceled: Boolean,
  pendingRef: Task[Any]
)

Finished(outcome: Outcome[Task, Throwable, A])
```

The non-atomic interpreter fields (`currentRef`, continuation stack, restart callback,
mask depth, and the initial-run flag) are accessed under ownership established by the
atomic state. CAS and scheduler enqueue/dequeue boundaries provide publication when
execution moves between threads.

### State transitions

| Operation | Active-state transition | Scheduling rule |
| --- | --- | --- |
| Async callback | Store the first `pendingRef`; change inactive to active | Only the callback that changes inactive to active submits the fiber |
| Cancellation | Set `isCanceled`; claim an inactive fiber | Schedule when unmasked; release a masked claim unless a callback raced it |
| Run-loop handoff | Prefer cancellation, then `pendingRef`, otherwise change active to inactive | No executor call while retrying a CAS |
| Join registration | Prepend listener while active | A finished fiber invokes the listener immediately |
| Completion | Replace `Active` with `Finished` | Publish outcome before invoking listeners |

Important invariants:

- At most one `run()` invocation interprets nodes.
- A non-null `pendingRef` implies active ownership.
- `isCanceled` is monotonic until `Finished` replaces the active state.
- Callback and cancellation races cannot lose a wake-up: a failed CAS retries against
  the newly published state.
- Scheduler submission happens after the successful CAS because a scheduler may
  execute inline or through a trampoline.

Do not split these fields into independent atomics. Transitions involving ownership,
cancellation, pending callbacks, completion, and join listeners need one consistent
snapshot.

## Cancellation model

Cancellation follows Cats Effect 3 semantics:

- `Fiber.cancel` is uncancelable and completes only after the target fiber reaches an
  outcome, including completion of registered cancellation finalizers.
- Cancellation requested under `uncancelable` remains pending.
- A matching `Poll` exposes one mask level and observes pending cancellation as soon
  as the depth reaches zero.
- Poll tokens from another fiber or from a mismatched nesting depth are identity
  transformations.
- `onCancel` finalizers run masked and in LIFO order.
- Errors raised by cancellation finalizers are reported to the scheduler; cancellation
  then continues if the reporter returns normally.
- The root Monix `Callback` has success/error channels only, so a canceled unsafe run
  does not signal that callback. `join` publishes `Outcome.Canceled`.
- `unsafeRunToFuture().cancel()` runs the effectful cancel token. The canceled future
  remains incomplete, matching the existing Monix-style bridge behavior.

An inactive masked fiber is not reinterpreted merely because cancellation arrived.
The canceler first acquires the atomic scheduling claim, reads the mask depth through
that happens-before boundary, and releases the claim when no run is needed. If an async
callback publishes before release, the canceler retains the claim and schedules the
callback. If the release CAS wins before callback publication, the callback observes or
retries against the inactive state, claims the fiber, and schedules it itself.

## Async callback paths

`AsyncSimple` uses `TaskRestartCallback`. Registration may signal synchronously or later:

- a callback delivered while a run is active publishes a pending node which that run
  consumes unless completion or unmasked cancellation wins;
- a callback delivered while the fiber is inactive claims it and submits a run;

Public `Task.async` and `Task.async0` wrap registration with `Callback.safe` and
`protectRegistration`:

- only the first callback result is accepted;
- a registration exception before a result becomes the effect error;
- an exception thrown after a result preserves the result and is reported.

A raw internal `AsyncSimple` registration exception reaching `TaskRestartCallback` is
reported as an API contract violation; that lower-level path does not synthesize an
effect result.

`AsyncCont` uses `TaskCallbackIndirection`. Its atomic states are `Init`, `Waiting`,
`Success`, and `Failure`; this supports callback-before-`get` and `get`-before-callback
without blocking.

`CatsAsyncForTask` builds `cede`, `sleep`, `racePair`, and `evalOn` from these primitives.
In particular:

- `cede` submits the continuation to the current scheduler;
- `sleep` installs scheduler cancellation safely against timer completion;
- `racePair` returns the losing fiber without canceling it;
- canceling the parent race cancels both children and waits for them;
- `evalOn` executes the source on the target execution context and returns the
  continuation to the original scheduler.

## Test coverage

### Cats Effect laws

`TaskAsyncLawsSuite` runs:

```scala
AsyncTests[Task].async[Int, Int, Int](10.millis)
```

The suite supplies `Eq` instances, generators, and `TestScheduler`-based unsafe runners.
The `Async[Task]` instance supplies the `Ref` and `Deferred` implementations exercised by
the laws. The recursive effect generator excludes the `racePair`-labeled recursive
case; the `AsyncTests` race and `racePair` laws themselves still execute.

The law source used during implementation was Cats Effect 3.7.0, commit
`506c97821e247180c3474b105648d5acea3339e9`. The user-provided upstream reference was:

`https://github.com/typelevel/cats-effect/tree/series/4.x/laws/shared/src`

### Shared deterministic tests

The hand-written shared suite includes:

- synchronous and scheduled 10,000-step async flatMap stack safety;
- first-callback-wins behavior;
- registration failures before and after a result;
- `AsyncCont` body failure;
- multiple cancels awaiting one finalization;
- cancellation masks, `Poll`, pending cancellation, and finalizer ordering;
- start/join outcomes;
- `racePair` leaving the loser running;
- `cede` rescheduling;
- `evalOn` target/source scheduler handoff;
- async cancellation token plus nested `onCancel` order.

Most scheduler-sensitive shared cases use `TestScheduler`. The legacy `simple flatMap`
test uses the global scheduler and a callback scheduled after one real second.

### JVM contention tests

`TaskCallbackSafetyJVMSuite` follows the established Monix `Task` callback-stress style.
The callback patterns use 10 workers; the join race uses 10 joiners plus one completion
racer; the cancellation/completion race uses two racers. The suite uses 1,000
repetitions locally, reduced to 100 repetitions in CI. It covers:

- exactly one accepted callback under concurrent attempts;
- callback publication racing run-loop suspension;
- fiber completion racing concurrent join registration;
- fiber cancellation racing async completion.

All latches and scheduler termination waits have 10-second bounds.

## Current behavior worth preserving

- A join registration has no removal token. If the joining fiber is canceled, the
  target retains that callback until the target completes.
- Completion publishes the outcome before notifying join listeners. Listeners are
  invoked in registration order after reversing the internally prepended list.
- Listener invocation is not individually guarded; a throwing listener stops later
  notifications.
- A `racePair` winner does not cancel its loser. Parent cancellation is responsible
  for canceling both children while the race is still pending.
- `Callback.empty` ignores successful child values but reports child errors.

## References used

Local Monix implementation and test patterns:

- `monix-eval/shared/src/main/scala/monix/eval/internal/TaskRunLoop.scala`
- `monix-eval/shared/src/main/scala/monix/eval/internal/TaskRestartCallback.scala`
- `monix-eval/shared/src/test/scala/monix/eval/TaskAsyncSuite.scala`
- `monix-eval/shared/src/test/scala/monix/eval/TaskCancellationSuite.scala`
- `monix-eval/shared/src/test/scala/monix/eval/TaskRaceSuite.scala`
- `monix-eval/jvm/src/test/scala/monix/eval/TaskCallbackSafetyJVMSuite.scala`

Cats Effect reference implementation and tests:

- `https://github.com/typelevel/cats-effect/blob/506c97821e247180c3474b105648d5acea3339e9/core/shared/src/main/scala/cats/effect/IOFiber.scala`
- `https://github.com/typelevel/cats-effect/blob/506c97821e247180c3474b105648d5acea3339e9/tests/shared/src/test/scala/cats/effect/IOSuite.scala`
- `https://github.com/typelevel/cats-effect/blob/506c97821e247180c3474b105648d5acea3339e9/laws/shared/src/main/scala/cats/effect/laws/AsyncTests.scala`

## Follow-up guidance

- Run the complete JVM and Scala.js cross-version matrix after modifying the fiber
  state protocol, masks, callback handoff, or Cats Effect instance.
- Add deterministic public-API tests before changing cancellation behavior, then add a
  bounded JVM race test when the change affects cross-thread publication.
- Preserve the one-atomic ownership protocol unless a replacement has an equally clear
  happens-before argument for every callback/cancel/completion race.
- No benchmark suite was run during this implementation. If performance work continues,
  benchmark async suspension/resumption, repeated cancellation, and start/join before
  changing the state representation.
