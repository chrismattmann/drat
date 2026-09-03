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

package backend;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import org.apache.oodt.cas.workflow.structs.WorkflowInstance;
import backend.ProcessDratWrapper;
import backend.RunMarker;
import backend.FileConstants;
import static backend.ProcessDratWrapper.MAPPER_TASK_ID;
import static backend.ProcessDratWrapper.PARTITION_AND_MAP_TASK_ID;
import junit.framework.TestCase;

public class TestProcessDratWrapper extends TestCase {

  public void testParseWorkflows(){
    ProcessDratWrapper wrapper = ProcessDratWrapper.getInstance();
    assertNotNull(wrapper);
    String cmdLines =  "Instance: [id=d3aed64f-6e7c-11e7-af03-cb83c51de744, status=FINISHED, currentTask=urn:drat:MimePartitioner, workflow=Dynamic Workflow-6fc5fc4c-d27a-47f6-905c-2f2e99fa92e9,wallClockTime=0.13265,currentTaskWallClockTime=0.0]\n" + 
                            "Instance: [id=d3aed64f-6e7c-11e7-af03-cb83c51de744, status=PGE EXEC, currentTask=urn:drat:MimePartitioner, workflow=Dynamic Workflow-6fc5fc4c-d27a-47f6-905c-2f2e99fa92e9,wallClockTime=0.13265,currentTaskWallClockTime=0.0]";
    
    List<WorkflowItem> items = null;
    items = wrapper.parseWorkflows(cmdLines);
    assertNotNull(items);
    assertEquals(2, items.size());
    assertTrue(items.get(1).getStatus().equals("PGE EXEC"));
  }
  
  public void testStillRunning(){
    ProcessDratWrapper wrapper = ProcessDratWrapper.getInstance();
    assertNotNull(wrapper);
    String cmdLines =  "Instance: [id=d3aed64f-6e7c-11e7-af03-cb83c51de744, status=FINISHED, currentTask=urn:drat:MimePartitioner, workflow=Dynamic Workflow-6fc5fc4c-d27a-47f6-905c-2f2e99fa92e9,wallClockTime=0.13265,currentTaskWallClockTime=0.0]\n" + 
                            "Instance: [id=d3aed64f-6e7c-11e7-af03-cb83c51de744, status=PGE EXEC, currentTask=urn:drat:MimePartitioner, workflow=Dynamic Workflow-6fc5fc4c-d27a-47f6-905c-2f2e99fa92e9,wallClockTime=0.13265,currentTaskWallClockTime=0.0]\n" + 
                            "Instance: [id=d3aed64f-6e7c-11e7-af03-cb83c51de744, status=PGE EXEC, currentTask=urn:drat:RatCodeAudit, workflow=Dynamic Workflow-6fc5fc4c-d27a-47f6-905c-2f2e99fa92e9,wallClockTime=0.13265,currentTaskWallClockTime=0.0]";
    
    List<WorkflowItem> items = null;
    items = wrapper.parseWorkflows(cmdLines);
    assertNotNull(items);
    List<WorkflowInstance> insts = new ArrayList<WorkflowInstance>(items.size());
    for(WorkflowItem wi: items) {
      insts.add(wi.toInstance());
    }
    assertTrue(wrapper.taskStillRunning(insts, PARTITION_AND_MAP_TASK_ID, MAPPER_TASK_ID)); 
  }

  public void testFilterPartitioners(){
    ProcessDratWrapper wrapper = ProcessDratWrapper.getInstance();
    assertNotNull(wrapper);
    String cmdLines =  "Instance: [id=d3aed64f-6e7c-11e7-af03-cb83c51de744, status=FINISHED, currentTask=urn:drat:MimePartitioner, workflow=Dynamic Workflow-6fc5fc4c-d27a-47f6-905c-2f2e99fa92e9,wallClockTime=0.13265,currentTaskWallClockTime=0.0]\n" + 
                            "Instance: [id=d3aed64f-6e7c-11e7-af03-cb83c51de744, status=PGE EXEC, currentTask=urn:drat:MimePartitioner, workflow=Dynamic Workflow-6fc5fc4c-d27a-47f6-905c-2f2e99fa92e9,wallClockTime=0.13265,currentTaskWallClockTime=0.0]\n" + 
                            "Instance: [id=d3aed64f-6e7c-11e7-af03-cb83c51de744, status=PGE EXEC, currentTask=urn:drat:RatCodeAudit, workflow=Dynamic Workflow-6fc5fc4c-d27a-47f6-905c-2f2e99fa92e9,wallClockTime=0.13265,currentTaskWallClockTime=0.0]";
    
    List<WorkflowItem> items = null;
    items = wrapper.parseWorkflows(cmdLines);
    assertNotNull(items);
    List<WorkflowInstance> insts = new ArrayList<WorkflowInstance>(items.size());
    for(WorkflowItem wi: items) {
      insts.add(wi.toInstance());
    }    
    List<WorkflowInstance> partitioners = null;
    partitioners = wrapper.filterInstances(insts, PARTITION_AND_MAP_TASK_ID);
    assertNotNull(partitioners);
    assertEquals(2, partitioners.size());    
  }
  
