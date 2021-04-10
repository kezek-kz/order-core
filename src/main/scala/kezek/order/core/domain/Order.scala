package kezek.order.core.domain

import io.circe.Json
import kezek.order.core.domain.OrderState.{Paid, Rejected}
import org.joda.time.DateTime

case class Order(id: Long,
                 orderType: String, // takeaway, in-place,
                 tableDetails: Option[Json],
                 customerId: String,
                 date: DateTime,
                 subtotal: BigDecimal,
                 status: String,
                 createdAt: DateTime,
                 updatedAt: DateTime,
                 products: Seq[ProductDetail],
                 rejectReason: Option[String],
                 paymentDetails: Option[Json],
                 states: Seq[OrderState]) {

  def changeState(newState: OrderState): Order = {
    (newState match {
      case s: Rejected => this.copy(rejectReason = Some(s.reason))
      case s: Paid => this.copy(paymentDetails = Some(s.paymentDetails))
      case _ => this
    }).copy(
      status = newState.name,
      updatedAt = DateTime.now(),
      states = states :+ newState
    )
  }

}

case class ProductDetail(productId: String,
                         title: String,
                         unit: String,
                         price: BigDecimal,
                         image: Option[String],
                         quantity: BigDecimal,
                         total: BigDecimal)
