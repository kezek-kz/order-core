package kezek.order.core.api.http.route

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
import io.circe.generic.auto._
import io.swagger.v3.oas.annotations.enums.ParameterIn
import io.swagger.v3.oas.annotations.media.{Content, ExampleObject, Schema}
import io.swagger.v3.oas.annotations.parameters.RequestBody
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import io.swagger.v3.oas.annotations.{Operation, Parameter}
import kezek.order.core.codec.MainCodec
import kezek.order.core.domain.Order
import kezek.order.core.domain.dto.{CreateOrderDTO, OrderListWithTotalDTO, OrderStateDTO, UpdateOrderDTO}
import kezek.order.core.service.OrderService
import kezek.order.core.util.{HttpUtil, SortUtil}

import javax.ws.rs._
import scala.util.{Failure, Success}

trait OrderHttpRoutes extends MainCodec {

  val orderService: OrderService

  def orderHttpRoutes: Route = {
    pathPrefix("orders") {
      concat(
        updateOrderStatus,
        updateOrder,
        getOrderById,
        deleteOrder,
        paginateOrders,
        createOrder
      )
    }
  }

  @PUT
  @Operation(
    summary = "Update order status",
    description = "Updates order's status and appends state to states",
    method = "PUT",
    parameters = Array(
      new Parameter(name = "id", in = ParameterIn.PATH, example = "2251799814619115", required = true),
    ),
    requestBody = new RequestBody(
      content = Array(
        new Content(
          schema = new Schema(implementation = classOf[OrderStateDTO]),
          mediaType = "application/json",
          examples = Array(
            new ExampleObject(name = "ApprovedDTO", value = "{\n  \"name\": \"ПОДТВЕРЖДЕН\"\n}"),
            new ExampleObject(name = "RejectedDTO", value = "{\n  \"name\": \"ОТКАЗОНО\"\n  \"reason\": \"Some reason\"\n}"),
            new ExampleObject(name = "PaidDTO", value = "{\n  \"name\": \"ОПЛАЧЕН\"\n  \"paymentDetails\": \"{}\"\n}"),
            new ExampleObject(name = "PreparingDTO", value = "{\n  \"name\": \"ГОТОВИТЬСЯ\"\n}"),
            new ExampleObject(name = "CompletedDTO", value = "{\n  \"name\": \"ГОТОВО\"\n}"),
          )
        ),
      ),
      required = true
    ),
    responses = Array(
      new ApiResponse(
        responseCode = "200",
        description = "OK",
        content = Array(
          new Content(
            schema = new Schema(implementation = classOf[Order]),
            examples = Array(new ExampleObject(name = "Order", value = ""))
          )
        )
      ),
      new ApiResponse(responseCode = "500", description = "Internal server error")
    )
  )
  @Path("/orders/{id}/status")
  @Tag(name = "Orders")
  def updateOrderStatus: Route = {
    put {
      path(LongNumber / "status") { id =>
        entity(as[OrderStateDTO]) { body =>
          onComplete(orderService.setState(id, OrderService.transformToOrderState(body))) {
            case Success(result) => complete(result)
            case Failure(exception) => HttpUtil.completeThrowable(exception)
          }
        }
      }
    }
  }

  @GET
  @Operation(
    summary = "Get order list",
    description = "Get filtered and paginated order list",
    method = "GET",
    parameters = Array(
      new Parameter(name = "page", in = ParameterIn.QUERY, example = "1"),
      new Parameter(name = "pageSize", in = ParameterIn.QUERY, example = "10"),
      new Parameter(name = "sort", in = ParameterIn.QUERY, example = "")
    ),
    responses = Array(
      new ApiResponse(
        responseCode = "200",
        description = "OK",
        content = Array(
          new Content(
            schema = new Schema(implementation = classOf[OrderListWithTotalDTO]),
            mediaType = "application/json",
            examples = Array(new ExampleObject(name = "OrderListWithTotalDTO", value = ""))
          )
        )
      ),
      new ApiResponse(responseCode = "500", description = "Internal server error")
    )
  )
  @Path("/orders")
  @Tag(name = "Orders")
  def paginateOrders: Route = {
    get {
      pathEndOrSingleSlash {
        parameters(
          "page".as[Int].?,
          "pageSize".as[Int].?,
          "sort".?
        ) {
          (page,
           pageSize,
           sort) => {
            onComplete {
              orderService.paginate(
                OrderService.generateFilters(
                ),
                page,
                pageSize,
                SortUtil.parseSortParams(sort)
              )
            } {
              case Success(result) => complete(result)
              case Failure(exception) => HttpUtil.completeThrowable(exception)
            }
          }
        }
      }
    }
  }

