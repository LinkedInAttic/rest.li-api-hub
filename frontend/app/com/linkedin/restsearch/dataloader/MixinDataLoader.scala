package com.linkedin.restsearch.dataloader

import com.linkedin.restli.internal.server.util.DataMapUtils
import com.linkedin.restsearch._
import java.io.ByteArrayInputStream
import org.apache.commons.io.IOUtils
import play.api.Play
import scala.collection.JavaConversions._
import scala.Some
import com.linkedin.restsearch.template.utils.Conversions._


/**
 * For development.
 *
 * Loads dataset from a 'dataset.json' resource file at the given path.
 * @author jbetz
 *
 */
class MixinDataLoader(underlying: DatasetLoader, resourcePath: String) extends DatasetLoader {
  override def loadDataset(isStartup: Boolean): Dataset = {
    val rawJson = Play.resourceAsStream(resourcePath)(Play.current) match {
      case Some(stream) => IOUtils.toString(stream)
      case None => throw new IllegalArgumentException("No dataset file at resource: " + resourcePath)
    }
    val dataset = underlying.loadDataset(isStartup)
    val mixin = DataMapUtils.read(new ByteArrayInputStream(rawJson.getBytes("UTF-8")), classOf[Mixin])
    merge(dataset, mixin)
  }

  def merge(dataset: Dataset, mixin: Mixin): Dataset = {
    val services = dataset.servicesMap
    val resourceProvenanceMap = mapAsScalaMap(mixin.getResourceProvenance())
    resourceProvenanceMap foreach { case (key, provenance) =>
      services.get(key) match {
        case Some(service) => {
          service.setProvenance(provenance)
        }
        case None => ()
      }
    }

    dataset
  }
}