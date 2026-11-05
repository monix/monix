# Observe cancellation completion through CancelableFuture

Historically, Monix borrowed cancellation semantics from RxJava and Rx.NET: cancellation meant that a subscriber was no longer interested and should unsubscribe. A cold source could close its resources, but `cancel()` did not wait for cleanup, and the subscriber received no cancellation-completion notification. Applied to futures, this could leave other consumers waiting indefinitely after one consumer requested cancellation.

For `CancelableFuture`, `cancel()` remains a cancellation request, while the process determines the terminal outcome. When cancellation takes effect, the future should report cancellation only after the process stops and its finalizers finish executing; finalizer failures must not replace the cancellation signal. This lets all consumers observe termination without treating the request itself as evidence that cleanup has finished.

`CancellationException` reports that the source was cancelled and its result is unavailable; it does not request cancellation of a consumer. A future can have multiple consumers, and each consumer may recover or proceed with other work. Therefore, recovery and transformation utilities retain ordinary `Future` semantics, including allowing a cancellation failure to become a successful derived result.

A process or finalizer that never finishes can leave the future pending. Exceptions that report cancellation before termination must be assessed case by case. The explicit `fromForgettableFuture` conversion below provides one such opt-in policy.

`CancelableFuture.never` never completes normally, but cancellation completes it immediately with a cancellation exception. Independent calls must not share mutable cancellation state.

Cancelling a future obtained from `CancelablePromise.future` cancels that subscription, not the shared producer. It therefore reports cancellation immediately, without waiting for the producer; other subscriptions remain active. This preserves the subscription scope of cancellation for a hot source while making the cancelled subscription's outcome observable.

The completion callback is a reusable protocol for asynchronous processes, including `Task`; it is not specific to `CancelableFuture`. Its API must expose cancellation explicitly, separately from ordinary failure. At the `CancelableFuture` boundary, a signaled cancellation is represented as a failed future containing `CancellationException`; that representation does not replace the callback's explicit cancellation operation.

Introduce `CompletionCallback` and deprecate the existing `Callback` class. Keep backward compatibility where possible, but retain uses of `Callback` only in deprecated methods; active APIs must use the new completion protocol. Mark retained compatibility APIs explicitly as deprecated.

`CompletionCallback` requires implementations of all three methods: `onSuccess`, `onError`, and `onCancellation`. Cancellation has no default implementation, so each callback implementation must explicitly decide how to handle it. The `CancelableFuture` adapter implements `onCancellation` by signaling `CancellationException`.

Defer the continuation-based cancellation-registration API. For this change, asynchronous registration continues to return a `Cancelable`; do not introduce `Continuation` or `invokeOnCancellation`.

The intended model is the classic fiber `cancel` and `join` pair: `cancel()` requests cancellation, while observing the future waits for termination and reports cancellation after cleanup when cancellation takes effect.

Introduce `CancelableFuture.fromAsync` using the new completion callback and deprecate the existing `async` builder while retaining its signature for compatibility.

`CompletionCallback` has one contravariant type parameter for the successful value, and `onError` accepts `Throwable`. It does not inherit from a function type. The deprecated `Callback[E, A]` retains its existing generic error parameter for compatibility.

Cancellation backpressure applies throughout the `CancelableFuture` API wherever the underlying operations permit it, including composition through `flatMap` and `zip`. Cancellation chaining must avoid retaining obsolete references.

If either `zip` input fails, including with `CancellationException`, `zip` requests cancellation of the sibling and waits for it to terminate before reporting failure. Explicitly cancelling the zipped result requests cancellation of both inputs and waits for both to terminate. Composing futures with `zip` therefore permits cancellation of an input even when it has other consumers; the combined operation waits for both inputs to terminate before reporting termination.

For `zip`, preserve the first observed failure as the result's primary exception. If the sibling's failure is also observed, attach it as a suppressed exception to the primary failure.

`CancelableFuture.fromFuture(future)` creates an adapter whose cancellation waits for the original future to complete, then reports `CancellationException` instead of its result. The cancellation request itself remains non-blocking. The original computation cannot be cancelled, so cancellation of the adapter may remain pending indefinitely if that computation never completes. Callers can explicitly construct a different cancellation policy through a builder.

`CancelableFuture.fromForgettableFuture(future)` provides an explicit alternative: cancellation immediately completes the adapter with `CancellationException`, without waiting for the source. The source is not cancelled and may continue running. This opt-in policy does not replace `fromFuture` as the conversion used by combinators.

Both conversion builders return an existing `CancelableFuture` unchanged. Their adapter policies apply only to ordinary futures, preserving an existing cancelable future’s cancellation behavior and avoiding redundant wrappers.

`CancelableFuture` inherits `zip(Future)` from Scala `Future`, so `zip` continues to accept ordinary futures. Such inputs use the conversion policy above: after a cancellation request, their adapters wait for the original futures to complete before reporting cancellation.

Recovery from cancellation establishes a fresh stage: work returned by `recoverWith` may continue and must not be automatically cancelled by the request that cancelled its source. Cancellation during a successful `flatMap` handoff still reaches the next stage. Cancellation chaining must preserve this distinction without retaining obsolete stages.

Implementation must apply these contracts consistently across builders, adapters, and composition, including downstream migration from deprecated callback APIs. Verification must cover termination ordering, cancellation races, recovery, and cancellation-chain retention.
