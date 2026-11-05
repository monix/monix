# Monix

Monix models asynchronous computations and event streams, including their cancellation lifecycle.

## Language

**Cancellation request**:
A request for an asynchronous process to stop. The request alone does not establish that the process has stopped.

**Cancellation completion**:
The terminal outcome in which a process has stopped through cancellation and its finalizers have finished executing, regardless of their outcome.

**Cancellation notification**:
A report that a source was cancelled and its result is unavailable. It describes the source's outcome and does not request cancellation of its consumers.
