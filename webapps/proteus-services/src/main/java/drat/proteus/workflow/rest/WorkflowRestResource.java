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

package drat.proteus.workflow.rest;

import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.core.MediaType;


import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Vector;
import java.util.logging.Logger;

import javax.ws.rs.GET;
import javax.ws.rs.PathParam;

import org.apache.oodt.cas.workflow.structs.WorkflowInstance;


import backend.OodtClientPool;
import org.apache.oodt.cas.metadata.Metadata;

/**
 * This is where all the rest apis related to workflow in drat are declared
 * */
@Path("/workflowservice")
@Produces(MediaType.APPLICATION_JSON)
public class WorkflowRestResource {
    
    
    private static final long serialVersionUID = -5885885059043262485L;
    private static final Logger LOG = Logger.getLogger(WorkflowRestResource.class.getName());
    public WorkflowRestResource() {
    }
    
    /**
     * The workflow instances currently in a given state, with the TaskId from
     * each one's shared context.
     *
     * dratstats.py needs exactly this to decide whether a PGE is still running,
     * and used to get it by calling workflowmgr.getWorkflowInstancesByStatus
     * over XML-RPC. mnemosyne#95 removed XML-RPC, which left the statistics
     * step with no way to ask the question at all, so the statistics core was
     * never populated and every chart on the Proteus dashboard stayed empty.
     */
    @GET
    @Path("/instances/{status}")
    public List<Map<String, Object>> instancesByStatus(@PathParam("status") String status) {
        List<Map<String, Object>> out = new ArrayList<Map<String, Object>>();
        try {
            Vector instances = OodtClientPool.withWorkflowManagerClient(client ->
                client.getWorkflowInstancesByStatus(status));
            if (instances == null) {
                return out;
            }
            for (Object o : instances) {
                WorkflowInstance instance = (WorkflowInstance) o;
                Map<String, Object> entry = new HashMap<String, Object>();
                entry.put("id", instance.getId());
                entry.put("taskIds", instance.getSharedContext() == null
                    ? new ArrayList<String>()
                    : instance.getSharedContext().getAllMetadata("TaskId"));
                out.add(entry);
            }
        } catch (Exception e) {
            LOG.info("Unable to list workflow instances with status [" + status
                + "]: " + e.getMessage());
        }
        return out;
    }

    @POST
    @Path("/dynamic")
    @Produces(MediaType.TEXT_PLAIN)
    public String performDynamicWorkFlow(DynamicWorkflowRequestWrapper requestBody ) {
   
        try {
            Metadata metaData = new Metadata();
            LOG.info(requestBody.taskIds.get(0));
            OodtClientPool.withWorkflowManagerClient(client -> {
                client.executeDynamicWorkflow(requestBody.taskIds,metaData);
                return null;
            });
            return "OK";
        }catch(Exception ex) {
            LOG.info("Workflow Service Error " + ex.getMessage());
            return "Failed to connect to client Url";
        }
    }
    
    
}
