package kezek.order.core.exception

import akka.http.scaladsl.model.StatusCode
import ApiException.{ErrorMessage, system}

object ApiException {

  case class ErrorMessage(system: Int, error: String)

  val system: Int = 3

  def throwableToErrorMessage(ex: Throwable): ErrorMessage = {
    ErrorMessage(error = ex.getMessage, system = system)
  }

}

case class ApiException(code: StatusCode, message: String, system: Int = system) extends RuntimeException {

  override def getMessage: String = message

  def toErrorMessage: ErrorMessage = {
    ErrorMessage(error = message, system = system)
  }
}

