/*
   Copyright (c) 2014 LinkedIn Corp.

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
*/

package com.linkedin.restsearch.permlink

import play.api.cache.Cache
import play.api.libs.ws.WS
import play.api.libs.json.Json
import play.api.Play.current
import scala.concurrent.Future
import play.api.libs.concurrent.Execution.Implicits._

case class PasteInException(message: String) extends Exception(message)

/**
 * API Client for the go/pastein service.  Since pastes are immutable, includes a read in memory cache with a 1 hr TTL.
 */
object CachingPasteInClient {
  def load(pasteinId: String): Future[String] = {
    val key = "paste_id=" + pasteinId
    Cache.getOrElse(key, 1*60*60 /* 1 hr cache */) {
      val promise = loadFromPasteIn(pasteinId)
      promise.map { pasteinId =>
        Cache.set(key, pasteinId)
        pasteinId
      }
    }
  }

  private def loadFromPasteIn(permlink: String): Future[String] = {
    val pasteRequestJson = Json.obj(
      "paste_id" -> permlink
    )
    val promise = WS.url("http://paste.corp.linkedin.com/json/?method=pastes.getPaste").post(pasteRequestJson)
    promise.map { pasteResponse =>
      val pasteResponseJson = pasteResponse.json
      val pasteError = (pasteResponseJson \ "error").asOpt[String]
      if (pasteError.isEmpty)
      {
        (pasteResponseJson \ "data" \ "code").as[String]
      }
      else
      {
        throw new PasteInException(pasteError.get)
      }
    }
  }

  def store(code: String): Future[String] = {
    val pasteDataJson = Json.obj(
      "language" -> "None",
      "code" -> code
    )
    val promise = WS.url("http://paste.corp.linkedin.com/json/?method=pastes.newPaste").post(pasteDataJson)
    promise.map { pasteResponse =>
      val pasteResponseJson = pasteResponse.json
      val pasteError = (pasteResponseJson \ "error").asOpt[String]
      if (pasteError.isEmpty)
      {
        val pasteinId = (pasteResponseJson \ "data").as[String]
        Cache.set("paste_id=" + pasteinId, code)
        pasteinId
      }
      else
      {
        throw new PasteInException(pasteError.get)
      }
    }
  }
}
