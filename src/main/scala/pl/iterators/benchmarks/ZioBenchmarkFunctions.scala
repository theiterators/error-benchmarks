package pl.iterators.benchmarks
import org.openjdk.jmh.infra.Blackhole
import scalaz.zio._

import scala.util.Random

trait ZioBenchmarkFunctions {

  def outsideWorldEitherZio(
      threshold: Double,
      baseTokens: Int,
      timeFactor: Int)(input: Data): UIO[Either[UhOh, Output]] = UIO {
    Blackhole.consumeCPU(timeFactor * baseTokens)
    if (Random.nextDouble() > threshold) Right(Output(input.i))
    else Left(UhOh(Random.nextString(10)))
  }

  def outsideWorldZio(threshold: Double, baseTokens: Int, timeFactor: Int)(
      input: Data): Task[Output] =
    Task {
      Blackhole.consumeCPU(timeFactor * baseTokens)
      if (Random.nextDouble() > threshold) Output(input.i)
      else throw UhOhException(UhOh(Random.nextString(10)))
    }

  def doZioWithFailure(baseTokens: Int, timeFactor: Int)(
      error: UhOh): UIO[Unit] = UIO {
    Blackhole.consumeCPU(timeFactor * baseTokens)
    ()
  }

  def doZioWithOutput(baseTokens: Int, timeFactor: Int)(
      output: Output): UIO[Result] = UIO {
    Blackhole.consumeCPU(timeFactor * baseTokens)
    Result(output.i)
  }

  def fetchDataZio(baseTokens: Int, timeFactor: Int)(input: ValidInput) = UIO {
    Blackhole.consumeCPU(timeFactor * baseTokens)
    Data(input.i)
  }
}
