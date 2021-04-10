package kezek.order.core.domain

import io.circe.Json
import org.joda.time.DateTime

trait OrderState {
  def name: String
}

object OrderState {

  final val CREATED = "СОЗДАН"
  final val APPROVED = "ПОДТВЕРЖДЕН"
  final val REJECTED = "ОТКАЗОНО"
  final val PAID = "ОПЛАЧЕН"
  final val PREPARING = "ГОТОВИТЬСЯ"
  final val COMPLETED = "ГОТОВО"

  case class Created(name: String, createdAt: DateTime) extends OrderState {
    require(name == CREATED)
  }

  case class Approved(name: String, createdAt: DateTime) extends OrderState {
    require(name == APPROVED)
  }

  case class Rejected(reason: String, name: String, createdAt: DateTime) extends OrderState {
    require(name == REJECTED)
  }

  case class Paid(paymentDetails: Json, name: String, createdAt: DateTime) extends OrderState {
    require(name == PAID)
  }

  case class Preparing(name: String, createdAt: DateTime) extends OrderState {
    require(name == PREPARING)
  }

  case class Completed(name: String, createdAt: DateTime) extends OrderState {
    require(name == COMPLETED)
  }

}
