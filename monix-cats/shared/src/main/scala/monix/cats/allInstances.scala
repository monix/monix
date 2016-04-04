package monix.cats

/** Groups all instances in a single trait. */
trait AllInstances extends TaskInstances
  with CoevalInstances
  with ObservableInstances
  with AsyncIteratorInstances
  with LazyIteratorInstances
