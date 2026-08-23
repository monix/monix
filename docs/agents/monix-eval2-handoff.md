# monix-eval2 Cats Effect 3 handoff

Date: 2026-08-23

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
