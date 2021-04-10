package kezek.order.core.domain

trait OrderFilter

object OrderFilter {

  case class ByCategoryIdFilter(categoryId: String) extends OrderFilter
  case class ByTitleFilter(title: String) extends OrderFilter
  case class ByDescriptionFilter(description: String) extends OrderFilter
}
