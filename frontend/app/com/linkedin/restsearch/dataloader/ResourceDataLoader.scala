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

package com.linkedin.restsearch.dataloader

import com.linkedin.restli.internal.server.util.DataMapUtils
import com.linkedin.restsearch.Dataset
import java.io.ByteArrayInputStream
import org.apache.commons.io.IOUtils
import play.api.Play


/**
 * For development.
 *
 * Loads dataset from a 'dataset.json' resource file at the given path.
 * @author jbetz
 *
 */
class ResourceDataLoader(resourcePath: String) extends DatasetLoader {
  override def loadDataset(isStartup: Boolean): Dataset = {
    val rawJson = Play.resourceAsStream(resourcePath)(Play.current) match {
      case Some(stream) => IOUtils.toString(stream)
      case None => throw new IllegalArgumentException("No dataset file at resource: " + resourcePath)
    }
    DataMapUtils.read(new ByteArrayInputStream(rawJson.getBytes("UTF-8")), classOf[Dataset])
  }
}