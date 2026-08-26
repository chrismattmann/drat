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

package drat.proteus.rest;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.UriInfo;

import drat.proteus.services.constants.ProteusEndpointConstants;

/**
 * Read-only passthrough to Solr for the browser.
 *
 * The Proteus charts query Solr directly. That worked while Solr was a war
 * inside the same Tomcat the interface is served from, because the request was
 * same-origin. Solr now runs as its own application on its own port, so those
 * requests became cross-origin and the browser blocks them.
 *
 * Rather than enable CORS on Solr -- which would mean editing the bundled
 * Jetty inside solr-server/, the directory meant to be replaced wholesale on
 * the next upgrade -- the charts come through here instead, and stay
 * same-origin.
 *
 * Only select is exposed, and only over GET. The browser has no business
 * reaching /update, and this deliberately gives it no way to.
 */
@Path("/solr")
@Produces(MediaType.APPLICATION_JSON)
public class SolrQueryResource {

  private static final Logger LOG = Logger.getLogger(SolrQueryResource.class.getName());

  /** The cores DRAT actually has. An unknown name is not forwarded. */
  private static final List<String> CORES = java.util.Arrays.asList("drat", "statistics");

  @GET
  @Path("/{core}/select")
  public String select(@PathParam("core") String core, @Context UriInfo uriInfo) {
    if (!CORES.contains(core)) {
      LOG.warning("Refusing to proxy a query for unknown core: [" + core + "]");
      return "{\"error\":\"unknown core\"}";
    }

    StringBuilder url = new StringBuilder(ProteusEndpointConstants.SOLR_BASE_URL);
    url.append("/solr/").append(core).append("/select");

    String separator = "?";
    MultivaluedMap<String, String> params = uriInfo.getQueryParameters();
    for (Map.Entry<String, List<String>> param : params.entrySet()) {
      for (String value : param.getValue()) {
        url.append(separator);
        url.append(encode(param.getKey())).append('=').append(encode(value));
        separator = "&";
      }
    }

    return read(url.toString());
  }

  private static String encode(String value) {
    try {
      return URLEncoder.encode(value, "UTF-8");
    } catch (Exception e) {
      return value;
    }
  }

  private String read(String target) {
    HttpURLConnection connection = null;
    try {
      connection = (HttpURLConnection) new URL(target).openConnection();
      connection.setConnectTimeout(2000);
      connection.setReadTimeout(30000);
      InputStream stream = connection.getResponseCode() < 400
          ? connection.getInputStream() : connection.getErrorStream();
      if (stream == null) {
        return "{\"error\":\"Solr returned HTTP " + connection.getResponseCode() + "\"}";
      }
      try {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] bytes = new byte[4096];
        int count;
        while ((count = stream.read(bytes)) != -1) {
          buffer.write(bytes, 0, count);
        }
        return new String(buffer.toByteArray(), StandardCharsets.UTF_8);
      } finally {
        stream.close();
      }
    } catch (Exception e) {
      LOG.warning("Unable to query Solr at [" + target + "]: " + e.getLocalizedMessage());
      return "{\"error\":\"Unable to reach Solr\"}";
    } finally {
      if (connection != null) {
        connection.disconnect();
      }
    }
  }
}
