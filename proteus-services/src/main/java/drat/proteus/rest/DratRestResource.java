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

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.logging.Logger;

import com.google.gson.Gson;
import backend.AbstractDratWrapper;
import backend.AbstractOodtWrapper;
import backend.FileConstants;
import backend.ProcessDratWrapper;
import backend.ProcessOodtWrapper;

@Path("/drat")
@Produces(MediaType.APPLICATION_JSON)
public class DratRestResource {
  private static final Logger LOG = Logger.getLogger(DratRestResource.class.getName());
  private static final long serialVersionUID = -5885535059043262485L;
  public AbstractOodtWrapper oodtWrapper;
  public AbstractDratWrapper dratWrapper;

  public DratRestResource() {
    oodtWrapper = ProcessOodtWrapper.getInstance();
    dratWrapper = ProcessDratWrapper.getInstance();
  }

  @POST
  @Path("/go")
  public void go(DratRequestWrapper body) throws Exception {
    
    dratWrapper.setData(body);
    dratWrapper.setIndexablePath(body.repo);
    dratWrapper.go();
  }

  @POST
  @Path("/index")
  public void index(DratRequestWrapper body) throws Exception {
    dratWrapper.setData(body);
    dratWrapper.setIndexablePath(body.repo);
    dratWrapper.index();
  }

  @POST
  @Path("/crawl")
  public void crawl(DratRequestWrapper body) throws Exception {
    dratWrapper.setData(body);
    dratWrapper.setIndexablePath(body.repo);
    dratWrapper.crawl();
  }

  @POST
  @Path("/map")
  public void map() throws Exception {
    dratWrapper.map();
  }

  @POST
  @Path("/reduce")
  public void reduce() throws Exception {
    dratWrapper.reduce();
  }

  @POST
  @Path("/reset")
  public void reset() throws Exception {
    dratWrapper.reset();
  }

  @POST
  @Path("/fullReset")
  public void fullReset() throws Exception {
    dratWrapper.fullReset();
  }
  
  @GET
  @Path("/currentrepo")
  @Produces(MediaType.TEXT_PLAIN)
  public String currentRepo() throws Exception{
    return dratWrapper.getIndexablePath();
  }

  @GET
  @Path("/log")
  @Produces(MediaType.TEXT_PLAIN)
  public String getProcessLog() {
    File log = new File(FileConstants.DRAT_TEMP_LOG_OUTPUT);
    if (log.exists()) {
      try {
        byte[] encoded = Files.readAllBytes(Paths.get(log.getAbsolutePath()));
        return new String(encoded);
      } catch (IOException ioe) {
        return ioe.getMessage();
      }
    } else {
      return "Log is empty!";
    }
  }
 
}
