package com.linkedin.restsearch.dataloader

import com.linkedin.restsearch.Dataset

/**
 * Provides a mechanism for loading a dataset.
 * 
 * @author jbetz
 *
 */
trait DatasetLoader {
  def loadDataset(isStartup: Boolean): Dataset
}