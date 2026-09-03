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
import backend.RunMarker;
import com.google.gson.JsonObject;
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
    // What not to crawl. Absent means the defaults, so a caller that says
    // nothing gets the same exclusions the command line has always used
    // rather than an audit of the repository's git objects and build output.
    dratWrapper.setExcludes(body.excludes);
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
  
  /**
   * The run that is happening, whoever started it.
   *
   * <p>
   * Answers the question Proteus could not previously ask: is there a DRAT
   * run in progress, and what is it doing? The phase comes from the marker
   * that both this and the command line write, and the repository from the
   * file both already wrote. Nothing here depends on Proteus having been the
   * one to start it.
   * </p>
   */
  @GET
  @Path("/run")
  @Produces(MediaType.APPLICATION_JSON)
  public String currentRun() {
    JsonObject run = new JsonObject();

    // Whether the marker is there decides this, not whether its contents
    // could be parsed. Those used to be the same answer: every field was read
    // separately and a null phase meant "not running", so a marker caught
    // between being written and being complete reported a live run as
    // finished -- and a watching UI, told once that a run had ended, closed
    // the watch and said so.
    if (!RunMarker.isRecorded()) {
      run.addProperty("running", false);
      // How the last one ended, so a page that was watching it can say
      // whether it finished or stopped part way rather than calling both
      // "completed".
      JsonObject last = RunMarker.readLast();
      if (last != null && last.has("outcome")) {
        try {
          run.addProperty("lastOutcome", last.get("outcome").getAsString());
        } catch (Exception ignored) {
          // Not worth failing the answer over.
        }
      }
      return run.toString();
    }

    run.addProperty("running", true);
    JsonObject marker = RunMarker.readAll();
    if (marker != null) {
      copy(marker, run, "phase");
      copy(marker, run, "startedBy");
      copy(marker, run, "startedAt");
      copy(marker, run, "repo");
      // Carried through so a reader can ask for figures about the same set of
      // files this run is working on, and go on asking for them once the run
      // has ended and its marker is gone.
      if (marker.has("excludes") && marker.get("excludes").isJsonArray()) {
        run.add("excludes", marker.get("excludes"));
      }
    }
    return run.toString();
  }

  /** Carries a field across when the marker has one to give. */
  private static void copy(JsonObject from, JsonObject to, String field) {
    try {
      if (from.has(field) && !from.get(field).isJsonNull()) {
        to.addProperty(field, from.get(field).getAsString());
      }
    } catch (Exception ignored) {
      // A field that will not read as a string is not worth failing over;
      // the run itself is still reported.
    }
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
