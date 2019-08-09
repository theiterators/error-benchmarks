package pl.iterators.benchmarks
import org.openjdk.jmh.infra.Blackhole

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Random

trait BenchmarkFunctions {

  def validateEitherStyle(threshold: Int)(input: Input): Either[Invalid, ValidInput] =
    if (input.i < threshold) Right(ValidInput(input.i))
    else Left(Invalid(input.i))

  def validateExceptionStyle(threshold: Int)(input: Input): ValidInput =
    if (input.i < threshold) ValidInput(input.i)
    else throw InvalidException(input.i)

  def transform(baseTokens: Int)(validInput: ValidInput): ValidInput = {
    Blackhole.consumeCPU(baseTokens)
    validInput.copy(i = Random.nextInt())
  }

  def fetchData(baseTokens: Int, timeFactor: Int)(input: ValidInput)(implicit ec: ExecutionContext) = Future {
    Blackhole.consumeCPU(timeFactor * baseTokens)
    Data(input.i)
  }

  def outsideWorldEither(threshold: Double, baseTokens: Int, timeFactor: Int)(
      input: Data
  )(implicit ec: ExecutionContext): Future[Either[UhOh, Output]] = Future {
    Blackhole.consumeCPU(timeFactor * baseTokens)
    if (Random.nextDouble() > threshold) Right(Output(input.i))
    else Left(UhOh(Random.nextString(10)))
  }

  def outsideWorldException(threshold: Double, baseTokens: Int, timeFactor: Int)(
      input: Data
  )(implicit ec: ExecutionContext): Future[Output] =
    Future {
      Blackhole.consumeCPU(timeFactor * baseTokens)
      if (Random.nextDouble() > threshold) Output(input.i)
      else throw UhOhException(UhOh(Random.nextString(10)))
    }

  def doSomethingWithFailure(baseTokens: Int, timeFactor: Int)(error: UhOh)(implicit ec: ExecutionContext): Future[Unit] = Future {
    Blackhole.consumeCPU(timeFactor * baseTokens)
    ()
  }

  def doSomethingWithOutput(baseTokens: Int, timeFactor: Int)(output: Output)(implicit ec: ExecutionContext): Future[Result] = Future {
    Blackhole.consumeCPU(timeFactor * baseTokens)
    Result(output.i)
  }

}
