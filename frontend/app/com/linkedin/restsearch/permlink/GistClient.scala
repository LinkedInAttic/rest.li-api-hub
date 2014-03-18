package com.linkedin.restsearch.permlink

import play.api.cache.Cache
import play.api.libs.ws.WS
import play.api.libs.json._
import play.api.Play.current
import scala.concurrent.Future
import play.api.libs.concurrent.Execution.Implicits._

class GistClient extends PastebinClient {

  def load(id: String): Future[String] = {
    val key = "gist_id=" + id
    Cache.getOrElse(key, 1*60*60 /* 1 hr cache */) {
      val promise = loadFromGist(id)
      promise.map { pasteinId =>
        Cache.set(key, pasteinId)
        pasteinId
      }
    }
  }

  private def loadFromGist(id: String): Future[String] = {

    val promise = WS.url("https://api.github.com/gists/" + id).get()
    promise.map { pasteResponse =>
      if (pasteResponse.status == 200)
      {
        val pasteResponseJson = pasteResponse.json
        (pasteResponseJson \ "files" \\ "content").headOption.map(_.as[String]).getOrElse(throw new PastebinException("Malformed gist, expected one file."))
      }
      else
      {
        throw new PastebinException(pasteResponse.statusText)
      }
    }
  }

  def store(code: String): Future[String] = {
    val pasteDataJson = Json.obj(
      "public" -> true,
      "files" ->Json.obj(
        "paste.json" -> Json.obj(
          "content" -> code
        )
      )
    )

    val promise = WS.url("https://api.github.com/gists").post(pasteDataJson)
    promise.map { pasteResponse =>
      if (pasteResponse.status == 201)
      {
        val gistId = (pasteResponse.json \ "id").as[String]
        Cache.set("gist_id=" + gistId, code)
        gistId
      }
      else
      {
        throw new PastebinException(pasteResponse.statusText)
      }
    }
  }
}
