package kezek.order.core.util

object PaginationUtil {
  def offset(page: Int, size: Int): Int = (page - 1) * size
}
