package kezek.order.core.api.http

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import kezek.order.core.api.http.route.OrderHttpRoutes

import javax.ws.rs.{GET, Path}

@Path("/api")
trait HttpRoutes
  extends OrderHttpRoutes {

  val routes: Route =
    pathPrefix("api") {
      concat(
        healthcheck,
        orderHttpRoutes
      )
    }

  @GET
  @Operation(
    summary = "health check",
    method = "GET",
    responses = Array(
      new ApiResponse(responseCode = "200", description = "OK"),
      new ApiResponse(responseCode = "500", description = "Internal server error")
    )
  )
  @Path("/healthcheck")
  @Tag(name = "Healthcheck")
  def healthcheck: Route = {
    path("healthcheck") { ctx =>
      complete("ok")(ctx)
    }
  }
}
