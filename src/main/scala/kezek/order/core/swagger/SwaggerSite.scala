package kezek.order.core.swagger

import akka.http.scaladsl.server.{Directives, Route}

trait SwaggerSite extends Directives {

  val swaggerSiteRoute: Route =
    path("swagger") {
      getFromResource("swagger-ui/index.html")
    } ~ getFromResourceDirectory("swagger-ui")
}