  public void testFilterMappers(){
    ProcessDratWrapper wrapper = ProcessDratWrapper.getInstance();
    assertNotNull(wrapper);
    String cmdLines =  "Instance: [id=d3aed64f-6e7c-11e7-af03-cb83c51de744, status=FINISHED, currentTask=urn:drat:MimePartitioner, workflow=Dynamic Workflow-6fc5fc4c-d27a-47f6-905c-2f2e99fa92e9,wallClockTime=0.13265,currentTaskWallClockTime=0.0]\n" + 
                            "Instance: [id=d3aed64f-6e7c-11e7-af03-cb83c51de744, status=PGE EXEC, currentTask=urn:drat:MimePartitioner, workflow=Dynamic Workflow-6fc5fc4c-d27a-47f6-905c-2f2e99fa92e9,wallClockTime=0.13265,currentTaskWallClockTime=0.0]\n" + 
                            "Instance: [id=d3aed64f-6e7c-11e7-af03-cb83c51de744, status=PGE EXEC, currentTask=urn:drat:RatCodeAudit, workflow=Dynamic Workflow-6fc5fc4c-d27a-47f6-905c-2f2e99fa92e9,wallClockTime=0.13265,currentTaskWallClockTime=0.0]";
    
    List<WorkflowItem> items = null;
    items = wrapper.parseWorkflows(cmdLines);
    assertNotNull(items);
    List<WorkflowInstance> insts = new ArrayList<WorkflowInstance>(items.size());
    for(WorkflowItem wi: items) {
      insts.add(wi.toInstance());
    }    
    List<WorkflowInstance> mappers = null;
    mappers = wrapper.filterInstances(insts, MAPPER_TASK_ID);
    assertNotNull(mappers);
    assertEquals(1, mappers.size());    
  }
  
  public void testIsRunning(){
    ProcessDratWrapper wrapper = ProcessDratWrapper.getInstance();
    assertNotNull(wrapper);
    String shouldBeRunning = "PGE EXEC";
    String tricky = "RSUBMIT";
    String queued = "QUEUED";
    String finished = "FINISHED";
    
    assertTrue(wrapper.isRunning(shouldBeRunning));
    assertTrue(wrapper.isRunning(tricky));
    assertTrue(wrapper.isRunning(queued));
    assertFalse(wrapper.isRunning(finished));
    
  }

  /**
   * The engine's own vocabulary decides, when it has one to offer. These are
   * W2's statuses, and not one of them appears in the built-in lists: without
   * asking, every one of them was read as finished.
   */
  public void testTheEngineSaysWhatIsRunning() {
    ProcessDratWrapper wrapper = wrapperReporting(w2Categories());

    assertTrue(wrapper.isRunning("Executing"));
    assertTrue(wrapper.isRunning("PreConditionEval"));
    assertTrue(wrapper.isRunning("WaitingOnResources"));
    assertTrue(wrapper.isRunning("Queued"));
    assertFalse(wrapper.isRunning("Success"));
    assertFalse(wrapper.isRunning("Failure"));
  }

  /**
   * With no lifecycle to read -- which is what W1 reports, its statuses being
   * fixed constants -- the built-in lists answer, and they are right for it.
   */
  public void testTheBuiltInStatusesRemainTheFallback() {
    ProcessDratWrapper wrapper = wrapperReporting(
        new java.util.HashMap<String, String>());

    assertTrue(wrapper.isRunning("PGE EXEC"));
    assertTrue(wrapper.isRunning("QUEUED"));
    assertFalse(wrapper.isRunning("FINISHED"));
  }

  /** A status the engine does not name falls through to the same place. */
  public void testAStatusTheEngineDoesNotNameFallsBack() {
    ProcessDratWrapper wrapper = wrapperReporting(w2Categories());

    assertTrue(wrapper.isRunning("PGE EXEC"));
    assertFalse(wrapper.isRunning("something nobody declared"));
  }

  private java.util.Map<String, String> w2Categories() {
    java.util.Map<String, String> categories =
        new java.util.HashMap<String, String>();
    categories.put("Null", "initial");
    categories.put("Loaded", "initial");
    categories.put("Queued", "waiting");
    categories.put("WaitingOnResources", "waiting");
    categories.put("PreConditionEval", "running");
    categories.put("Executing", "running");
    categories.put("Success", "done");
    categories.put("Failure", "done");
    return categories;
  }

  private ProcessDratWrapper wrapperReporting(
      java.util.Map<String, String> categories) {
    ProcessDratWrapper wrapper = ProcessDratWrapper.getInstance();
    wrapper.setStatusCategories(categories);
    return wrapper;
  }

  @Override
  protected void tearDown() throws Exception {
    // The wrapper is a singleton, so what one test says the engine reports
    // would otherwise be what the next one sees.
    ProcessDratWrapper.getInstance().setStatusCategories(null);
    super.tearDown();
  }
  
  


