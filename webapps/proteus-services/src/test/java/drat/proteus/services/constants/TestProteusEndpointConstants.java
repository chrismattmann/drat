/*
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

import org.junit.After;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Where DRAT's services are is the deployment's to say.
 *
 * <p>
 * These addresses were compiled in. On a host where DRAT does not own 8080
 * and 8983 -- one running a second OODT deployment, say -- the mime type
 * breakdown queried a Solr belonging to something else and returned nothing,
 * and a reset issued deleteByQuery("*:*") against it.
 * </p>
 */
public class TestProteusEndpointConstants {

  @After
  public void clearProperties() {
    System.clearProperty(ProteusEndpointConstants.BASE_URL_PROPERTY);
    System.clearProperty(ProteusEndpointConstants.SOLR_BASE_URL_PROPERTY);
  }

  @Test
  public void asystemPropertyWins() {
    System.setProperty(ProteusEndpointConstants.BASE_URL_PROPERTY,
        "http://localhost:8180");

    assertEquals("http://localhost:8180",
        ProteusEndpointConstants.configuredBaseUrl(
            ProteusEndpointConstants.BASE_URL_PROPERTY,
            ProteusEndpointConstants.BASE_URL_ENV,
            "http://localhost:8080"));
  }

  /** With nothing configured, the address that was compiled in before. */
  @Test
  public void thefallbackIsTheOldCompiledInAddress() {
    assertEquals("http://localhost:8080",
        ProteusEndpointConstants.configuredBaseUrl(
            "drat.test.absent.property", "DRAT_TEST_ABSENT_ENV",
            "http://localhost:8080"));
  }

  /**
   * A trailing slash is dropped, because every caller appends a path that
   * starts with one and "host:8983//solr/drat" is a 404 on Solr 10.
   */
  @Test
  public void atrailingSlashIsRemoved() {
    System.setProperty(ProteusEndpointConstants.SOLR_BASE_URL_PROPERTY,
        "http://localhost:8984/");

    assertEquals("http://localhost:8984",
        ProteusEndpointConstants.configuredBaseUrl(
            ProteusEndpointConstants.SOLR_BASE_URL_PROPERTY,
            ProteusEndpointConstants.SOLR_BASE_URL_ENV,
            "http://localhost:8983"));
  }

  @Test
  public void severalTrailingSlashesAreAllRemoved() {
    System.setProperty(ProteusEndpointConstants.SOLR_BASE_URL_PROPERTY,
        "http://localhost:8984///");

    assertEquals("http://localhost:8984",
        ProteusEndpointConstants.configuredBaseUrl(
            ProteusEndpointConstants.SOLR_BASE_URL_PROPERTY,
            ProteusEndpointConstants.SOLR_BASE_URL_ENV,
            "http://localhost:8983"));
  }

  /** An empty property is not a configured value. */
  @Test
  public void ablankPropertyFallsBack() {
    System.setProperty(ProteusEndpointConstants.BASE_URL_PROPERTY, "   ");

    assertEquals("http://localhost:8080",
        ProteusEndpointConstants.configuredBaseUrl(
            ProteusEndpointConstants.BASE_URL_PROPERTY,
            ProteusEndpointConstants.BASE_URL_ENV,
            "http://localhost:8080"));
  }
}
