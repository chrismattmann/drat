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
import java.util.List;
import java.util.ArrayList;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.google.gson.JsonArray;
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
   * Whether a run is recorded at all.
   *
   * <p>
   * Asked separately from reading a field, because the two answers are not
   * the same and treating them as one said a run had finished when it had
   * not. {@link #read} returns null both for a marker that is not there and
   * for one it could not parse; a caller mapping that to "nothing is running"
   * ends a live run on any transient unreadability -- a marker caught
   * mid-rewrite, a slow disk -- and in a UI that watch closes for good.
   * Existence is the durable fact; the phase inside is detail.
   * </p>
   */
  public static synchronized boolean isRecorded() {
    return new File(FileConstants.CURRENT_RUN_FILE).exists();
  }

  /**
   * The whole marker, or null when there is no run recorded.
   *
   * <p>
   * One read for all of it. Asking field by field opened the file once per
   * field, so a single response could be assembled from several different
   * moments and any one of those reads could fail on its own.
   * </p>
   */
  public static synchronized JsonObject readAll() {
    return readFile(new File(FileConstants.CURRENT_RUN_FILE));
  }

  /** The run that finished most recently, or null when there has been none. */
  public static synchronized JsonObject readLast() {
    return readFile(new File(FileConstants.LAST_RUN_FILE));
  }

  /** One marker file, or null when it is not there or will not parse. */
  private static JsonObject readFile(File marker) {
    if (marker == null || !marker.exists()) {
      return null;
    }
    try {
      String body = new String(Files.readAllBytes(Paths.get(marker.getPath())),
          StandardCharsets.UTF_8);
      return JsonParser.parseString(body).getAsJsonObject();
    } catch (Exception e) {
      LOG.log(Level.WARNING, "Unable to read the run marker: " + e.getMessage());
      return null;
    }
  }

  /**
   * The names this run was told not to crawl, empty when it was told nothing.
   *
   * <p>
   * A progress bar counting toward every file in a repository never fills
   * when the run was told to skip some of them, and a finished crawl reads as
   * a stalled one.
   * </p>
   */
  public static synchronized List<String> excludes() {
    List<String> names = new ArrayList<String>();
    JsonObject parsed = readAll();
    if (parsed == null) {
      // No run under way, so the one that finished last is what the figures
      // on screen are still describing.
      parsed = readFile(new File(FileConstants.LAST_RUN_FILE));
    }
    if (parsed == null || !parsed.has("excludes")) {
      return names;
    }
    try {
      JsonArray listed = parsed.get("excludes").getAsJsonArray();
      for (int i = 0; i < listed.size(); i++) {
        String name = listed.get(i).getAsString();
        if (name != null && name.length() > 0) {
          names.add(name);
        }
      }
    } catch (Exception e) {
      LOG.log(Level.WARNING, "Unable to read the run's excludes: "
          + e.getMessage());
    }
    return names;
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
