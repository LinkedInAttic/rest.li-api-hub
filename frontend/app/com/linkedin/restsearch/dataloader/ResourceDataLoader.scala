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