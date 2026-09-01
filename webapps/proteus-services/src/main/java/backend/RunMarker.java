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
import java.io.FileWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * What is running, written down where anything can read it.
 *
 * <p>
 * A DRAT run has always been two different facts held in two places. The
 * engine knows about the part it schedules, which is everything from the
 * partitioner onwards. Nothing knows about the crawl and the index, because
 * those are not workflows -- they are executed directly, by whichever of the
 * command line or Proteus was asked to start them, and the only record that
 * either was happening was a field in the memory of the process doing it.
 * </p>
 *
 * <p>
 * That is why a run started from the command line was invisible in Proteus:
 * not because the two disagreed, but because the fact lived in the heap of
 * whichever one you were not looking at. This is the same fact on disk, in
 * the directory both already write the current repository to, so either can
 * see a run the other started.
 * </p>
 *
 * <p>
 * It is deliberately a stopgap. When the crawl and the index become workflow
 * tasks the engine will know about the whole run, one instance will describe
 * it, and this file will have nothing left to say.
 * </p>
 */
public class RunMarker {

  private static final Logger LOG = Logger.getLogger(RunMarker.class.getName());

  private RunMarker() {
  }

  /**
   * Record that a phase has begun.
   *
   * @param phase   what is running now: crawl, index, or audit
   * @param startedBy which side started it, for the benefit of a reader who
   *                  wants to know whether this is their own run
   * @param repo    the repository being audited, or null to leave whatever is
   *                already recorded
   */
  public static synchronized void write(String phase, String startedBy,
      String repo) {
    JsonObject marker = new JsonObject();
    marker.addProperty("phase", phase);
    marker.addProperty("startedBy", startedBy);
    marker.addProperty("startedAt", String.valueOf(System.currentTimeMillis()));
    if (repo != null) {
      marker.addProperty("repo", repo);
    } else {
      String previous = read("repo");
      if (previous != null) {
        marker.addProperty("repo", previous);
      }
    }
    FileWriter writer = null;
    try {
      // A deployment that has never run has no data directory yet, and a
      // write that fails here fails quietly into a log, leaving the run
      // invisible for the reason this file exists to prevent.
      File target = new File(FileConstants.CURRENT_RUN_FILE);
      File parent = target.getParentFile();
      if (parent != null && !parent.exists() && !parent.mkdirs()) {
        LOG.log(Level.WARNING, "Unable to create " + parent
            + "; the running phase will not be recorded");
        return;
      }
      writer = new FileWriter(target);
      writer.write(marker.toString());
    } catch (Exception e) {
      LOG.log(Level.WARNING, "Unable to record the running phase: "
          + e.getMessage());
    } finally {
      if (writer != null) {
        try {
          writer.close();
        } catch (Exception ignored) {
        }
      }
    }
  }

  /** Record that nothing is running. */
  public static synchronized void clear() {
    File marker = new File(FileConstants.CURRENT_RUN_FILE);
    if (marker.exists() && !marker.delete()) {
      LOG.log(Level.WARNING, "Unable to clear the run marker at " + marker);
    }
  }

  /**
   * One field of the marker, or null when there is no run recorded. A missing
   * file is the ordinary case, not a failure: it means nothing is running.
   */
  public static synchronized String read(String field) {
    File marker = new File(FileConstants.CURRENT_RUN_FILE);
    if (!marker.exists()) {
      return null;
    }
    try {
      String body = new String(Files.readAllBytes(Paths.get(marker.getPath())),
          StandardCharsets.UTF_8);
      JsonObject parsed = JsonParser.parseString(body).getAsJsonObject();
      return parsed.has(field) ? parsed.get(field).getAsString() : null;
    } catch (Exception e) {
      LOG.log(Level.WARNING, "Unable to read the run marker: " + e.getMessage());
      return null;
    }
  }
}
