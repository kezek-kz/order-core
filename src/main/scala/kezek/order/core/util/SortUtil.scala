package kezek.order.core.util

object SortUtil {
  def parseSortParams(sortParams: Option[String]): Map[String, SortType] = {
    sortParams match {
      case Some(params) =>
        params.split(",").map(param => (param.drop(1), if (param.take(1) == "+") ASC else DESC)).toMap
      case None => Map.empty
    }
  }
}

sealed trait SortType

object ASC extends SortType

object DESC extends SortType
