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

package drat.proteus.services.general;

import drat.proteus.services.constants.ProteusEndpointConstants;
import javax.ws.rs.HttpMethod;

import javax.ws.rs.client.*;
import javax.ws.rs.core.Response;
import java.util.Map;

public class RestRequest {
  private WebTarget target;
  private Client client;

  public RestRequest(Client client) {
    this.client = client;
  }

  public void buildTarget(String service, String path) {
    // Services under Tomcat are paths to be hung off BASE_URL. Solr is not
    // under Tomcat any more and names its own host, so take it as given.
    String base = service.startsWith("http://") || service.startsWith("https://")
        ? service
        : ProteusEndpointConstants.BASE_URL + service;
    this.target = this.client.target(base).path(path);
  }

  public WebTarget buildTarget(String service, String path,
      Map<String, String> queryParams) {
    buildTarget(service, path);
    for (String q : queryParams.keySet()) {
      this.target = this.target.queryParam(q, queryParams.get(q));
    }
    return target;
  }

  public RestRequest addQueryParam(String key, Object... value) {
    this.target = this.target.queryParam(key, value);
    return this;
  }

  public Response getResponse(String method) {
    Invocation.Builder builder = this.target.request();
    switch (method) {
    case HttpMethod.GET: {
      return builder.get();
    }
    case HttpMethod.HEAD: {
      return builder.head();
    }
    case HttpMethod.DELETE: {
      return builder.delete();
    }
    default: {
      throw new IllegalStateException();
    }
    }
  }

  public Response getResponse(String method, Entity entity) {
    Invocation.Builder builder = target.request();
    switch (method) {
    case HttpMethod.PUT: {
      return builder.put(entity);
    }
    case HttpMethod.POST: {
      return builder.post(entity);
    }
    default: {
      throw new IllegalStateException();
    }
    }
  }
  
  WebTarget getTarget(){
    return this.target;
  }
}
