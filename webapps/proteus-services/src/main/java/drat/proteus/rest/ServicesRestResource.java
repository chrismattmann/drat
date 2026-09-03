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
import org.apache.commons.io.filefilter.IOFileFilter;
import org.apache.commons.io.filefilter.NameFileFilter;

import javax.ws.rs.core.Response;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import org.apache.oodt.cas.workflow.structs.WorkflowInstance;
import backend.OodtClientPool;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.oodt.cas.filemgr.structs.ProductType;
import java.util.LinkedHashSet;
import java.util.Set;
import backend.RunMarker;
import backend.FileConstants;
import drat.proteus.services.general.Item;
import drat.proteus.services.health.HealthMonitorService;
import drat.proteus.services.licenses.RatInstanceService;
import drat.proteus.services.mimetype.MimeTypeBreakdownService;
import drat.proteus.services.product.RecentProductService;

@Path("/service")
@Produces(MediaType.APPLICATION_JSON)
public class ServicesRestResource {

  /** The task a RAT audit runs as, from the audit pipeline's policy. */
  private static final String RAT_TASK_ID = "urn:drat:RatCodeAudit";

  /** One of these is written by each audit that finishes. */
  private static final String RAT_LOG_TYPE = "RatLog";

  /** The statuses an instance can be in while something else holds it up. */
  private static final String[] GATED_STATUSES =
      new String[] {"PreConditionEval", "Blocked", "Queued"};

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
  public List<Item> getRecentProducts(
      @QueryParam("type") @DefaultValue("") String type) {
    // Named type or nothing. The list this feeds is the repository's files,
    // and a run's own RAT logs are the newest products in the catalog the
    // moment it starts making them.
    if (type != null && type.trim().length() > 0) {
      return productService.getRecentProductsByType(type.trim());
    }
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
      @QueryParam("dir") @DefaultValue("NOTPROVIDED") String repoPath,
      @QueryParam("exclude") @DefaultValue("") String excludeNames) {
    if (repoPath.equals("NOTPROVIDED")) {
      repoPath = FileConstants.DRAT_TEMP_UNZIPPED_PATH;
    }

    File repoDir = new File(repoPath);
    long repoSize = 0;
    long numFiles = 0;
    if (repoDir.exists()) {
      // What the run was told to skip is skipped here too. Counting every
      // file in the repository gives a total the crawl is never going to
      // reach -- an audit of Tika excluding target and .git can only ever
      // reach 3,519 of the 10,473 files on disk -- so a finished crawl reads
      // as one stalled at a third, which is worse than no bar at all.
      // What the caller names, or failing that what the current run was told.
      // A caller that knows the answer can keep asking about the same files
      // after the run has ended, rather than watching the totals jump back to
      // the whole repository the moment the marker goes.
      List<String> excluded = new ArrayList<String>();
      if (excludeNames != null && excludeNames.trim().length() > 0) {
        for (String name : excludeNames.split(",")) {
          if (name.trim().length() > 0) {
            excluded.add(name.trim());
          }
        }
      } else {
        excluded = RunMarker.excludes();
      }
      IOFileFilter directories = excluded.isEmpty()
          ? FileFilterUtils.directoryFileFilter()
          : FileFilterUtils.and(FileFilterUtils.directoryFileFilter(),
              FileFilterUtils.notFileFilter(
                  new NameFileFilter(excluded.toArray(new String[0]))));

      Collection<File> repoFiles = FileUtils.listFiles(repoDir,
          FileFilterUtils.trueFileFilter(), directories);
      if (repoFiles != null) {
        for (File counted : repoFiles) {
          // Empty files are not counted, because they are not crawled: the
          // crawler holds a precondition that a file be larger than nothing
          // before it will ingest one, there being no licence to find in an
          // empty file. Counting them made a completed crawl of Tika read
          // 3503 of 3519 and sit there, with the sixteen it never reached
          // being sixteen empty files it was never going to.
          if (counted.length() <= 0) {
            continue;
          }
          numFiles++;
          repoSize += counted.length();
        }
      }
    }

    Map<String, Long> repoSizeInfo = new ConcurrentHashMap<String, Long>();
    repoSizeInfo.put("numberOfFiles", numFiles);
    repoSizeInfo.put("memorySize", repoSize);
    return repoSizeInfo;
  }

  /**
   * How many RAT audits are running, and how many have finished.
   *
   * <p>
   * Counted from the RAT audits themselves rather than from every instance
   * the workflow manager holds. A run's instances are mostly its own
   * scaffolding -- the pipeline, its phases, the redirectors and the
   * conditions gating them -- and counting those reported five RAT audits
   * running before RAT had been asked to do anything at all.
   * </p>
   *
   * <p>
   * Finished audits are counted as RAT logs, because a RAT audit that has
   * finished is exactly one log in the catalog, and that is a cheap and
   * unambiguous question to ask. Running ones are counted from the instances
   * currently executing, which is a small set however large the run.
   * </p>
   */
  @GET
  @Path("/rat/progress")
  public Map<String, Long> getRatProgress() {
    Map<String, Long> progress = new ConcurrentHashMap<String, Long>();
    progress.put("finished", countRatLogs());
    progress.put("running", countExecutingRatAudits());
    return progress;
  }

