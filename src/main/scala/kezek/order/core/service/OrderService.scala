package kezek.order.core.service

import akka.Done
import akka.actor.typed.ActorSystem
import akka.http.scaladsl.model.StatusCodes
import com.typesafe.config.{Config, ConfigFactory}
import io.circe.generic.auto._
import io.circe.syntax.EncoderOps
import io.scalaland.chimney.dsl.TransformerOps
import kezek.order.core.codec.MainCodec
import kezek.order.core.domain.OrderFilter._
import kezek.order.core.domain.OrderState._
import kezek.order.core.domain._
import kezek.order.core.domain.dto.OrderStateDTO.{ApprovedDTO, CompletedDTO, PaidDTO, PreparingDTO, RejectedDTO}
import kezek.order.core.domain.dto.{CreateOrderDTO, OrderListWithTotalDTO, OrderStateDTO, UpdateOrderDTO}
import kezek.order.core.exception.ApiException
import kezek.order.core.repository.OrderRepository
import kezek.order.core.repository.mongo.MongoRepository.DUPLICATED_KEY_ERROR_CODE
import kezek.order.core.repository.mongo.OrderMongoRepository
import kezek.order.core.util.SortType
import org.joda.time.DateTime
import org.mongodb.scala.{MongoClient, MongoWriteException}
import org.slf4j.{Logger, LoggerFactory}

import scala.concurrent.{ExecutionContext, Future}

object OrderService extends MainCodec {

  val log: Logger = LoggerFactory.getLogger(getClass.getSimpleName)

  def generateFilters(categoryId: Option[String] = None,
                      title: Option[String] = None,
                      description: Option[String] = None): Seq[OrderFilter] = {
    var filters: Seq[OrderFilter] = Seq.empty
    if (categoryId.isDefined) filters = filters :+ ByCategoryIdFilter(categoryId.get)
    if (title.isDefined) filters = filters :+ ByTitleFilter(title.get)
    if (description.isDefined) filters = filters :+ ByDescriptionFilter(description.get)
    filters
  }

  def transformToOrderState(orderStateDTO: OrderStateDTO): OrderState = {
    log.debug(s"transformToOrderState() was called {amlRequestStateDTO: ${orderStateDTO.asJson.noSpaces}}")
    orderStateDTO match {
      case ApprovedDTO(name) => Approved(name, DateTime.now())
      case RejectedDTO(reason, name) => Rejected(reason, name, DateTime.now())
      case PaidDTO(paymentDetails, name) => Paid(paymentDetails, name, DateTime.now())
      case PreparingDTO(name) => Preparing(name, DateTime.now())
      case CompletedDTO(name) => Completed(name, DateTime.now())
    }
  }

}

class OrderService()(implicit val mongoClient: MongoClient,
                     implicit val executionContext: ExecutionContext,
                     implicit val system: ActorSystem[_]) extends MainCodec {

  val config: Config = ConfigFactory.load()
  val log: Logger = LoggerFactory.getLogger(getClass.getSimpleName)
  val orderRepository: OrderRepository = new OrderMongoRepository()

  def paginate(filters: Seq[OrderFilter],
               page: Option[Int],
               pageSize: Option[Int],
               sortParams: Map[String, SortType]): Future[OrderListWithTotalDTO] = {
    log.debug(s"paginate() was called {filters: $filters, page: $page, pageSize: $pageSize, sortParams: $sortParams}")
    (for (
      orders <- orderRepository.paginate(filters, page, pageSize, sortParams);
      count <- orderRepository.count(filters)
    ) yield OrderListWithTotalDTO(
      collection = orders,
      total = count
    )).recover { exception =>
      log.error(s"paginate() failed to paginate orders {exception: $exception, filters: $filters, page: $page, pageSize: $pageSize, sortParams: $sortParams}")
      throw new RuntimeException(s"Failed to paginate orders: $exception")
    }
  }

  def setState(id: Long, newState: OrderState): Future[Order] = {
    log.debug(s"setState() was called {id $id, newState: ${newState.asJson.noSpaces}}")
    getById(id).flatMap { order =>
      if(isStateChangeValid(order, newState)) {
        orderRepository.update(id, order.changeState(newState))
      } else {
        log.error(s"setState() invalid state change {id $id, currentStatus: ${order.status}, newStatus: ${newState.name}}")
        throw ApiException(StatusCodes.Conflict, "Invalid state change")
      }
    }
  }

  def update(id: Long, updateOrderDTO: UpdateOrderDTO): Future[Order] = {
    log.debug(s"update() was called {id: $id, updateOrderDTO: $updateOrderDTO}")
    getById(id).flatMap { order =>
      orderRepository.update(
        id,
        updateOrderDTO.into[Order]
          .withFieldConst(_.id, id)
          .withFieldConst(_.updatedAt, DateTime.now())
          .withFieldConst(_.createdAt, order.createdAt)
          .transform
      )
    }
  }

  def getById(id: Long): Future[Order] = {
    log.debug(s"getById() was called {id: $id}")
    orderRepository.findById(id).map {
      case Some(order) => order
      case None =>
        log.error(s"getById() failed to find order {id: $id}")
        throw ApiException(StatusCodes.NotFound, s"Failed to find order with id: $id")
    }
  }

  def create(createOrderDTO: CreateOrderDTO): Future[Order] = {
    log.debug(s"create() was called {createOrderDTO: ${createOrderDTO.asJson.noSpaces}}")
    val dateTimeNow = DateTime.now()
    val initState = Created(CREATED, dateTimeNow)
    (for (
      _ <- orderRepository.incrementCounter();
      count <- orderRepository.getCounter();
      order <- Future {
        createOrderDTO.into[Order]
          .withFieldConst(_.id, count)
          .withFieldConst(_.status, initState.name)
          .withFieldConst(_.createdAt, dateTimeNow)
          .withFieldConst(_.updatedAt, dateTimeNow)
          .withFieldConst(_.states, Seq(initState))
          .withFieldConst(_.rejectReason, None)
          .withFieldConst(_.paymentDetails, None)
          .transform
      };
      _ <- orderRepository.create(order).recover {
        case ex: MongoWriteException if ex.getCode == DUPLICATED_KEY_ERROR_CODE =>
          log.error(s"create() failed to create order due to duplicate key {ex: $ex, order: ${order.asJson.noSpaces}")
          throw ApiException(StatusCodes.Conflict, s"Failed to create, order with id: ${order.id} already exists")
        case ex: Exception =>
          log.error(s"create() failed to create order {ex: $ex, order: ${order.asJson.noSpaces}}")
          throw ApiException(StatusCodes.ServiceUnavailable, ex.getMessage)
      }
    ) yield order).recover {
      case ex: ApiException => throw ex
      case exception: Exception =>
        log.error(s"create() failed to create order {exception: $exception, createOrderDTO: ${createOrderDTO.asJson.noSpaces}}")
        throw ApiException(StatusCodes.ServiceUnavailable, s"Failed to create order: $exception")
    }
  }

  def delete(id: Long): Future[Done] = {
    log.debug(s"delete() was called {id: $id}")
    orderRepository.delete(id)
  }

  def isStateChangeValid(order: Order, newState: OrderState): Boolean = {
    log.debug(s"isStateChangeValid() was called {currentStatus: ${order.status}, newStatus: ${newState.name}}")
    (order.status, newState.name) match {
      case (CREATED, APPROVED) => true
      case (CREATED, REJECTED) => true
      case (APPROVED, PAID) => true
      case (PAID, PREPARING) => true
      case (PREPARING, COMPLETED) => true
      case _ => false
    }
  }

}

