package com.linkedin.restsearch.permlink

import scala.concurrent.Future

case class PastebinException(message: String) extends Exception(message)

trait PastebinClient {
  def load(id: String): Future[String]
  def store(code: String): Future[String]
}
