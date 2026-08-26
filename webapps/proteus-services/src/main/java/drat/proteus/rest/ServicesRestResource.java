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

import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.core.MediaType;

import java.io.File;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import drat.proteus.services.constants.ProteusEndpointConstants;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.FileFilterUtils;

import javax.ws.rs.core.Response;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import backend.FileConstants;
import drat.proteus.services.general.Item;
import drat.proteus.services.health.HealthMonitorService;
import drat.proteus.services.licenses.RatInstanceService;
import drat.proteus.services.mimetype.MimeTypeBreakdownService;
import drat.proteus.services.product.RecentProductService;

@Path("/service")
@Produces(MediaType.APPLICATION_JSON)
public class ServicesRestResource {

  private static final long serialVersionUID = -963632756412793830L;
  private static final Logger LOG = Logger
      .getLogger(ServicesRestResource.class.getName());
  private RecentProductService productService;
  private HealthMonitorService healthMonitorService;
  private MimeTypeBreakdownService mimeTypeBreakdownService;
  private RatInstanceService ratInstanceService;

  public ServicesRestResource() {
    productService = new RecentProductService();
    healthMonitorService = new HealthMonitorService();
    mimeTypeBreakdownService = new MimeTypeBreakdownService();
    ratInstanceService = new RatInstanceService();
  }

  @GET
  @Path("/repo/licenses/unapproved")
  public List<Item> getUnapprovedLicensesFromRatInstances() {
    List<Item> licenses = new ArrayList<Item>();
    try {
      ratInstanceService.getRatLogs();
      licenses = ratInstanceService.getUnapprovedLicenses();
    } catch (Exception e) {
      e.printStackTrace();
      LOG.warning("Error obtaining unapproved licenses from RAT: Message: "
          + e.getLocalizedMessage());
    }

    return licenses;
  }

  @GET
  @Path("/products")
  public List<Item> getRecentProducts() {
    return productService.getAllRecentProducts();
  }

  @GET
  @Path("/repo/breakdown/mime")
  public List<Item> getRepoMimeTypeBreakdown(
      @QueryParam("limit") Integer limit) {
    if (limit == null) {
      limit = 0;
    }
    return mimeTypeBreakdownService.getMimeTypes(limit);
  }

  @GET
  @Path("/repo/breakdown/license")
  public List<Item> getRepoLicenseTypeBreakdown() {
    List<Item> breakdown = new ArrayList<Item>();
    try {
      ratInstanceService.getRatLogs();
      breakdown = ratInstanceService.getLicenseTypeBreakdown();
    } catch (Exception e) {
      e.printStackTrace();
      LOG.warning("Unable to get repo license type breakdown: Message: "
          + e.getLocalizedMessage());
    }

    return breakdown;
  }

  @GET
  @Path("/repo/size")
  public Map<String, Long> getRepositorySize(
      @QueryParam("dir") @DefaultValue("NOTPROVIDED") String repoPath) {
    if (repoPath.equals("NOTPROVIDED")) {
      repoPath = FileConstants.DRAT_TEMP_UNZIPPED_PATH;
    }

    File repoDir = new File(repoPath);
    long repoSize = 0;
    long numFiles = 0;
    if (repoDir.exists()) {
      repoSize = FileUtils.sizeOfDirectory(repoDir);
      numFiles = -1;
      Collection<File> repoFiles = FileUtils.listFiles(repoDir,
          FileFilterUtils.trueFileFilter(),
          FileFilterUtils.directoryFileFilter());
      if (repoFiles != null && repoFiles.size() > 0) {
        numFiles = repoFiles.size();
      } else
        numFiles = 0;
    }

    Map<String, Long> repoSizeInfo = new ConcurrentHashMap<String, Long>();
    repoSizeInfo.put("numberOfFiles", numFiles);
    repoSizeInfo.put("memorySize", repoSize);
    return repoSizeInfo;
  }

  @GET
  @Path("/status/drat")
  @Produces(MediaType.TEXT_PLAIN)
  public String getDratRunningStatus() {
    return healthMonitorService.getDratStatus().toUpperCase();
  }

  @GET
  @Path("/status/oodt")
  public String getOodtRunningStatus() {
    // This is the implementation that was actually serving. WicketApplication
    // mounted /service/status/oodt ahead of /service, so its hand-written
    // resource won and the HealthMonitorService version below it never ran.
    // Kept as-is rather than quietly switching behaviour along with the
    // transport; consolidating the two is a separate decision.
    String healthJson = readOodtHealthJson();
    boolean isUp = healthJson.contains("\"fm\":{\"url\"")
        && healthJson.contains("\"fm\":{\"url\":\"http://localhost:9000\",\"daemon\":\"File Manager\",\"status\":\"UP\"")
        && healthJson.contains("\"wm\":{\"url\":\"http://localhost:9001\",\"daemon\":\"Workflow Manager\",\"status\":\"UP\"")
        && healthJson.contains("\"rm\":{\"url\":\"http://localhost:9002\",\"daemon\":\"Resource Manager\",\"status\":\"UP\"");
    return String.valueOf(isUp);
  }

  @GET
  @Path("/status/oodt/raw")
  public String getOodtRawHealthStatus() {
    // Also the implementation that was serving: a straight passthrough of the
    // health monitor's JSON, not the Gson-parsed Map the shadowed version built.
    return readOodtHealthJson();
  }

  private String readOodtHealthJson() {
    HttpURLConnection connection = null;
    try {
      URL healthUrl = new URL(ProteusEndpointConstants.BASE_URL
          + ProteusEndpointConstants.Services.HEALTH_MONITOR + "/"
          + ProteusEndpointConstants.HEALTH_STATUS_REPORT);
      connection = (HttpURLConnection) healthUrl.openConnection();
      connection.setConnectTimeout(2000);
      connection.setReadTimeout(5000);
      InputStream stream = connection.getResponseCode() < 400
          ? connection.getInputStream() : connection.getErrorStream();
      if (stream == null) {
        return "{\"error\":\"OODT health service returned HTTP "
            + connection.getResponseCode() + "\"}";
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
      LOG.warning("Unable to read OODT health status: " + e.getLocalizedMessage());
      return "{\"error\":\"Unable to read OODT health status\"}";
    } finally {
      if (connection != null) {
        connection.disconnect();
      }
    }
  }
}