  @GET
  @Operation(
    summary = "Get order by id",
    description = "Returns a full information about order by id",
    method = "GET",
    parameters = Array(
      new Parameter(name = "id", in = ParameterIn.PATH, example = "", required = true),
    ),
    responses = Array(
      new ApiResponse(
        responseCode = "200",
        description = "OK",
        content = Array(
          new Content(
            schema = new Schema(implementation = classOf[Order]),
            examples = Array(new ExampleObject(name = "Order", value = ""))
          )
        )
      ),
      new ApiResponse(responseCode = "500", description = "Internal server error")
    )
  )
  @Path("/orders/{id}")
  @Tag(name = "Orders")
  def getOrderById: Route = {
    get {
      path(LongNumber) { id =>
        onComplete(orderService.getById(id)) {
          case Success(result) => complete(result)
          case Failure(exception) => HttpUtil.completeThrowable(exception)
        }
      }
    }
  }

  @POST
  @Operation(
    summary = "Create order",
    description = "Creates new order",
    method = "POST",
    requestBody = new RequestBody(
      content = Array(
        new Content(
          schema = new Schema(implementation = classOf[CreateOrderDTO]),
          mediaType = "application/json",
          examples = Array(
            new ExampleObject(name = "CreateOrderDTO", value = "")
          )
        )
      ),
      required = true
    ),
    responses = Array(
      new ApiResponse(
        responseCode = "200",
        description = "OK",
        content = Array(
          new Content(
            schema = new Schema(implementation = classOf[Order]),
            examples = Array(new ExampleObject(name = "Order", value = ""))
          )
        )
      ),
      new ApiResponse(responseCode = "500", description = "Internal server error")
    )
  )
  @Path("/orders")
  @Tag(name = "Orders")
  def createOrder: Route = {
    post {
      pathEndOrSingleSlash {
        entity(as[CreateOrderDTO]) { body =>
          onComplete(orderService.create(body)) {
            case Success(result) => complete(result)
            case Failure(exception) => HttpUtil.completeThrowable(exception)
          }
        }
      }
    }
  }

  @PUT
  @Operation(
    summary = "Update order",
    description = "Updates order",
    method = "PUT",
    parameters = Array(
      new Parameter(name = "id", in = ParameterIn.PATH, example = "", required = true),
    ),
    requestBody = new RequestBody(
      content = Array(
        new Content(
          schema = new Schema(implementation = classOf[UpdateOrderDTO]),
          mediaType = "application/json",
          examples = Array(new ExampleObject(name = "UpdateOrderDTO", value = ""))
        )
      ),
      required = true
    ),
    responses = Array(
      new ApiResponse(
        responseCode = "200",
        description = "OK",
        content = Array(
          new Content(
            schema = new Schema(implementation = classOf[Order]),
            examples = Array(new ExampleObject(name = "Order", value = ""))
          )
        )
      ),
      new ApiResponse(responseCode = "500", description = "Internal server error")
    )
  )
  @Path("/orders/{id}")
  @Tag(name = "Orders")
  def updateOrder: Route = {
    put {
      path(LongNumber) { id =>
        entity(as[UpdateOrderDTO]) { body =>
          onComplete(orderService.update(id, body)) {
            case Success(result) => complete(result)
            case Failure(exception) => HttpUtil.completeThrowable(exception)
          }
        }
      }
    }
  }

  @DELETE
  @Operation(
    summary = "Deletes order",
    description = "Deletes order",
    method = "DELETE",
    parameters = Array(
      new Parameter(name = "id", in = ParameterIn.PATH, example = "", required = true),
    ),
    responses = Array(
      new ApiResponse(
        responseCode = "204",
        description = "OK",
      ),
      new ApiResponse(responseCode = "500", description = "Internal server error")
    )
  )
  @Path("/orders/{id}")
  @Tag(name = "Orders")
  def deleteOrder: Route = {
    delete {
      path(LongNumber) { id =>
        onComplete(orderService.delete(id)) {
          case Success(_) => complete(StatusCodes.NoContent)
          case Failure(exception) => HttpUtil.completeThrowable(exception)
        }
      }
    }
  }

}
