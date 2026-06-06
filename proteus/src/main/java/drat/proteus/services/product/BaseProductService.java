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

import backend.FileConstants;
import drat.proteus.services.general.AbstractRestService;
import drat.proteus.services.constants.ProteusEndpointConstants;
import drat.proteus.services.general.Item;
import org.apache.oodt.cas.filemgr.structs.Product;
import org.apache.oodt.cas.metadata.util.PathUtils;
import org.apache.oodt.pcs.util.FileManagerUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

public class BaseProductService extends AbstractRestService {
  private static final Logger LOG = Logger.getLogger(BaseProductService.class.getName());
  private final FileManagerUtils fileManagerUtils;

  public BaseProductService() {
    super(ProteusEndpointConstants.Services.FILE_MANAGER_PRODUCT);
    fileManagerUtils = new FileManagerUtils(
        PathUtils.replaceEnvVariables(FileConstants.FILEMGR_URL));
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
      List<Product> recentProducts = fileManagerUtils.safeGetTopNProducts(topN);
      for (Product product : recentProducts) {
        products.add(createProductItem(product));
      }
    } catch (Exception e) {
      LOG.warning("Unable to get recent File Manager products: Message: "
          + e.getLocalizedMessage());
    }
    return products;
  }

  private ProductItem createProductItem(Product product) {
    String source = fileManagerUtils.getFilePath(product);
    if (source == null) {
      source = "";
    }
    String productType = product.getProductType() == null ? ""
        : product.getProductType().getName();
    return new ProductItem(product.getProductName(), productType,
        product.getProductReceivedTime(), source, source, source);
  }
}
