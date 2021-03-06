package io.parsek.optics

import java.time.Instant

import cats.data.NonEmptyList
import cats.syntax.either._
import io.parsek.PValue._
import io.parsek._
import io.parsek.implicits._

import scala.language.dynamics

/**
  * @author andr83
  */
case class PPath(value: PValidation[PValue]) extends Dynamic {
  import io.parsek.optics.PPath._

  def `null`: PValidation[Unit] = value compose pNull

  def boolean: PValidation[Boolean] = value compose pBoolean

  def int: PValidation[Int] = value compose pInt

  def long: PValidation[Long] = value compose pLong

  def double: PValidation[Double] = value compose pDouble

  def string: PValidation[String] = value compose pString

  def time: PValidation[Instant] = value compose pTime

  def arr: PValidation[Vector[PValue]] = value compose pArray

  def bytes: PValidation[Array[Byte]] = value compose pBytes

  def optional: PValidationNel = opt[PValue]

  def opt[A: Decoder : Encoder]: PValidationNel =
    Validation.apply[PValue, NonEmptyList[Throwable], PValue](s => value._getOrModify(s) match {
      case Right(v) => implicitly[Decoder[A]].apply(v).fold(
        error => Left(NonEmptyList(error, Nil)),
        a => Right(implicitly[Encoder[A]].apply(a))
      )
      case Left((_: TraverseFailure, _: PValue)) => Right(PValue.Null)
      case Left((FilterFailure, _: PValue)) => Right(PValue.Null)
      case Left((error: Throwable, _: PValue)) => Left(NonEmptyList(error, Nil))
    })(pw => pv => value._set(pw)(pv))

  def required: PValidationNel = req[PValue]

  def req[A: Decoder : Encoder]: PValidationNel =
    Validation.apply[PValue, NonEmptyList[Throwable], PValue](s => value._getOrModify(s) match {
      case Right(v) => implicitly[Decoder[A]].apply(v).fold(
        error => Left(NonEmptyList(error, Nil)),
        a => Right(implicitly[Encoder[A]].apply(a))
      )
      case Left((error: Throwable, _: PValue)) => Left(NonEmptyList(error, Nil))
    })(pw => pv => value._set(pw)(pv))

  def at(key: Symbol): PPath = PPath(pmap compose index(key))

  def at(key: String): PPath = PPath(pmap compose index(Symbol(key)))

  def pmap: PValidation[Map[Symbol, PValue]] = value compose pMap

  def map[A: Decoder, B: Encoder](f: A => B): PPath = PPath(ValidationS[PValue, PValue, Throwable, PValue, PValue](
    s => value._getOrModify(s)
      .flatMap(pa =>
        implicitly[Decoder[A]].apply(pa).map(
          a => implicitly[Encoder[B]].apply(f(a))).left.map[(Throwable, PValue)](e => e -> s)
      )
  )(value._set))

  def validate[A: Decoder, B: Encoder](f: A => Throwable Either B): PPath = PPath(ValidationS[PValue, PValue, Throwable, PValue, PValue](
    s => value._getOrModify(s)
      .flatMap(pa => {
        implicitly[Decoder[A]].apply(pa).flatMap(a => {
          f(a).map(implicitly[Encoder[B]].apply)
        }).left.map[(Throwable, PValue)](e => e -> s)
      })
  )(value._set))

  def filter[A : Decoder](f: A => Boolean): PPath = PPath(ValidationS[PValue, PValue, Throwable, PValue, PValue](
    s => value._getOrModify(s)
      .flatMap(pa => {
        implicitly[Decoder[A]].apply(pa).flatMap(a => {
          if (f(a)) {
            Right(pa)
          } else {
            Left(FilterFailure)
          }
        }).left.map[(Throwable, PValue)](e => e -> s)
      })
  )(value._set))

  def findAndMap[A : Decoder, B: Encoder](p: (Symbol,PValue) => Boolean, f: (Symbol,A) => (Symbol, B)): PPath = {
    PPath(ValidationS[PValue, PValue, Throwable, PValue, PValue](
      s => value._getOrModify(s).map(_.findAndMap(p, f))
    )(value._set))
  }

  /**
    * Memoize is a lens with caching of getting result.
    * Can be useful for getting access optimisation to source value inner fields
    *
    * @return
    */
  def memoize: PPath = {
    // Source argument which result need to memoize
    // ToDo: make thread safe via cuncurrent hashmap?
    var source: Option[PValue] = None
    var result: Option[(Throwable, PValue) Either PValue] = None
    PPath(ValidationS[PValue, PValue, Throwable, PValue, PValue](s => {
      (for {
        _source <- source
        if _source == s
        _result <- result
      } yield _result) getOrElse {
        val _result = value._getOrModify(s)
        source = Some(s)
        result = Some(_result)
        _result
      }
    })(value._set))
  }

  def set(v: PValue): PValue => PValue = value.set(v)

  def orElse(fallback: PPath): PPath = PPath(Validation.apply[PValue, Throwable, PValue]
    (s => value.get(s).orElse(fallback.value.get(s)))(value._set))

  def selectDynamic(field: String): PPath = PPath(pmap.compose(index(Symbol(field))))
}

object PPath {
  val root = PPath(Validation.id)

  def pNull: PValidation[Unit] = validated[Unit](_ => PValue.Null)

  def pBoolean: PValidation[Boolean] = validated[Boolean](PBoolean)

  def pInt: PValidation[Int] = validated[Int](PInt)

  def pLong: PValidation[Long] = validated[Long](PLong)

  def pDouble: PValidation[Double] = validated[Double](PDouble)

  def validated[A: Decoder](reverseGet: A => PValue): PValidation[A] =
    Validation(implicitly[Decoder[A]].apply)(a => _ => reverseGet(a))

  def pString: PValidation[String] = validated[String](PString)

  def pTime: PValidation[Instant] = validated[Instant](PInstant)

  def pArray: PValidation[Vector[PValue]] = validated[Vector[PValue]](PArray)

  def pMap: PValidation[Map[Symbol, PValue]] = validated[Map[Symbol, PValue]](PMap)

  def pBytes: PValidation[Array[Byte]] = validated[Array[Byte]](PBytes)

  def index(key: Symbol): Validation[Map[Symbol, PValue], Throwable, PValue] =
    Validation[Map[Symbol, PValue], Throwable, PValue](v =>
      v.get(key).fold[Throwable Either PValue](Left(TraverseFailure(s"Field $key doesn't exist")))(a => Right(a))
    ) { a => v => v + (key -> a) }
}


