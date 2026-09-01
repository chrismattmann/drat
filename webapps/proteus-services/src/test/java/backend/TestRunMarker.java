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

package backend;

import java.io.File;

import junit.framework.TestCase;

/**
 * The record of what is running, which is what lets one side see a run the
 * other started.
 */
public class TestRunMarker extends TestCase {

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    RunMarker.clear();
  }

  @Override
  protected void tearDown() throws Exception {
    RunMarker.clear();
    super.tearDown();
  }

  /** No file is not a failure: it is how "nothing is running" is spelled. */
  public void testNothingRunningReadsAsNothing() {
    assertNull(RunMarker.read("phase"));
    assertNull(RunMarker.read("repo"));
  }

  public void testAPhaseIsReadableAfterItIsWritten() {
    RunMarker.write("crawl", "cli", "/repos/tika");

    assertEquals("crawl", RunMarker.read("phase"));
    assertEquals("cli", RunMarker.read("startedBy"));
    assertEquals("/repos/tika", RunMarker.read("repo"));
    assertNotNull(RunMarker.read("startedAt"));
  }

  /**
   * A later phase of the same run keeps the repository the earlier one
   * recorded, so the crawl does not have to be repeated to each phase that
   * follows it.
   */
  public void testALaterPhaseKeepsTheRepository() {
    RunMarker.write("crawl", "cli", "/repos/tika");
    RunMarker.write("audit", "cli", null);

    assertEquals("audit", RunMarker.read("phase"));
    assertEquals("/repos/tika", RunMarker.read("repo"));
  }

  public void testClearingLeavesNothingRunning() {
    RunMarker.write("index", "proteus", "/repos/tika");
    RunMarker.clear();

    assertNull(RunMarker.read("phase"));
    assertFalse(new File(FileConstants.CURRENT_RUN_FILE).exists());
  }

  /** A field the marker does not carry is absent, not an error. */
  public void testAnAbsentFieldIsNull() {
    RunMarker.write("audit", "cli", null);
    assertNull(RunMarker.read("repo"));
  }
}
