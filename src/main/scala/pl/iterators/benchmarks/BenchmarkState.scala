package pl.iterators.benchmarks
import org.openjdk.jmh.annotations._

import scala.util.Random

case class Input(i: Int)
case class ValidInput(i: Int)
case class Data(i: Int)
case class Output(i: Int)
case class Result(i: Int)

sealed trait ThisIsError        extends Product with Serializable
case class Invalid(input: Int)  extends ThisIsError
case class UhOh(reason: String) extends ThisIsError

case class InvalidException(input: Int) extends RuntimeException("Invalid") with ThisIsError
case class UhOhException(uhOh: UhOh)    extends RuntimeException(uhOh.reason) with ThisIsError

@State(Scope.Benchmark)
class BenchmarkState {

  @Param(Array("80"))
  var validInvalidThreshold: Int = _

  val max: Int = 100

  @Param(Array("0.1"))
  var failureThreshold: Double = _

  @Param(Array("5"))
  var timeFactor: Int = _

  @Param(Array("10"))
  var baseTimeTokens: Int = _

  def getSampleInput: Input = Input(Random.nextInt(max))
}
