package monix.cats

import monix.eval.AsyncIterator
import monix.cats.tests.SequenceableTests

object AsyncIteratorLawsSuite extends BaseLawsSuite {
  checkAll("Sequenceable[AsyncIterator]", SequenceableTests[AsyncIterator].sequenceable[Int,Int,Int])
}