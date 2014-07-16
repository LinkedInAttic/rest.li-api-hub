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

package com.linkedin.restsearch.utils

import com.linkedin.restsearch.Cluster
import com.linkedin.restsearch.template.utils.Conversions._

/**
 * Utility functions used in views involving Clusters
 *
 * @author kparikh
 */
object ClusterViewUtil {

  /**
   * @param clusters potential clusters to display. These all represent the same logical cluster and are colo variants.
   * @return true if we should display this cluster on the UI, false otherwise.
   */
  def shouldDisplay(clusters: List[Cluster]): Boolean = {
    clusters.foreach { cluster =>
      if (cluster.services(primaryOnly = true).nonEmpty) {
        return true
      }
    }
    false
  }

  /**
   * Get the primary colo variant for a cluster.
   * @param clusters clusters to get the primary for
   * @return the primary cluster
   */
  def getPrimaryColoVariant(clusters: List[Cluster]): Option[Cluster] = {
    val clustersWithPrimaryServices = clusters.filter(_.services(primaryOnly = true).nonEmpty)
    clustersWithPrimaryServices.length match {
      case 0 => None
      // if multiple clusters have primary services it means that there is a master colo. We prefer the master in this
      // case.
      case 1 => Some(clustersWithPrimaryServices(0))
      case _ => Some(clustersWithPrimaryServices.filter(_.getName.endsWith("Master"))(0))
    }
  }
}
