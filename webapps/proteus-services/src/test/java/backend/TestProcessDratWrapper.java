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

import java.util.ArrayList;
import java.util.List;
import org.apache.oodt.cas.workflow.structs.WorkflowInstance;
import backend.ProcessDratWrapper;
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
  
  

}
