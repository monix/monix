package monix.cats

import monix.cats.tests.SequenceableTests
import monix.eval.LazyIterator

object LazyIteratorLawsSuite extends BaseLawsSuite {
  checkAll("Sequenceable[LazyIterator]", SequenceableTests[LazyIterator].sequenceable[Int,Int,Int])
}