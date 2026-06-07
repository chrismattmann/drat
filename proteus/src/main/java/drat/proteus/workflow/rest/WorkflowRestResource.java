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


import java.util.logging.Logger;


import backend.OodtClientPool;
import org.apache.oodt.cas.metadata.Metadata;
import org.wicketstuff.rest.annotations.MethodMapping;
import org.wicketstuff.rest.annotations.parameters.RequestBody;
import org.wicketstuff.rest.contenthandling.json.webserialdeserial.GsonWebSerialDeserial;
import org.wicketstuff.rest.resource.AbstractRestResource;
import org.wicketstuff.rest.utils.http.HttpMethod;

/**
 * This is where all the rest apis related to workflow in drat are declared
 * */
public class WorkflowRestResource extends AbstractRestResource<GsonWebSerialDeserial> {
    
    
    private static final long serialVersionUID = -5885885059043262485L;
    private static final Logger LOG = Logger.getLogger(WorkflowRestResource.class.getName());
    public WorkflowRestResource() {
        super(new GsonWebSerialDeserial());
    }
    
    @MethodMapping(value = "/dynamic", httpMethod = HttpMethod.POST)
    public String performDynamicWorkFlow(@RequestBody DynamicWorkflowRequestWrapper requestBody ) {
   
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
