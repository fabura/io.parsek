package io.parsek.instances

import java.time.{Instant, LocalDate}

import io.parsek.{Encoder, PValue}

import scala.language.higherKinds

/**
  * @author Andrei Tupitcyn
  */
trait encoders {
  implicit val idEncoder: Encoder[PValue] = Encoder.pure[PValue](identity)
  implicit val booleanEncoder: Encoder[Boolean] = Encoder.pure[Boolean](PValue.fromBoolean)
  implicit val intEncoder: Encoder[Int] = Encoder.pure[Int](PValue.fromInt)
  implicit val longEncoder: Encoder[Long] = Encoder.pure[Long](PValue.fromLong)
  implicit val doubleEncoder: Encoder[Double] = Encoder.pure[Double](PValue.fromDouble)
  implicit val stringEncoder: Encoder[String] = Encoder.pure[String](PValue.fromString)
  implicit val instantEncoder: Encoder[Instant] = Encoder.pure[Instant](PValue.fromInstant)
  implicit val localDateEncoder: Encoder[LocalDate] = Encoder.pure[LocalDate](PValue.fromLocalDate)
  implicit def traversableEncoder[A, C[A] <: Iterable[A]](implicit e: Encoder[A]): Encoder[C[A]] = Encoder.pure[C[A]](it => {
    PValue.fromValues(it.map(e.apply))
  })
  implicit def mapEncoder[A](implicit e: Encoder[A]): Encoder[Map[Symbol, A]] = Encoder.pure[Map[Symbol, A]](m=> {
    PValue.fromMap(m.mapValues(e.apply))
  })
}

object encoders extends encoders