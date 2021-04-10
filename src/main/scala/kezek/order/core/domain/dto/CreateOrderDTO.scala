package kezek.order.core.domain.dto

import io.circe.Json
import kezek.order.core.domain.ProductDetail
import org.joda.time.DateTime

case class CreateOrderDTO(orderType: String, // takeaway, in-place,
                          tableDetails: Option[Json],
                          customerId: String,
                          date: DateTime,
                          subtotal: BigDecimal,
                          products: Seq[ProductDetail])
