name := "error-benchmarks"
version := "0.1"
description := "Benchmarks of various functional error handling methods"

inThisBuild(
  Seq(
    organization := "pl.iterators",
    scalaVersion := "2.12.6",
    scalacOptions := Seq(
      "-feature",
      "-deprecation",
      "-unchecked",
      "-Xlint:_",
      "-Ywarn-unused-import",
      "-Ywarn-unused:locals,privates",
      "-Ywarn-adapted-args",
      "-Ypartial-unification",
      "-encoding",
      "utf8",
      "-target:jvm-1.8",
      "-opt:l:inline",
      "-opt-inline-from:**"
    ),
    scalafmtOnCompile := false
  ))

val cats   = "org.typelevel" %% "cats-core"   % "1.6.0"
val catsIo = "org.typelevel" %% "cats-effect" % "1.2.0"
val zio = "org.scalaz" %% "scalaz-zio" % "1.0-RC4"
val zioCats = "org.scalaz" %% "scalaz-zio-interop-cats" % "1.0-RC4"

libraryDependencies ++= Seq(cats, catsIo, zio, zioCats)
addCompilerPlugin("org.spire-math" %% "kind-projector" % "0.9.7")

enablePlugins(JmhPlugin)

addCommandAlias("flame1", "jmh:run -p timeFactor=5 -prof jmh.extras.Async:dir=target/flamegraphs/EitherTFut;flameGraphOpts=--width,1900,--colors,java,--title=EitherT,--subtitle=Future FutBenchmark.eitherT")
addCommandAlias("flame2", "jmh:run -p timeFactor=5 -prof jmh.extras.Async:dir=target/flamegraphs/FutEither;flameGraphOpts=--width,1900,--colors,java,--title=Future,--subtitle=Either FutBenchmark.either")
addCommandAlias("flame3", "jmh:run -p timeFactor=5 -prof jmh.extras.Async:dir=target/flamegraphs/EitherTIo;flameGraphOpts=--width,1900,--colors,java,--title=EitherT,--subtitle=IO IoBenchmark.eitherT")
addCommandAlias("flame4", "jmh:run -p timeFactor=5 -prof jmh.extras.Async:dir=target/flamegraphs/IoEither;flameGraphOpts=--width,1900,--colors,java,--title=IO,--subtitle=Either IoBenchmark.either")
//addCommandAlias("flame5", "jmh:run -p timeFactor=5 -prof jmh.extras.Async:dir=target/flamegraphs/IoEitherAsync;flameGraphOpts=--width,1900,--colors,java,--title=IO,--subtitle=Either IoBenchmark.eitherIoAsync")
addCommandAlias("flame6", "jmh:run -p timeFactor=5 -p failureThreshold=0.45 -p validInvalidThreshold=50 -prof jmh.extras.Async:dir=target/flamegraphs/FutEx;flameGraphOpts=--width,1900,--colors,java,--title=Future,--subtitle=Throwable FutBenchmark.exceptions")
addCommandAlias("flame7", "jmh:run -p timeFactor=5 -p failureThreshold=0.45 -p validInvalidThreshold=50 -prof jmh.extras.Async:dir=target/flamegraphs/IoEx;flameGraphOpts=--width,1900,--colors,java,--title=IO,--subtitle=Throwable IoBenchmark.exceptions")
addCommandAlias("flame8", "jmh:run -p timeFactor=5 -prof jmh.extras.Async:dir=target/flamegraphs/EitherTF;flameGraphOpts=--width,1900,--colors,java,--title=EitherT,--subtitle=F EffectBenchmark.eitherTIo")
addCommandAlias("flame9", "jmh:run -p timeFactor=5 -prof jmh.extras.Async:dir=target/flamegraphs/ZIO;flameGraphOpts=--width,1900,--colors,java,--title=ZIO ZioBenchmark.zio")

