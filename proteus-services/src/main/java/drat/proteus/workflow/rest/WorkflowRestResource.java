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


import java.util.logging.Logger;


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
