package ai.metarank.feature

import ai.metarank.feature.BaseFeature.RankingFeature
import ai.metarank.feature.LocalDateTimeFeature.LocalDateTimeSchema
import ai.metarank.fstore.Persistence
import ai.metarank.model.Dimension.SingleDim
import ai.metarank.model.Feature.FeatureConfig
import ai.metarank.model.Field.StringField
import ai.metarank.model.FieldName.EventType
import ai.metarank.model.Key.FeatureName
import ai.metarank.model.MValue.SingleValue
import ai.metarank.model.ScopeType.SessionScopeType
import ai.metarank.model.Write.Put
import ai.metarank.model._
import ai.metarank.util.Logging
import cats.effect.IO
import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}

import java.time.format.DateTimeFormatter
import java.time.{Duration, ZonedDateTime}
import scala.util.{Failure, Success, Try}

case class LocalDateTimeFeature(schema: LocalDateTimeSchema) extends RankingFeature with Logging {
  override def dim                                                          = SingleDim
  override def states: List[FeatureConfig]                                  = Nil
  override def writes(event: Event): IO[Iterable[Put]] = IO.pure(Nil)

  override def valueKeys(event: Event.RankingEvent): Iterable[Key] = Nil
  override def value(
      request: Event.RankingEvent,
      features: Map[Key, FeatureValue]
  ): MValue = {

    request.fieldsMap.get(schema.source.field) match {
      case Some(StringField(_, value)) =>
        Try(ZonedDateTime.parse(value, DateTimeFormatter.ISO_DATE_TIME)) match {
          case Success(datetime) =>
            SingleValue(schema.name, schema.parse.map(datetime))
          case Failure(ex) =>
            logger.warn(s"cannot parse field '$value' as an ISO DateTime", ex)
            SingleValue(schema.name, 0.0)
        }
      case _ =>
        SingleValue(schema.name, 0.0)
    }
  }
}

object LocalDateTimeFeature {
  sealed trait DateTimeMapper {
    def name: String
    def map(ts: ZonedDateTime): Double
  }
  case object TimeOfDay extends DateTimeMapper {
    val name                 = "time_of_day"
    lazy val SECONDS_IN_HOUR = Duration.ofHours(1).getSeconds.toDouble
    override def map(ts: ZonedDateTime): Double = {
      val second = ts.toLocalTime.toSecondOfDay
      second / SECONDS_IN_HOUR
    }
  }
  case object DayOfWeek extends DateTimeMapper {
    val name = "day_of_week"
    override def map(ts: ZonedDateTime): Double = {
      ts.getDayOfWeek.getValue.toDouble
    }
  }
  case object MonthOfYear extends DateTimeMapper {
    val name = "month_of_year"
    override def map(ts: ZonedDateTime): Double = {
      ts.getMonth.getValue.toDouble
    }
  }
  case object Year extends DateTimeMapper {
    val name = "year"
    override def map(ts: ZonedDateTime): Double = {
      ts.getYear.toDouble
    }
  }
  case object Second extends DateTimeMapper {
    val name = "second"
    override def map(ts: ZonedDateTime): Double = {
      ts.toEpochSecond.toDouble
    }
  }
  case class LocalDateTimeSchema(
      name: FeatureName,
      source: FieldName,
      parse: DateTimeMapper
  ) extends FeatureSchema {
    override val refresh = None
    override val ttl     = None
    override val scope   = SessionScopeType
  }

  implicit val tdMapperDecoder: Decoder[DateTimeMapper] = Decoder.decodeString.emapTry {
    case TimeOfDay.name   => Success(TimeOfDay)
    case DayOfWeek.name   => Success(DayOfWeek)
    case MonthOfYear.name => Success(MonthOfYear)
    case Year.name        => Success(Year)
    case Second.name      => Success(Second)
    case other            => Failure(new IllegalArgumentException(s"parsing method $other is not supported"))
  }

  implicit val tdMapperEncoder: Encoder[DateTimeMapper] = Encoder.encodeString.contramap(_.name)

  implicit val timeDayDecoder: Decoder[LocalDateTimeSchema] =
    deriveDecoder[LocalDateTimeSchema]
      .ensure(
        _.source.event == EventType.Ranking,
        "can only work with ranking event fields"
      )
      .withErrorMessage("cannot parse a feature definition of type 'local_time'")

  implicit val timeDayEncoder: Encoder[LocalDateTimeSchema] = deriveEncoder
}
