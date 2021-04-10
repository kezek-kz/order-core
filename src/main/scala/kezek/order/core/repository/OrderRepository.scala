package kezek.order.core.repository

import akka.Done
import kezek.order.core.domain.{Order, OrderFilter}
import kezek.order.core.util.SortType

import scala.concurrent.Future

trait OrderRepository {

  def create(order: Order): Future[Order]

  def update(id: Long, order: Order): Future[Order]

  def findById(id: Long): Future[Option[Order]]

  def paginate(filters: Seq[OrderFilter],
               page: Option[Int],
               pageSize: Option[Int],
               sortParams: Map[String, SortType]): Future[Seq[Order]]

  def count(filters: Seq[OrderFilter]): Future[Long]

  def delete(id: Long): Future[Done]

  def incrementCounter(): Future[Unit]

  def getCounter(): Future[Long]
}
