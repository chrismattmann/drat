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

package drat.proteus.services.constants;

public class ProteusEndpointConstants {

  /** System property naming the Tomcat DRAT's web applications run in. */
  public static final String BASE_URL_PROPERTY = "drat.base.url";

  /** Environment variable equivalent of {@link #BASE_URL_PROPERTY}. */
  public static final String BASE_URL_ENV = "DRAT_BASE_URL";

  /** System property naming the Solr DRAT indexes into, without a path. */
  public static final String SOLR_BASE_URL_PROPERTY = "drat.solr.base.url";

  /** Environment variable equivalent of {@link #SOLR_BASE_URL_PROPERTY}. */
  public static final String SOLR_BASE_URL_ENV = "DRAT_SOLR_BASE_URL";

  /**
   * Where a value comes from, in order: a system property, then an
   * environment variable, then the address that used to be compiled in.
   *
   * <p>
   * These were literals. That works only on a machine where DRAT owns 8080
   * and 8983, and quietly does the wrong thing where it does not: on a host
   * running a second OODT deployment, the Solr on 8983 belongs to something
   * else, and the mime type breakdown queries a core that is not DRAT's --
   * returning nothing, with no error to say why. A deployment has to be able
   * to say where its own services are.
   * </p>
   *
   * @param property system property to read first
   * @param env      environment variable to read next
   * @param fallback the address to use when neither is set
   * @return the configured base URL, without a trailing slash
   */
  static String configuredBaseUrl(String property, String env,
      String fallback) {
    String value = System.getProperty(property);
    if (value == null || value.trim().length() == 0) {
      value = System.getenv(env);
    }
    if (value == null || value.trim().length() == 0) {
      value = fallback;
    }
    value = value.trim();
    while (value.endsWith("/")) {
      value = value.substring(0, value.length() - 1);
    }
    return value;
  }

  public static final String BASE_URL = configuredBaseUrl(
      BASE_URL_PROPERTY, BASE_URL_ENV, "http://localhost:8080");

  /**
   * Solr runs as its own application on its own port now, rather than as a
   * war inside the Tomcat that BASE_URL points at, so its services carry a
   * whole URL instead of a path under BASE_URL.
   */
  public static final String SOLR_BASE_URL = configuredBaseUrl(
      SOLR_BASE_URL_PROPERTY, SOLR_BASE_URL_ENV, "http://localhost:8983");
  public static final String HEALTH_STATUS_REPORT = "report";
  public static final String MIME_TYPE_SELECT = "select";

  public static class Services {
    public static final String MIME_TYPE_BREAKDOWN = SOLR_BASE_URL + "/solr/drat";
    public static final String FILE_MANAGER_PRODUCT = "/opsui";
    public static final String HEALTH_MONITOR = "/pcs/services/health";

    /**
     * What the deployment's workflow manager declares about its own statuses,
     * including which lifecycle stage each belongs to. Read over HTTP rather
     * than through the workflow client: DRAT builds against a released
     * Mnemosyne, so a method added there is not callable here until it ships,
     * while an endpoint that grows a field is readable as soon as the
     * deployment runs one that sends it.
     */
    public static final String WORKFLOW_STATUSES = "/pcs/services/workflow/statuses";
    public static final String RAT_INSTANCES_MONITOR = "/opsui/instances";
  }
}
