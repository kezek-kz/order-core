package kezek.order.core.domain.dto

import kezek.order.core.domain.Order

case class OrderListWithTotalDTO(total: Long, collection: Seq[Order])
