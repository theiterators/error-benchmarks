package pl.iterators.benchmarks
import cats.effect.IO
import org.openjdk.jmh.infra.Blackhole

import scala.util.Random

trait IoBenchmarkFunctions {

  def outsideWorldEitherIo(threshold: Double, baseTokens: Int, timeFactor: Int)(
      input: Data): IO[Either[UhOh, Output]] = IO {
    Blackhole.consumeCPU(timeFactor * baseTokens)
    if (Random.nextDouble() > threshold) Right(Output(input.i))
    else Left(UhOh(Random.nextString(10)))
  }

  def outsideWorldIo(threshold: Double, baseTokens: Int, timeFactor: Int)(
      input: Data): IO[Output] =
    IO {
      Blackhole.consumeCPU(timeFactor * baseTokens)
      if (Random.nextDouble() > threshold) Output(input.i)
      else throw UhOhException(UhOh(Random.nextString(10)))
    }

  def fetchDataIo(baseTokens: Int, timeFactor: Int)(input: ValidInput) = IO {
    Blackhole.consumeCPU(timeFactor * baseTokens)
    Data(input.i)
  }

  def doIoWithFailure(baseTokens: Int, timeFactor: Int)(error: UhOh): IO[Unit] =
    IO {
      Blackhole.consumeCPU(timeFactor * baseTokens)
      ()
    }

  def doIoWithOutput(baseTokens: Int, timeFactor: Int)(
      output: Output): IO[Result] = IO {
    Blackhole.consumeCPU(timeFactor * baseTokens)
    Result(output.i)
  }

}
