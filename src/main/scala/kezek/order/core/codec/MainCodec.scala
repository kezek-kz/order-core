package kezek.order.core.codec

import io.circe.generic.auto._
import io.circe.syntax.EncoderOps
import io.circe.{Decoder, Encoder, HCursor}
import kezek.order.core.domain.OrderState
import kezek.order.core.domain.OrderState.{APPROVED, Approved, COMPLETED, CREATED, Completed, Created, PAID, PREPARING, Paid, Preparing, REJECTED, Rejected}
import kezek.order.core.domain.dto.OrderStateDTO
import kezek.order.core.domain.dto.OrderStateDTO.{ApprovedDTO, CompletedDTO, PaidDTO, PreparingDTO, RejectedDTO}

import scala.util.{Failure, Success}

trait MainCodec extends JodaTimeCodec {

  implicit val orderStateEncoder: Encoder[OrderState] = Encoder.instance {
    case s: Created => s.asJson.dropNullValues
    case s: Approved => s.asJson.dropNullValues
    case s: Rejected => s.asJson.dropNullValues
    case s: Paid => s.asJson.dropNullValues
    case s: Preparing => s.asJson.dropNullValues
    case s: Completed => s.asJson.dropNullValues
  }

  implicit val orderStateDecoder: Decoder[OrderState] = new Decoder[OrderState] {
    final def apply(c: HCursor): Decoder.Result[OrderState] = {
      def code = c.downField("name").as[String].toTry

      code match {
        case Success(s) if s == CREATED => c.as[Created]
        case Success(s) if s == APPROVED => c.as[Approved]
        case Success(s) if s == REJECTED => c.as[Rejected]
        case Success(s) if s == PAID => c.as[Paid]
        case Success(s) if s == PREPARING => c.as[Preparing]
        case Success(s) if s == COMPLETED => c.as[Completed]
        case Failure(exception) => throw exception
        case _ => throw new RuntimeException("Invalid state name")
      }
    }
  }

  implicit val orderStateDTOEncoder: Encoder[OrderStateDTO] = Encoder.instance {
    case s: ApprovedDTO => s.asJson.dropNullValues
    case s: RejectedDTO => s.asJson.dropNullValues
    case s: PaidDTO => s.asJson.dropNullValues
    case s: PreparingDTO => s.asJson.dropNullValues
    case s: CompletedDTO => s.asJson.dropNullValues
  }

  implicit val orderStateDTODecoder: Decoder[OrderStateDTO] = new Decoder[OrderStateDTO] {
    final def apply(c: HCursor): Decoder.Result[OrderStateDTO] = {
      def code = c.downField("name").as[String].toTry

      code match {
        case Success(s) if s == APPROVED => c.as[ApprovedDTO]
        case Success(s) if s == REJECTED => c.as[RejectedDTO]
        case Success(s) if s == PAID => c.as[PaidDTO]
        case Success(s) if s == PREPARING => c.as[PreparingDTO]
        case Success(s) if s == COMPLETED => c.as[CompletedDTO]
        case Failure(exception) => throw exception
        case _ => throw new RuntimeException("Invalid state name")
      }
    }
  }


}
