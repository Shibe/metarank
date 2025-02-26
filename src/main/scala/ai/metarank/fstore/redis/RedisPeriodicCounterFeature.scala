package ai.metarank.fstore.redis

import ai.metarank.fstore.codec.{KCodec, StoreFormat}
import ai.metarank.fstore.redis.client.RedisClient
import ai.metarank.model.Feature.PeriodicCounterFeature
import ai.metarank.model.Feature.PeriodicCounterFeature.PeriodicCounterConfig
import ai.metarank.model.FeatureValue.PeriodicCounterValue
import ai.metarank.model.{Key, Timestamp}
import ai.metarank.model.Write.PeriodicIncrement
import cats.effect.IO
import cats.implicits._

case class RedisPeriodicCounterFeature(
    config: PeriodicCounterConfig,
    client: RedisClient,
    prefix: String,
    format: StoreFormat
) extends PeriodicCounterFeature {
  override def put(action: PeriodicIncrement): IO[Unit] = {
    val period = action.ts.toStartOfPeriod(config.period)
    client.hincrby(format.key.encode(prefix, action.key), period.ts.toString, action.inc).void
  }

  override def computeValue(key: Key, ts: Timestamp): IO[Option[PeriodicCounterValue]] = {
    for {
      map     <- client.hgetAll(format.key.encode(prefix, key))
      decoded <- map.toList.map { case (k, v) => decode(new String(k), new String(v)) }.sequence
    } yield {
      if (decoded.isEmpty) None else Some(PeriodicCounterValue(key, ts, fromMap(decoded.toMap)))
    }
  }

  def decode(timeString: String, cntString: String): IO[(Timestamp, Long)] = for {
    ts  <- IO.fromOption(timeString.toLongOption)(new Exception(s"cannot decode timestamp $timeString"))
    cnt <- IO.fromOption(cntString.toLongOption)(new Exception(s"cannot decode counter $cntString"))
  } yield {
    Timestamp(ts) -> cnt
  }
}
