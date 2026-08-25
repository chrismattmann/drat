/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package drat.proteus.services.product;

import backend.OodtClientPool;
import drat.proteus.services.general.AbstractRestService;
import drat.proteus.services.constants.ProteusEndpointConstants;
import drat.proteus.services.general.Item;
import org.apache.oodt.cas.filemgr.structs.Product;
import org.apache.oodt.cas.filemgr.structs.Reference;
import org.apache.oodt.cas.filemgr.system.FileManagerClient;

import java.io.File;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

public class BaseProductService extends AbstractRestService {
  private static final Logger LOG = Logger.getLogger(BaseProductService.class.getName());

  public BaseProductService() {
    super(ProteusEndpointConstants.Services.FILE_MANAGER_PRODUCT);
  }

  protected List<Item> getRecentProductsByChannel(String channel) {
    return generateProducts(10);
  }

  protected List<Item> getRecentProductsByChannelAndTypeId(String channel,
      String typeId) {
    return generateProducts(10);
  }

  private List<Item> generateProducts(int topN) {
    List<Item> products = new ArrayList<Item>();
    try {
      OodtClientPool.withFileManagerClient(client -> {
        List<Product> recentProducts = client.getTopNProducts(topN);
        for (Product product : recentProducts) {
          products.add(createProductItem(client, product));
        }
        return null;
      });
    } catch (Exception e) {
      LOG.warning("Unable to get recent File Manager products: Message: "
          + e.getLocalizedMessage());
    }
    return products;
  }

  private ProductItem createProductItem(FileManagerClient client, Product product) {
    String source = getFilePath(client, product);
    if (source == null) {
      source = "";
    }
    String productType = product.getProductType() == null ? ""
        : product.getProductType().getName();
    return new ProductItem(product.getProductName(), productType,
        product.getProductReceivedTime(), source, source, source);
  }

  private String getFilePath(FileManagerClient client, Product product) {
    try {
      if (product.getProductReferences() == null) {
        product.setProductReferences(client.getProductReferences(product));
      }
      if (product.getProductReferences() == null
          || product.getProductReferences().isEmpty()) {
        return "";
      }
      Reference reference = (Reference) product.getProductReferences().get(0);
      return new File(new URI(reference.getDataStoreReference())).getAbsolutePath();
    } catch (Exception e) {
      LOG.warning("Unable to resolve File Manager product path: Message: "
          + e.getLocalizedMessage());
      return "";
    }
  }
}
