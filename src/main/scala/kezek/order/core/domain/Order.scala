package kezek.order.core.domain

import io.circe.Json
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
                 cancelReason: Option[String],
                 paymentDetails: Option[Json])

case class ProductDetail(productId: String,
                         title: String,
                         unit: String,
                         price: BigDecimal,
                         image: Option[String],
                         quantity: BigDecimal,
                         total: BigDecimal)