  /** One log per finished audit. */
  private long countRatLogs() {
    final long[] logs = new long[1];
    try {
      OodtClientPool.withFileManagerClient(client -> {
        ProductType type = client.getProductTypeByName(RAT_LOG_TYPE);
        logs[0] = type == null ? 0 : client.getNumProducts(type);
        return null;
      });
    } catch (Exception e) {
      LOG.warning("Unable to count RAT logs: " + e.getMessage());
    }
    return logs[0];
  }

  /**
   * The audits executing right now.
   *
   * <p>
   * Asked of the PCS instance service rather than of a workflow manager
   * client here: this webapp's own client cannot list instances -- it answers
   * every such request with an empty list and an unwound interceptor -- and
   * that is a older fault than this method. The service is the same one the
   * health panel is read through, on this deployment's own Tomcat.
   * </p>
   */
  private long countExecutingRatAudits() {
    long running = 0;
    try {
      URL url = new URL(ProteusEndpointConstants.BASE_URL
          + "/pcs/services/workflow/instances?status=Executing&page=1");
      HttpURLConnection connection = (HttpURLConnection) url.openConnection();
      connection.setConnectTimeout(5000);
      connection.setReadTimeout(10000);
      try {
        if (connection.getResponseCode() != 200) {
          return 0;
        }
        StringBuilder body = new StringBuilder();
        BufferedReader reader = new BufferedReader(new InputStreamReader(
            connection.getInputStream(), StandardCharsets.UTF_8));
        try {
          String line;
          while ((line = reader.readLine()) != null) {
            body.append(line);
          }
        } finally {
          reader.close();
        }

        JsonObject page = JsonParser.parseString(body.toString())
            .getAsJsonObject().getAsJsonObject("page");
        JsonArray instances = page.getAsJsonArray("instances");
        for (int i = 0; i < instances.size(); i++) {
          JsonObject instance = instances.get(i).getAsJsonObject();
          if (instance.has("currentTaskId") && RAT_TASK_ID.equals(
              instance.get("currentTaskId").getAsString())) {
            running++;
          }
        }
      } finally {
        connection.disconnect();
      }
    } catch (Exception e) {
      LOG.warning("Unable to count running RAT audits: " + e.getMessage());
    }
    return running;
  }

  /**
   * What the run is waiting for, in its own words.
   *
   * <p>
   * A run can sit for a minute or more with everything apparently done: the
   * files crawled, the audits finished, the charts drawn, and the aggregate
   * step still waiting for its conditions to agree that no more logs are
   * coming. Watching that without being told is watching nothing happen. The
   * engine records why each instance is waiting; this is that, gathered for
   * the page.
   * </p>
   */
  @GET
  @Path("/waiting")
  public Map<String, Object> getWaitingOn() {
    Set<String> reasons = new LinkedHashSet<String>();
    for (String status : GATED_STATUSES) {
      reasons.addAll(waitingReasons(status));
    }
    Map<String, Object> waiting = new ConcurrentHashMap<String, Object>();
    waiting.put("reasons", new ArrayList<String>(reasons));
    return waiting;
  }

  /** The reasons carried by instances in one status. */
  private List<String> waitingReasons(String status) {
    List<String> reasons = new ArrayList<String>();
    try {
      URL url = new URL(ProteusEndpointConstants.BASE_URL
          + "/pcs/services/workflow/instances?status=" + status + "&page=1");
      HttpURLConnection connection = (HttpURLConnection) url.openConnection();
      connection.setConnectTimeout(5000);
      connection.setReadTimeout(10000);
      try {
        if (connection.getResponseCode() != 200) {
          return reasons;
        }
        StringBuilder body = new StringBuilder();
        BufferedReader reader = new BufferedReader(new InputStreamReader(
            connection.getInputStream(), StandardCharsets.UTF_8));
        try {
          String line;
          while ((line = reader.readLine()) != null) {
            body.append(line);
          }
        } finally {
          reader.close();
        }
        JsonArray instances = JsonParser.parseString(body.toString())
            .getAsJsonObject().getAsJsonObject("page")
            .getAsJsonArray("instances");
        for (int i = 0; i < instances.size(); i++) {
          JsonObject instance = instances.get(i).getAsJsonObject();
          if (instance.has("waitingOn")
              && !instance.get("waitingOn").isJsonNull()) {
            String reason = instance.get("waitingOn").getAsString();
            if (reason != null && reason.length() > 0) {
              reasons.add(reason);
            }
          }
        }
      } finally {
        connection.disconnect();
      }
    } catch (Exception e) {
      LOG.warning("Unable to read what is being waited on: " + e.getMessage());
    }
    return reasons;
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
