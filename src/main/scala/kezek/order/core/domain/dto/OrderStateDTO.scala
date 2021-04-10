package kezek.order.core.domain.dto

import io.circe.Json

trait OrderStateDTO

object OrderStateDTO {

  case class ApprovedDTO(name: String) extends OrderStateDTO

  case class RejectedDTO(reason: String, name: String) extends OrderStateDTO

  case class PaidDTO(paymentDetails: Json, name: String) extends OrderStateDTO

  case class PreparingDTO(name: String) extends OrderStateDTO

  case class CompletedDTO(name: String) extends OrderStateDTO
}
