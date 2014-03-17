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
import java.io.File
import java.io.ByteArrayInputStream
import org.apache.commons.io.FileUtils
import org.apache.commons.io.output.ByteArrayOutputStream
import play.api.Logger

/**
 * On startup, reads the dataset from a 'dataset.json' file on disk at the specified directory.  If
 * the file does not exist, returns an empty dataset,
 *
 * For all non-startup loads, calls the underlying data loader and then writes the data to disk.
 *
 * @author jbetz
 *
 */
class FilesystemCachingDataLoaderProxy(underlying: DatasetLoader, filesystemCacheDir: String) extends DatasetLoader {

  override def loadDataset(isStartup: Boolean): Dataset = {
    val cacheDir = new File(filesystemCacheDir)
    cacheDir.mkdirs()
    val datasetFile = new File(cacheDir, "dataset.json")

    if (isStartup) {
      if (datasetFile.exists()) {
        Logger.info("Startup: Loading cached dataset from " + datasetFile.getAbsolutePath())
        val rawJson = FileUtils.readFileToString(datasetFile)
        DataMapUtils.read(new ByteArrayInputStream(rawJson.getBytes("UTF-8")), classOf[Dataset])
      } else {
        Logger.info("Startup: no cached dataset found at " + datasetFile.getAbsolutePath() + " constructing empty dataset")
        new Dataset // return an empty dataset
      }
    } else {
      val dataset = underlying.loadDataset(isStartup)
      val out = new ByteArrayOutputStream()
      DataMapUtils.write(dataset, out, true)
      val rawJson = new String(out.toByteArray(), "UTF-8")
      FileUtils.writeStringToFile(datasetFile, rawJson)
      Logger.info("Saved cached dataset to " + datasetFile.getAbsolutePath())
      dataset
    }
  }
}