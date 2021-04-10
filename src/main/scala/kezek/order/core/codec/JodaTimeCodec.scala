package kezek.order.core.codec

import io.circe.{Decoder, Encoder, HCursor, Json}
import org.joda.time.format.{DateTimeFormat, DateTimeFormatter}
import org.joda.time.{DateTime, LocalDate, LocalTime}

import scala.util.Try

trait JodaTimeCodec {

  val defaultDateTimeFormatter: DateTimeFormatter = DateTimeFormat.forPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ")
  val dateFormatter: DateTimeFormatter = DateTimeFormat.forPattern("yyyy-MM-dd")
  val dateTimeFormatterWithTime: DateTimeFormatter = DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss")
  val dateTimeFormatterWithSeconds: DateTimeFormatter = DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss+SSSS")
  val dateTimeFormatterWithoutMillis: DateTimeFormatter = DateTimeFormat.forPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")

  def dateTimeFormatDecoder(format: DateTimeFormatter): Decoder[DateTime] =
    Decoder[String].emapTry(str => Try(DateTime.parse(str, format)))

  implicit val encodeDateTime: Encoder[DateTime] = new Encoder[DateTime] {
    final def apply(a: DateTime): Json = {
      Json.fromLong(a.getMillis)
    }
  }
  implicit val decodeDateTime: Decoder[DateTime] = {
    Decoder[Long].map(long => new DateTime(long))
      .or(dateTimeFormatDecoder(defaultDateTimeFormatter))
      .or(dateTimeFormatDecoder(dateFormatter))
      .or(dateTimeFormatDecoder(dateTimeFormatterWithTime))
      .or(dateTimeFormatDecoder(dateTimeFormatterWithSeconds))
      .or(dateTimeFormatDecoder(dateTimeFormatterWithoutMillis))
  }

}