  /**
   * A run from the UI skips what a run from the command line skips.
   *
   * <p>
   * The crawl set DRAT_EXCLUDE to the empty string, so a UI run audited a
   * repository's .git objects and its build output along with its source. On
   * Mnemosyne that is 15,818 files and 5.1GB rather than 2,400 and 0.1GB, and
   * the licences RAT reports for git blobs and compiled artefacts are noise
   * in the summary.
   * </p>
   */
  public void testARunExcludesBuildOutputAndGitByDefault() {
    ProcessDratWrapper wrapper = ProcessDratWrapper.getInstance();
    wrapper.setExcludes(null);

    assertTrue(wrapper.getExcludes().contains("target"));
    assertTrue(wrapper.getExcludes().contains(".git"));

    String regex = wrapper.excludeRegex();
    assertTrue("a path under target should be excluded",
        "/repo/module/target/classes/A.class".matches(regex));
    assertTrue("a path under .git should be excluded",
        "/repo/.git/objects/ab/cdef".matches(regex));
    assertFalse("source should not be excluded",
        "/repo/src/main/java/A.java".matches(regex));
    assertFalse("a name merely containing the word should not be excluded",
        "/repo/src/targeting/A.java".matches(regex));
  }

  /** A caller can name its own, and can ask for everything. */
  public void testExcludesCanBeNamedOrEmptied() {
    ProcessDratWrapper wrapper = ProcessDratWrapper.getInstance();

    wrapper.setExcludes(java.util.Arrays.asList("node_modules"));
    String regex = wrapper.excludeRegex();
    assertTrue("/repo/node_modules/x/y.js".matches(regex));
    assertFalse("/repo/target/classes/A.class".matches(regex));

    wrapper.setExcludes(new java.util.ArrayList<String>());
    assertEquals("nothing excluded is an empty pattern", "",
        wrapper.excludeRegex());

    wrapper.setExcludes(null);
  }

  /** Writes the marker bin/drat leaves behind when a run ends. */
  private static void writeLastRun(String repo) throws Exception {
    File marker = new File(FileConstants.LAST_RUN_FILE);
    marker.getParentFile().mkdirs();
    java.io.FileWriter out = new java.io.FileWriter(marker);
    try {
      out.write("{\"phase\":\"audit\",\"startedBy\":\"cli\",\"repo\":\""
          + repo + "\",\"outcome\":\"finished\"}");
    } finally {
      out.close();
    }
  }

  /**
   * The repository a run is about, when this process is not the one that
   * started it.
   *
   * <p>
   * getIndexablePath returned a field set only by a run started through
   * Proteus, so a command line run left it empty and /drat/currentrepo
   * answered with nothing while an audit was underway. The UI sizes the
   * repository and draws its charts by that answer.
   * </p>
   */
  public void testTheCurrentRepoIsTheRunningRunsEvenWhenStartedElsewhere() {
    RunMarker.clear();
    ProcessDratWrapper wrapper = ProcessDratWrapper.getInstance();
    wrapper.setIndexablePath("");
    try {
      RunMarker.write("audit", "cli", "/repos/tika");

      assertEquals("a run started from the command line has a repository too",
          "/repos/tika", wrapper.getIndexablePath());
    } finally {
      RunMarker.clear();
      wrapper.setIndexablePath("");
    }
  }

  /**
   * The live run wins. Both can be present at once -- a marker is written
   * when a run starts and this process may still hold the path from the run
   * before it -- and the one happening now is the one being described.
   */
  public void testARunningRunOutranksWhateverThisProcessLastHeld() {
    RunMarker.clear();
    ProcessDratWrapper wrapper = ProcessDratWrapper.getInstance();
    try {
      wrapper.setIndexablePath("/repos/stale");
      RunMarker.write("audit", "cli", "/repos/live");

      assertEquals("/repos/live", wrapper.getIndexablePath());
    } finally {
      RunMarker.clear();
      wrapper.setIndexablePath("");
    }
  }

  /**
   * With nothing running, the run that finished last is what the figures on
   * screen are still describing, so that is what this reports rather than
   * nothing at all.
   */
  public void testWithNoRunTheLastOneIsStillTheSubject() throws Exception {
    RunMarker.clear();
    ProcessDratWrapper wrapper = ProcessDratWrapper.getInstance();
    wrapper.setIndexablePath("");
    File last = new File(FileConstants.LAST_RUN_FILE);
    try {
      writeLastRun("/repos/finished");

      assertEquals("/repos/finished", wrapper.getIndexablePath());
    } finally {
      last.delete();
      wrapper.setIndexablePath("");
    }
  }

  /**
   * What this process is doing still counts when nothing is written down.
   * A run started here reports its repository before the marker exists.
   */
  public void testWithNoMarkerAtAllThisProcessesOwnPathIsUsed() {
    RunMarker.clear();
    ProcessDratWrapper wrapper = ProcessDratWrapper.getInstance();
    try {
      wrapper.setIndexablePath("/repos/started-here");

      assertEquals("/repos/started-here", wrapper.getIndexablePath());
    } finally {
      wrapper.setIndexablePath("");
    }
  }

}
