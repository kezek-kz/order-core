package kezek.order.core.domain.dto

import io.circe.Json
import kezek.order.core.domain.{OrderState, ProductDetail}
import org.joda.time.DateTime

case class UpdateOrderDTO(orderType: String, // takeaway, in-place,
                          tableDetails: Option[Json],
                          customerId: String,
                          date: DateTime,
                          subtotal: BigDecimal,
                          status: String,
                          products: Seq[ProductDetail],
                          rejectReason: Option[String],
                          paymentDetails: Option[Json],
                          states: Seq[OrderState])
