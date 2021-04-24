package kezek.order.core.api.http.route

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
import io.circe.Json
import io.circe.generic.auto._
import io.scalaland.chimney.dsl.PatcherOps
import io.swagger.v3.oas.annotations.enums.ParameterIn
import io.swagger.v3.oas.annotations.media.{Content, ExampleObject, Schema}
import io.swagger.v3.oas.annotations.parameters.RequestBody
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import io.swagger.v3.oas.annotations.{Operation, Parameter}
import kezek.order.core.codec.MainCodec
import kezek.order.core.domain.Order
import kezek.order.core.domain.dto.{CancelOrderDTO, CreateOrderDTO, OrderListWithTotalDTO, UpdateOrderDTO}
import kezek.order.core.service.OrderService
import kezek.order.core.util.{HttpUtil, SortUtil}

import javax.ws.rs._
import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}

trait OrderHttpRoutes extends MainCodec {

  val orderService: OrderService

  implicit val executionContext: ExecutionContext

  def orderHttpRoutes: Route = {
    pathPrefix("orders") {
      concat(
        handleEvent,
        updateOrder,
        getOrderById,
        deleteOrder,
        paginateOrders,
        createOrder
      )
    }
  }

  @POST
  @Operation(summary = "Handle order event")
  @RequestBody(
    required = true,
    content = Array(
      new Content(
        schema = new Schema(implementation = classOf[Json]),
        mediaType = "application/json",
        examples = Array(
          new ExampleObject(name = "Cancel", value = "{\n  \"reason\": \"Some cancel reason\"\n}"),
          new ExampleObject(name = "Checkout", value = "{ \"some\": \"json\"}"),
          new ExampleObject(name = "Empty", value = "{}")
        )
      )
    )
  )
  @Parameter(name = "id", in = ParameterIn.PATH, required = true)
  @Parameter(
    name = "event",
    in = ParameterIn.PATH,
    description = "Events: checkout, cancel, cook, cooked, taken",
    required = true
  )
  @ApiResponse(responseCode = "200", description = "OK", content = Array(new Content(schema = new Schema(implementation = classOf[Order]))))
  @ApiResponse(responseCode = "500", description = "Internal server error")
  @Path("/orders/{id}/{event}")
  @Tag(name = "Orders")
  def handleEvent: Route = {
    post {
      path(LongNumber / Segment) { (id, event) =>
        concat (
          entity(as[CancelOrderDTO]) { body =>
            onComplete(orderService.handleEvent(id, event, cancelReason = body.cancelReason)) {
              case Success(result) => complete(result)
              case Failure(exception) => HttpUtil.completeThrowable(exception)
            }
          },
          entity(as[Json]) { body =>
            onComplete(orderService.handleEvent(id, event, paymentDetails = body)) {
              case Success(result) => complete(result)
              case Failure(exception) => HttpUtil.completeThrowable(exception)
            }
          }
        )
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
      new Parameter(name = "sort", in = ParameterIn.QUERY, example = "-createdAt")
    ),
    responses = Array(
      new ApiResponse(
        responseCode = "200",
        description = "OK",
        content = Array(
          new Content(
            schema = new Schema(implementation = classOf[OrderListWithTotalDTO]),
            mediaType = "application/json"
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
      new Parameter(name = "id", in = ParameterIn.PATH, required = true),
    ),
    responses = Array(
      new ApiResponse(
        responseCode = "200",
        description = "OK",
        content = Array(
          new Content(
            schema = new Schema(implementation = classOf[Order])
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
            schema = new Schema(implementation = classOf[Order])
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
      new Parameter(name = "id", in = ParameterIn.PATH, required = true),
    ),
    responses = Array(
      new ApiResponse(
        responseCode = "200",
        description = "OK",
        content = Array(
          new Content(
            schema = new Schema(implementation = classOf[Order]),
          )
        )
      ),
      new ApiResponse(responseCode = "500", description = "Internal server error")
    )
  )
  @RequestBody(content = Array(new Content(schema = new Schema(implementation = classOf[UpdateOrderDTO]), mediaType = "application/json")), required = true)
  @Path("/orders/{id}")
  @Tag(name = "Orders")
  def updateOrder: Route = {
    put {
      path(LongNumber) { id =>
        entity(as[UpdateOrderDTO]) { body =>
          onComplete {
            orderService.getById(id).map(_.using(body).ignoreNoneInPatch.patch).map(orderService.update(id, _))
          } {
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
      new Parameter(name = "id", in = ParameterIn.PATH, required = true),
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
