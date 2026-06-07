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

package drat.proteus.services.health;

import backend.ProcessDratWrapper;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import drat.proteus.services.constants.ProteusEndpointConstants;
import drat.proteus.services.general.AbstractRestService;
import drat.proteus.services.general.Item;
import org.wicketstuff.rest.utils.http.HttpMethod;

import javax.ws.rs.core.Response;
import java.net.URISyntaxException;
import java.util.Map;

public class HealthMonitorService extends AbstractRestService {
  private static final long HEALTH_CACHE_TTL_MILLIS = 5000L;
  private static final String FM_URL = "http://localhost:9000";
  private static final String FM_GENERIC_FILE = "GenericFile";

  private static final String DAEMON = "daemon";
  private static final String DAEMON_OK_STATUS = "UP";
  private static final String URL = "url";
  private static final String STATUS = "status";

  private static final String FILE_MGR_ABBR = "fm";
  private static final String WORK_MGR_ABBR = "wm";
  private static final String RES_MGR_ABBR = "rm";

  private static final String SPECIFIC_DAEMON_STATUS = ProteusEndpointConstants.HEALTH_STATUS_REPORT
      + "/daemon/";
  private static final String DAEMON_FILE_MGR = SPECIFIC_DAEMON_STATUS
      + FILE_MGR_ABBR;
  private static final String DAEMON_WORK_MGR = SPECIFIC_DAEMON_STATUS
      + WORK_MGR_ABBR;
  private static final String DAEMON_RES_MGR = SPECIFIC_DAEMON_STATUS
      + RES_MGR_ABBR;

  private ProcessDratWrapper dratWrapper;
  private static long cachedHealthMillis;
  private static int cachedHealthStatus;
  private static String cachedHealthBody;

  public HealthMonitorService() {
    super(ProteusEndpointConstants.Services.HEALTH_MONITOR);
    dratWrapper = ProcessDratWrapper.getInstance();
  }

  // Simple function to make a JAX-RS call to the OODT PCS-Health service and
  // route it to /proteus/service/health instead
  public synchronized Response rerouteHealthMonitorData() {
    long now = System.currentTimeMillis();
    if (cachedHealthBody != null
        && now - cachedHealthMillis < HEALTH_CACHE_TTL_MILLIS) {
      return Response.status(cachedHealthStatus).entity(cachedHealthBody).build();
    }

    Response response = null;
    try {
      response = this.createRequest(
          ProteusEndpointConstants.HEALTH_STATUS_REPORT).getResponse(
          HttpMethod.GET);
      cachedHealthStatus = response.getStatus();
      cachedHealthBody = response.readEntity(String.class);
      cachedHealthMillis = now;
      return Response.status(cachedHealthStatus).entity(cachedHealthBody).build();
    } catch (Exception e) {
      cachedHealthStatus = Response.Status.INTERNAL_SERVER_ERROR.getStatusCode();
      cachedHealthBody = "";
      cachedHealthMillis = now;
      return Response.serverError().entity(cachedHealthBody).build();
      // if response has an exception, let's assume that OODT cannot be accessed
      // (aka it's been stopped/not started)
    } finally {
      if (response != null) {
        response.close();
      }
    }
  }

  public String getDratStatus() {
    return dratWrapper.getStatus();
  }

  public boolean getOodtStatus() {
    try {
      Response response = rerouteHealthMonitorData();
      if (response == null || response.getStatus() > 300) { // there was an error
                                                            // in the response,
                                                            // possibly caused by
                                                            // misconfig or OODT
                                                            // not being on
        return false;
      }
      String jsonBody;
      try {
        jsonBody = response.readEntity(String.class);
      } finally {
        response.close();
      }
      GsonBuilder g = new GsonBuilder();
      g.serializeSpecialFloatingPointValues();
      Gson gson = g.create();

      Map<String, Object> rawStatusOutput = gson.fromJson(jsonBody,
          Map.class);
      Map<String, Object> report = (Map<String, Object>) rawStatusOutput
          .get("report");
      Map<String, Object> daemonStatus = (Map<String, Object>) report
          .get("daemonStatus");
      HealthMonitorItem fileManager = (HealthMonitorItem) parseJsonMap((Map<String, Object>) daemonStatus
          .get(FILE_MGR_ABBR));
      HealthMonitorItem resManager = (HealthMonitorItem) parseJsonMap((Map<String, Object>) daemonStatus
          .get(RES_MGR_ABBR));
      HealthMonitorItem workflowManager = (HealthMonitorItem) parseJsonMap((Map<String, Object>) daemonStatus
          .get(WORK_MGR_ABBR));
      return fileManager.isRunning()
          && resManager.isRunning() && workflowManager.isRunning();
    } catch (Exception e) {
      e.printStackTrace();
      return false;
    }
  }

  public Item getFileManagerStatus() throws URISyntaxException {
    return getDaemonStatus(DAEMON_FILE_MGR);
  }

  public Item getResourceManagerStatus() throws URISyntaxException {
    return getDaemonStatus(DAEMON_RES_MGR);
  }

  public Item getWorkflowManagerStatus() throws URISyntaxException {
    return getDaemonStatus(DAEMON_WORK_MGR);
  }

  private Item getDaemonStatus(String daemonPath) throws URISyntaxException {
    Response response = this.createRequest(daemonPath).getResponse(
        HttpMethod.GET);
    String jsonBody;
    try {
      jsonBody = response.readEntity(String.class);
    } finally {
      response.close();
    }
    GsonBuilder g = new GsonBuilder();
    g.serializeSpecialFloatingPointValues();
    Gson gson = g.create();
    Map<String, Object> daemonStatus = gson.fromJson(jsonBody, Map.class);
    return parseJsonMap(daemonStatus);
  }

  private Item parseJsonMap(Map<String, Object> daemonStatus) {
    try {
      boolean isRunning = (((String) daemonStatus.get(STATUS)).toUpperCase()
          .equals(DAEMON_OK_STATUS));
      return new HealthMonitorItem((String) daemonStatus.get(DAEMON),
          (String) daemonStatus.get(URL), isRunning);
    } catch (URISyntaxException urise) {
      urise.printStackTrace();
      return null; // this shouldn't happen unless PCS returns a broken link
    }
  }

}
