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

import com.google.gson.Gson;
import drat.proteus.rest.DratRequestWrapper;
import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.PumpStreamHandler;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.time.DurationFormatUtils;
import org.apache.oodt.cas.crawl.MetExtractorProductCrawler;
import org.apache.oodt.cas.workflow.structs.WorkflowInstance;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.HttpJdkSolrClient;
import org.apache.oodt.cas.filemgr.structs.Product;
import org.apache.oodt.cas.filemgr.structs.ProductPage;
import org.apache.oodt.cas.filemgr.structs.ProductType;
import org.apache.oodt.cas.filemgr.tools.SolrIndexer;
import org.apache.oodt.cas.metadata.util.PathUtils;
import org.apache.oodt.pcs.util.FileManagerUtils;

import com.google.common.annotations.VisibleForTesting;

import drat.proteus.workflow.rest.DynamicWorkflowRequestWrapper;
import drat.proteus.workflow.rest.WorkflowRestResource;
import org.springframework.context.support.FileSystemXmlApplicationContext;

import java.io.ByteArrayOutputStream;
import java.util.Properties;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Logger;
import drat.proteus.services.constants.ProteusEndpointConstants;

public class ProcessDratWrapper extends GenericProcess
    implements AbstractDratWrapper {
  private static final Logger LOG = Logger
      .getLogger(ProcessDratWrapper.class.getName());
  private static final String DRAT = FileConstants.DRAT_PATH;
  private static final long DRAT_PROCESS_WAIT_DURATION = 3000;
  private static final int MAX_RESET_TRIES = 10;


  /** What a run skips by default, matching the command line's own default. */
  public static final List<String> DEFAULT_EXCLUDES =
      Collections.unmodifiableList(Arrays.asList("target", ".git"));

  private List<String> excludes = new ArrayList<String>(DEFAULT_EXCLUDES);

  /** What this run should not crawl. Null restores the defaults. */
  public synchronized void setExcludes(List<String> names) {
    this.excludes = names == null
        ? new ArrayList<String>(DEFAULT_EXCLUDES)
        : new ArrayList<String>(names);
  }

  public synchronized List<String> getExcludes() {
    return new ArrayList<String>(this.excludes);
  }

  /**
   * The exclude names as the crawler's precondition wants them.
   *
   * <p>
   * Built to the same shape as the command line's build_exclude_regex, so a
   * run from here skips what a run from there skips: any path with one of
   * these as a whole component.
   * </p>
   */
  String excludeRegex() {
    StringBuilder joined = new StringBuilder();
    for (String name : this.excludes) {
      if (name == null || name.trim().length() == 0) {
        continue;
      }
      if (joined.length() > 0) {
        joined.append('|');
      }
      joined.append(java.util.regex.Pattern.quote(name.trim()));
    }
    if (joined.length() == 0) {
      return "";
    }
    return ".*(^|/)(" + joined + ")(/|$).*";
  }

  private static final String RESET_CMD = "reset";
  private static final String CRAWL_CMD = "crawl";
  private static final String INDEX_CMD = "index";
  private static final String MAP_CMD = "map";
  private static final String REDUCE_CMD = "reduce";
  private static final String STATUS_IDLE = "idle";
  protected static final String PARTITION_AND_MAP_TASK_ID = "urn:drat:MimePartitioner";
  protected static final String MAPPER_TASK_ID = "urn:drat:RatCodeAudit";
  protected static final String REDUCE_TASK_ID = "urn:drat:RatAggregator";
  private static final String[] WIPE_TYPES = { "RatLog", "GenericFile",
      "RatAggregateLog" };

  private String status;

  /** Cached lifecycle stages by status name; see statusCategories(). */
  private Map<String, String> statusCategories;

  private FileManagerUtils fm;
  private String path;
  private DratRequestWrapper body;
  
  private static ProcessDratWrapper singletonDratWrapper = new ProcessDratWrapper();
  private boolean runnning = false;

  public static ProcessDratWrapper getInstance() {
    return singletonDratWrapper;
  }

  private ProcessDratWrapper() {
    super(DRAT);
    this.path = "";
    body = new DratRequestWrapper();
    this.status = "IDLE";
  }

  private synchronized FileManagerUtils getFileManagerUtils() {
    if (this.fm == null) {
      this.fm = new FileManagerUtils(
          PathUtils.replaceEnvVariables("[FILEMGR_URL]"));
    }
    return this.fm;
  }

  public void setIndexablePath(String canonicalPath) {
    this.path = canonicalPath;
  }

  public String getIndexablePath() {
    return this.path;
  }
  
  @Override
  public void setData(DratRequestWrapper body) {
    this.body = body;
  }
  
  
  /**
   * What is running, from where it is written down rather than from memory.
   *
   * <p>
   * This used to return a field, so it described what this process was doing
   * and nothing else. A run started from the command line was invisible here
   * -- not because the two disagreed but because the fact lived in the heap
   * of whichever one you were not looking at. Everything the UI shows about a
   * running audit hangs off this one answer, so reading it from disk is what
   * makes a command line run visible without the UI changing at all.
   * </p>
   */
  public String getStatus() {
    String running = RunMarker.read("phase");
    if (running != null) {
      return running;
    }
    return this.status;
  }

  public synchronized void setStatus(String status) {
    this.status = status;
    if (STATUS_IDLE.equals(status)) {
      RunMarker.clear();
    } else {
      RunMarker.write(status, "proteus", this.path);
    }
  }

  @Override
  public void crawl() throws Exception {
    reset();
    versionControlCheck();
    
    dumpToFile(this.body);
    simpleCrawl();
    
  }
  
  private void simpleCrawl() throws Exception{
    DratLog crawlLog = new DratLog("CRAWLING");
  
  
    try{
      setStatus(CRAWL_CMD);
    
      crawlLog.logInfo("Configuring");
      String beanRepo = System.getProperty("org.apache.oodt.cas.crawl.bean.repo",
              FileConstants.CRAWLER_CONFIG);
      String crawlerId = "MetExtractorProductCrawler";
      // What not to crawl. This used to be set to nothing at all, so a run
      // from the UI audited a repository's .git objects and its build output
      // along with its source: on Mnemosyne that is 15,818 files and 5.1GB
      // rather than 2,400 files and 0.1GB, and the licences RAT reports for
      // compiled artefacts and git blobs are noise in the summary. The
      // command line has excluded these all along; this had no way to say so.
      System.setProperty("DRAT_EXCLUDE", excludeRegex());
      FileSystemXmlApplicationContext appContext = null;
      MetExtractorProductCrawler crawler = null;
      try {
        appContext = new FileSystemXmlApplicationContext("file:" + beanRepo);
        crawler = new MetExtractorProductCrawler();
        crawler.setApplicationContext(appContext);
        crawler.setId(crawlerId);
        crawler.setMetExtractor("org.apache.oodt.cas.metadata.extractors.CopyAndRewriteExtractor");
        crawler.setMetExtractorConfig(FileConstants.MET_EXT_CONFIG_PATH);
        crawler.setFilemgrUrl(FileConstants.FILEMGR_URL);
        crawler.setClientTransferer("org.apache.oodt.cas.filemgr.datatransfer.InPlaceDataTransferFactory");
        crawler.setPreCondIds(Arrays.asList("RegExExcludeComparator"));
        crawler.setProductPath(this.path);
        crawlLog.logInfo("Starting. ",null);
        crawler.crawl();
      } finally {
        if (crawler != null) {
          try {
            crawler.shutdown();
          } catch (IOException e) {
            crawlLog.logWarning("Unable to shutdown crawler cleanly",
                e.getLocalizedMessage());
          }
        }
        if (appContext != null) {
          appContext.close();
        }
      }
      crawlLog.logInfo("Completed.",null);
    }catch (Exception ex) {
      crawlLog.logSevere("ERROR ",ex.getLocalizedMessage());
      ex.printStackTrace();
      throw ex;
    }
  }

  @Override
  public void index() throws IOException, DratWrapperException, InstantiationException, SolrServerException {
      solrIndex();
  }
  
  private synchronized void solrIndex() throws InstantiationException, SolrServerException, IOException {
      setStatus(INDEX_CMD);
      DratLog idl = new DratLog("INDEXING");
      idl.logInfo("Starting", null);
      System.setProperty(FileConstants.SOLR_INDEXER_CONFIG,FileConstants.SOLR_INDEXER_CONFIG_PATH);
      SolrIndexer sIndexer = new SolrIndexer(FileConstants.SOLR_DRAT_URL,FileConstants.FILEMGR_URL);
      sIndexer.indexAll(false);
      sIndexer.commit();
      sIndexer.optimize();
      idl.logInfo("Completed",null);
  }

  @Override
  public synchronized void map() {
    setStatus(MAP_CMD);
    DratLog mapLog = new DratLog("MAPPING");
    WorkflowRestResource restResource = new WorkflowRestResource();
    DynamicWorkflowRequestWrapper requestBody = new DynamicWorkflowRequestWrapper();
    requestBody.taskIds = new ArrayList<>();
    requestBody.taskIds.add(PARTITION_AND_MAP_TASK_ID);
    mapLog.logInfo("STARTING MAPPING");
    mapLog.logInfo("STARTING", " (dynamic workflow with task "+PARTITION_AND_MAP_TASK_ID+")");
    String resp = restResource.performDynamicWorkFlow(requestBody);
    if(resp.equals("OK")) {
        mapLog.logInfo("STARTED SUCCESSFULLY, "+PARTITION_AND_MAP_TASK_ID+" dynamic workflow");
    }else {
        mapLog.logSevere("FAILED", "Dynamic workflow starting failed "+resp);
    }
    mapLog.logInfo("Completed.", null);
    
  }

  @Override
  public synchronized void reduce() throws IOException {
    setStatus(REDUCE_CMD);
    DratLog reduceLog = new DratLog("REDUCING");
    WorkflowRestResource restResource = new WorkflowRestResource();
    DynamicWorkflowRequestWrapper requestBody = new DynamicWorkflowRequestWrapper();
    requestBody.taskIds = new ArrayList<>();
    requestBody.taskIds.add(REDUCE_TASK_ID);
    LOG.info("STARTING REDUCING");
    reduceLog.logInfo("STARTING", " (dynamic workflow with task "+REDUCE_TASK_ID+")");
    String resp = (String)restResource.performDynamicWorkFlow(requestBody);
    if(resp.equals("OK")) {
        reduceLog.logInfo("STARTED SUCCESSFULLY, "+REDUCE_TASK_ID+" dynamic workflow");
    }else {
        reduceLog.logSevere("FAILED", "Dynamic workflow starting failed "+resp);
        throw new IOException(resp);
    }
    reduceLog.logInfo("Completed.", null);
  }

  @Override
  public synchronized void reset() {
    reset(false);
  }

  @Override
  public synchronized void fullReset() {
    reset(true);
  }

  private synchronized void reset(boolean includeStats) {
    DratLog resetLog = new DratLog("RESET");
    resetLog.logInfo("Starting","");
    resetLog.logInfo("DRAT: reset: wiping FM product catalog");
    FileManagerUtils fileManagerUtils = getFileManagerUtils();
    for (String type : WIPE_TYPES) {
      int numTries = 0;
      ProductType pType = fileManagerUtils.safeGetProductTypeByName(type);
      // make sure all products are actually deleted in case there
      // are references issues or XML-RPC issues.
      while (fileManagerUtils.safeGetNumProducts(pType) > 0
          && numTries <= MAX_RESET_TRIES) {
        this.wipeProductType(type);
        numTries++;
      }

      if (numTries == MAX_RESET_TRIES
          && fileManagerUtils.safeGetNumProducts(pType) > 0) {
        resetLog.logWarning("Unable to fully wipe type: [" + type + "]. Tried ["
            + String.valueOf(numTries) + "] times. Max attempts: ["
            + String.valueOf(MAX_RESET_TRIES)
            + "]. Is your File Manager corrupt?");
      }
    }

    resetLog.logInfo("DRAT: reset: wiping WM instance repository.");
    this.wipeInstanceRepo();

    String[] coreNames = includeStats ? new String[] {"drat", "statistics"} : new String[] {"drat"};
    for(String coreName: coreNames){
	       resetLog.logInfo("DRAT: reset: wiping Solr core: [" + coreName + "]");
	       this.wipeSolrCore(coreName);
    }
  
    resetLog.logInfo("DRAT: reset: recursively removed: [" + Utils.getResetDirectories()
        + "]");
    for (String dir : Utils.getResetDirectories()) {
      File file = new File(dir);
      if (file.exists()) {
        try {
          resetLog.logInfo(
              "DRAT: reset: removing dir: [" + file.getAbsolutePath() + "]");
          FileUtils.forceDelete(file);
        } catch (FileNotFoundException e) {
          resetLog.logInfo("Error removing: [" + file.getAbsolutePath()
              + "]: Message: " + e.getLocalizedMessage());
        } catch (IOException e) {
          resetLog.logInfo("Unable to remove file: [" + file.getAbsolutePath()
              + "]: Message: " + e.getLocalizedMessage());
        }
      }
    }
  
    resetLog.logInfo("Completed",null);
  }

  public synchronized void go() throws Exception {
    DratLog goLog = new DratLog("GO");
    goLog.logInfo("Starting", "");
    // before go, always reset
    goLog.logInfo("DRAT: go: resetting.");
    // Said before it starts. Clearing a full catalog is minutes of work in
    // which DRAT is plainly busy, and nothing here marked it: the run showed
    // as idle throughout, with the previous run's charts still on screen,
    // because as far as anything watching could tell nothing was happening.
    setStatus(RESET_CMD);
    reset();
    goLog.logInfo("DRAT: go: Version Control Check");
    versionControlCheck();
    goLog.logInfo("DRAT: go: wrote repo to JSON");
    dumpToFile(this.body);
    
    this.simpleCrawl();
    this.solrIndex();
    this.map();

    goLog.logInfo("DRAT: go: checking still running partition and map and maps.");
    // don't run reduce until all maps are done
    while (stillRunning(PARTITION_AND_MAP_TASK_ID) || stillRunning(MAPPER_TASK_ID)) {
      goLog.logInfo("MAPS STILL RUNNING: Sleeping: "+String.valueOf(DRAT_PROCESS_WAIT_DURATION));
      Thread.sleep(DRAT_PROCESS_WAIT_DURATION);
    }
    
    goLog.logInfo("DRAT: go: Waiting for rat aggregate log to be generated.");
    // you're not done until the final log is generated.
    while (!hasAggregateRatLog()) {
      try {
        if (!stillRunning(REDUCE_TASK_ID)) {
          if (!hasRatLog()) {
            goLog.logInfo("DRAT: go: no RatLog inputs available for reducer yet; sleeping.");
            Thread.sleep(DRAT_PROCESS_WAIT_DURATION);
            continue;
          }
          goLog.logInfo("DRAT: go: no reduces running, and no rat aggregate log, so firing reducer.");
          reduce();
        }
        else {
          goLog.logInfo("DRAT: go: reduce running, and no rat aggregate log, so give it a chance to finish.");
        }
      } catch (IOException e) {
        goLog.logWarning("DRAT: go: Fired reduce off before mappers were done. Sleeping: ["
            + String.valueOf(DRAT_PROCESS_WAIT_DURATION / 1000)
            + "] seconds and will try again.");
      }
      Thread.sleep(DRAT_PROCESS_WAIT_DURATION);
    }

    goLog.logInfo("Completed.", null);
    setStatus(STATUS_IDLE);
  }


  private synchronized boolean hasAggregateRatLog() {
    int numLogs = -1;
    FileManagerUtils fileManagerUtils = getFileManagerUtils();
    ProductType type = fileManagerUtils.safeGetProductTypeByName("RatAggregateLog");
    numLogs = fileManagerUtils.safeGetNumProducts(type);
    String breakStatus = (numLogs > 0) ? "breaking" : "looping";
    LOG.info("Checking for RatAggregateLog: num: [" + String.valueOf(numLogs)
        + "]: " + breakStatus);
    return numLogs > 0;
  }

  private synchronized boolean hasRatLog() {
    int numLogs = -1;
    FileManagerUtils fileManagerUtils = getFileManagerUtils();
    ProductType type = fileManagerUtils.safeGetProductTypeByName("RatLog");
    numLogs = fileManagerUtils.safeGetNumProducts(type);
    String breakStatus = (numLogs > 0) ? "ready" : "waiting";
    LOG.info("Checking for RatLog: num: [" + String.valueOf(numLogs)
        + "]: " + breakStatus);
    return numLogs > 0;
  }
  
  private boolean stillRunning(String taskId) throws Exception {
        DratLog workflowRunLog = new DratLog("CHECKING FOR RUNNING MAPPERS/PARTITIONERS");
        workflowRunLog.logInfo("Starting.", "");
        FutureTask<List<WorkflowInstance>> timeoutWorkflowInst = 
            new FutureTask<List<WorkflowInstance>>(
                new Callable<List<WorkflowInstance>>() {
                  @Override
                  public List<WorkflowInstance> call() throws Exception {
                    return OodtClientPool.withWorkflowManagerClient(
                        client -> client.getWorkflowInstances());
                  }
                }
        );
        
        List<WorkflowInstance> workflowInstances = null;
        Thread instCheckThread = null;
        try {
          instCheckThread = new Thread(timeoutWorkflowInst);
          instCheckThread.start();
          workflowInstances = timeoutWorkflowInst.get(3L, TimeUnit.SECONDS);
        }
        catch(InterruptedException e) {
          workflowRunLog.logInfo("Drat::Checking Workflows:: Interrupted exception: "+e.getLocalizedMessage());
          workflowInstances = Collections.EMPTY_LIST;              
        }
        catch(ExecutionException e) {
          workflowRunLog.logInfo("Drat::Checking Workflows:: Execution exception: "+e.getLocalizedMessage());
          workflowInstances = Collections.EMPTY_LIST;              
        }
        catch(TimeoutException e) {
          workflowRunLog.logInfo("Drat::Checking Workflows:: Timeout exception: "+e.getLocalizedMessage());          
          timeoutWorkflowInst.cancel(true);
          workflowInstances = Collections.EMPTY_LIST;
        }
        finally {
          if (instCheckThread != null) {
            try {
              instCheckThread.join(DRAT_PROCESS_WAIT_DURATION);
            }
            catch(InterruptedException ignore) {
              Thread.currentThread().interrupt();
            }
          }
        }
       
        for(WorkflowInstance instance : workflowInstances){
          LOG.info("Running Instances : id: "+instance.getId()
                  +" state name "+instance.getState().getName()+" current task name : "+instance.getCurrentTask().getTaskName());
        }
        
        workflowRunLog.logInfo("Completed.", null);
        return taskStillRunning(workflowInstances, taskId);            

  }

  @VisibleForTesting
  protected List<WorkflowItem> parseWorkflows(String cmdOutput) {
    List<WorkflowItem> items = new ArrayList<WorkflowItem>();

    String lines[] = cmdOutput.split("\\r?\\n");
    if (lines != null && lines.length > 0) {
      int lineNo = 1;
      for (String line : lines) {
        if (line == null || (line != null && line.trim().equals(""))) {
          LOG.info("Blank line in evaluating workflow instance response: ["
              + line + "]: skipping lineNo: [" + String.valueOf(lineNo) + "]");
          continue;
        }

        if (!line.startsWith("Instance:")) {
          LOG.info("Skipping line: does not begin with Instance: [" + line
              + "]: lineNo: [" + String.valueOf(lineNo) + "]");
          continue;
        }

        String[] tmpLine = line.split("\\[");
        if (tmpLine != null && tmpLine.length > 0) {
          String instInfo = tmpLine[1].trim();
          instInfo = instInfo.substring(0, instInfo.length() - 1); // chop ']'
          String[] instToks = instInfo.split(",");
          if (instToks != null && instToks.length > 0) {
            WorkflowItem item = new WorkflowItem();
            item.setId(cleanAndSplit(instToks[0]));
            item.setStatus(cleanAndSplit(instToks[1]));
            item.setCurrentTask(cleanAndSplit(instToks[2]));
            item.setWorkflowName(cleanAndSplit(instToks[3]));
            item.setWallClockTime(cleanAndSplit(instToks[4]));
            item.setCurrentTaskWallClock(cleanAndSplit(instToks[5]));
            items.add(item);
          }
        }

        lineNo++;
      }
    }
    return items;
  }
  
  protected boolean taskStillRunning(List<WorkflowInstance> instances, String ...taskIds) {
    if (taskIds != null && taskIds.length > 0) {
      for(String taskId: taskIds) {
        List<WorkflowInstance> insts = filterInstances(instances, taskId);
        LOG.info("Checking task: "+taskId+" : inspecting ["+String.valueOf(instances.size())+"] tasks.");
        for(WorkflowInstance i: insts) {
         if(isRunning(i.getState().getName())) {
           LOG.info("Task: [" + i.getId() + "] still running.");     
           return true;
         }
        }
      }
    }
    
    return false;
  }
  
  @VisibleForTesting 
  protected List<WorkflowInstance> filterInstances(List<WorkflowInstance> instances, String taskId){
    List<WorkflowInstance> insts = new ArrayList<>();
    if(instances!=null && instances.size()>0){
        for(WorkflowInstance instance:instances){
            if(instance.getCurrentTask().getTaskId().equals(taskId)){
                LOG.info("Adding "+taskId+" instance: [" + instance.getCurrentTask().getTaskId() + "]");
                insts.add(instance);
            }else{
                LOG.info("Filtering task: [" + instance.getCurrentTask().getTaskId() + "]");
            }
        }
    }
    return insts;
  }  
  

  /**
   * Whether a status means the work is still going.
   *
   * <p>
   * Asked of the workflow manager, which reads it from the lifecycle its
   * engine was configured with. The lists below are W1's vocabulary, and
   * they were the only answer here: none of those names appear in W2's, so
   * every running instance of that engine fell through to "assuming
   * finished" and this reported a map that was still fanning out as done.
   * The reduce that followed ran against a fraction of it.
   * </p>
   *
   * <p>
   * They remain as the fallback, because they are right for the engine they
   * were written for. An engine with no lifecycle to read reports nothing,
   * and W1's statuses are fixed constants rather than declared ones.
   * </p>
   */
  @VisibleForTesting
  protected boolean isRunning(String status) {
    String category = statusCategories().get(status);
    if (category != null) {
      // "done" is the stage a lifecycle puts its terminal states in.
      return !"done".equals(category);
    }

    List<String> runningStates = Arrays.asList("CREATED", "QUEUED", "STARTED",
        "RSUBMIT", "PGE EXEC", "STAGING INPUT", "CRAWLING");
    List<String> finishedStates = Arrays.asList("PAUSED", "METMISS",
        "FINISHED");
    if (finishedStates.contains(status)) {
      return false;
    } else {
      if (runningStates.contains(status)) {
        return true;
      } else {
        // it's not in running or finished, so we'll assume
        // it's not running (dead)
        LOG.info("Unknown status: [" + status + "]: assuming finished.");
        return false;
      }
    }
  }

  /**
   * The stage each status belongs to, as this deployment's engine declares
   * it. Read once: a lifecycle does not change while the manager runs, and
   * this is asked for every instance on every poll.
   */
  /**
   * For tests, which need to say what the engine reports without one running.
   * Null puts it back to reading the manager.
   */
  @VisibleForTesting
  void setStatusCategories(Map<String, String> categories) {
    this.statusCategories = categories;
  }

  /**
   * Read the stages from the services this deployment is already running.
   *
   * <p>
   * Over HTTP rather than through the workflow client. DRAT builds against a
   * released Mnemosyne, so a method added to that client cannot be called
   * here until it ships; an endpoint that grows a field is readable as soon
   * as the deployment runs a version that sends it, and simply absent before
   * then. Absent means falling back, which is what a deployment on an older
   * Mnemosyne should do.
   * </p>
   */
  private Map<String, String> readStatusCategories() {
    Map<String, String> read = new HashMap<String, String>();
    HttpURLConnection connection = null;
    try {
      URL url = new URL(ProteusEndpointConstants.BASE_URL
          + ProteusEndpointConstants.Services.WORKFLOW_STATUSES);
      connection = (HttpURLConnection) url.openConnection();
      connection.setConnectTimeout(2000);
      connection.setReadTimeout(5000);
      if (connection.getResponseCode() >= 400) {
        return read;
      }
      InputStream stream = connection.getInputStream();
      try {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] bytes = new byte[4096];
        int count;
        while ((count = stream.read(bytes)) != -1) {
          buffer.write(bytes, 0, count);
        }
        JsonObject body = JsonParser.parseString(
            new String(buffer.toByteArray(), StandardCharsets.UTF_8))
            .getAsJsonObject();
        if (body.has("categories") && body.get("categories").isJsonObject()) {
          JsonObject categories = body.getAsJsonObject("categories");
          for (String status : categories.keySet()) {
            read.put(status, categories.get(status).getAsString());
          }
        }
      } finally {
        stream.close();
      }
    } catch (Exception e) {
      LOG.info("Unable to read status categories: " + e.getMessage()
          + ": falling back on the built-in statuses");
    } finally {
      if (connection != null) {
        connection.disconnect();
      }
    }
    return read;
  }

  @VisibleForTesting
  protected Map<String, String> statusCategories() {
    if (statusCategories == null) {
      statusCategories = readStatusCategories();
    }
    return statusCategories;
  }

  private String cleanAndSplit(String s) {
    String[] sToks = s.split("=");
    if (sToks != null && sToks.length == 2) {
      return sToks[1].trim();
    } else
      return "";
  }

  private synchronized String execToString(String command) throws Exception {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    CommandLine commandline = CommandLine.parse(command);
    DefaultExecutor exec = new DefaultExecutor();
    PumpStreamHandler streamHandler = new PumpStreamHandler(outputStream);
    int status = -1;
    exec.setStreamHandler(streamHandler);
    status = exec.execute(commandline);
    String output = outputStream.toString(Charset.defaultCharset().name());
    if (status != 0) {
      throw new IOException("Non-zero status message from executing: ["
          + command + "]: output: [" + output + "]");
    }
    return output;
  }

  private synchronized void wipeProductType(String productTypeName) {
    FileManagerUtils fileManagerUtils = getFileManagerUtils();
    ProductType type = fileManagerUtils.safeGetProductTypeByName(productTypeName);
    if (type == null) {
      LOG.warning("Unable to get product type definition for: ["
          + productTypeName + "]: FM wipe fails.");
      return;
    }
    LOG.info("Paging through products for product type: " + productTypeName);
    ProductPage page = fileManagerUtils.safeFirstPage(type);

    while (page != null) {
      LOG.info("Cleaning File Manager: Product Type: [" + productTypeName
          + "]: wiping [" + String.valueOf(page.getTotalPages())
          + "] pages of products: pageSize: [" + page.getPageSize() + "].");
      for (Product product : page.getPageProducts()) {
        try {
          LOG.info("Removing File Manager product catalog entry: ["
              + product.getProductId() + "] name: [" + product.getProductName()
              + "] type: [" + productTypeName + "]");
          fileManagerUtils.getFmgrClient().removeProduct(product);
        } catch (Exception e) {
          LOG.warning("Unable to remove File Manager product: ["
              + product.getProductId() + "] name: [" + product.getProductName()
              + "] type: [" + productTypeName + "]: Message: "
              + e.getLocalizedMessage());
        }
      }

      if (page.isLastPage()) {
        break;
      }
      try {
        page = fileManagerUtils.getFmgrClient().getNextPage(type, page);
      } catch (Exception e) {
        e.printStackTrace();
        LOG.warning("Unable to obtain next page. Message: "
            + e.getLocalizedMessage() + " breaking loop.");
        break;
      }
    }

  }

  
  private void versionControlCheck() throws Exception {
    if (path.startsWith("http://") || path.startsWith("https://")) {
      this.body.loc_url = this.path;
      String projectName = getRemoteProjectName(path);
      boolean git = isGitRemoteUrl(path);
      File tmpDir = new File(FileConstants.DRAT_CLONES);
      String tmpDirPath = tmpDir.getCanonicalPath();
      String clonePath = tmpDirPath + File.separator + projectName;
      File cloneDir = new File(clonePath);
      if (cloneDir.isDirectory() && cloneDir.exists()) {
        LOG.info(
                "Git / SVN clone: [" + clonePath + "] already exists, removing it.");
        org.apache.cxf.helpers.FileUtils.removeDir(cloneDir);
      }
      LOG.info("Cloning Git / SVN project: [" + projectName + "] remote repo: ["
              + path + "] into " + tmpDirPath);
      
      CommandLine cmdLine;
      if (git) {
        cmdLine = new CommandLine("git");
        cmdLine.addArgument("clone");
        cmdLine.addArgument("--depth");
        cmdLine.addArgument("1");
        cmdLine.addArgument(normalizeGitRemoteUrl(path));
      } else {
        cmdLine = new CommandLine("svn");
        cmdLine.addArgument("export");
        cmdLine.addArgument(path);
      }
      executeRemoteCheckout(cmdLine, tmpDir);
      
      if (git) {
        String gitHiddenDirPath = clonePath + File.separator + ".git";
        File gitHiddenDir = new File(gitHiddenDirPath);
        LOG.info("Removing .git directory from " + gitHiddenDirPath);
        org.apache.cxf.helpers.FileUtils.removeDir(gitHiddenDir);
      }
      
      this.path = clonePath;
    }else{
      this.body.loc_url = "http://drat.apache.org/#";
    }
      
      this.body.id = "id:"+this.path;
      this.body.repo = this.path;
  }

  private String getRemoteProjectName(String remotePath) throws Exception {
    String cleanedPath = remotePath;
    int queryIndex = cleanedPath.indexOf('?');
    if (queryIndex > -1) {
      cleanedPath = cleanedPath.substring(0, queryIndex);
    }
    while (cleanedPath.endsWith("/")) {
      cleanedPath = cleanedPath.substring(0, cleanedPath.length() - 1);
    }

    String projectName = cleanedPath.substring(cleanedPath.lastIndexOf("/") + 1);
    if (projectName.endsWith(".git")) {
      projectName = projectName.substring(0, projectName.length() - ".git".length());
    }
    if (projectName.trim().isEmpty()) {
      throw new IOException("Unable to infer project name from remote URL: " + remotePath);
    }
    return projectName;
  }

  private boolean isGitRemoteUrl(String remotePath) {
    if (remotePath.endsWith(".git")) {
      return true;
    }

    try {
      String host = new URI(remotePath).getHost();
      if (host == null) {
        return false;
      }
      host = host.toLowerCase();
      return host.equals("github.com")
          || host.equals("gitlab.com")
          || host.equals("bitbucket.org")
          || host.equals("gitbox.apache.org");
    } catch (Exception e) {
      LOG.warning("Unable to parse remote URL host: [" + remotePath + "]: "
          + e.getLocalizedMessage());
      return false;
    }
  }

  private String normalizeGitRemoteUrl(String remotePath) {
    if (remotePath.startsWith("http://github.com/")) {
      return "https://" + remotePath.substring("http://".length());
    }
    return remotePath;
  }

  private void executeRemoteCheckout(CommandLine cmdLine, File workingDirectory)
      throws IOException {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    DefaultExecutor executor = new DefaultExecutor();
    executor.setWorkingDirectory(workingDirectory);
    executor.setStreamHandler(new PumpStreamHandler(outputStream));
    try {
      executor.execute(cmdLine);
    } catch (IOException e) {
      String output = outputStream.toString(Charset.defaultCharset().name());
      LOG.warning("Remote checkout failed. Command: [" + cmdLine + "] output: ["
          + output + "]");
      throw new IOException("Remote checkout failed for command: [" + cmdLine
          + "]. Output: [" + output + "]", e);
    }
  }

  /**
   * Clear the workflow instances, by asking the manager that owns them.
   *
   * <p>
   * This deleted a directory called data/workflow whatever the deployment was
   * configured with. That is the Lucene repository's default path, and a
   * deployment holding its instances in a database has no such directory --
   * so the delete found nothing, reported nothing, and every instance from
   * every previous run stayed exactly where it was while the reset reported
   * success.
   * </p>
   *
   * <p>
   * The manager owns whichever repository was configured, so asking it works
   * for all of them and needs no knowledge of where the instances live. It
   * refuses while anything is executing, which is the right answer here too.
   * </p>
   */
  private synchronized void wipeInstanceRepo() {
    try {
      OodtClientPool.withWorkflowManagerClient(client -> {
        client.clearWorkflowInstances(true);
        return null;
      });
      LOG.info("DRAT: reset: cleared the workflow instances");
    } catch (Exception e) {
      // Said plainly rather than passed over: a reset that reports success
      // while every instance from every previous run is still there is worse
      // than one that admits it could not.
      LOG.warning("DRAT: reset: workflow instances were NOT cleared: "
          + reasonFor(e) + ". Instances from previous runs are still "
          + "present.");
    }
  }

  /**
   * Why the manager refused, in words.
   *
   * <p>
   * The error Avro carries back has no message of its own -- its reason lives
   * in the detail field -- so reporting getMessage() logged "NOT cleared:
   * null", which says nothing about whether a run was in progress, the
   * manager was unreachable, or something else.
   * </p>
   */
  private String reasonFor(Exception e) {
    try {
      java.lang.reflect.Method detail = e.getClass().getMethod("getDetail");
      Object value = detail.invoke(e);
      if (value != null) {
        return String.valueOf(value);
      }
    } catch (Exception noDetail) {
      // Not one of the Avro errors; the message below will do.
    }
    return e.getMessage() != null ? e.getMessage() : e.toString();
  }

  /** The workflow manager's configuration, or empty when it cannot be read. */
  private Properties workflowProperties() {
    Properties props = new Properties();
    File file = new File(PathUtils.replaceEnvVariables("[DRAT_HOME]"),
        "workflow/etc/workflow.properties");
    if (!file.exists()) {
      return props;
    }
    try {
      java.io.InputStream in = new java.io.FileInputStream(file);
      try {
        props.load(in);
      } finally {
        in.close();
      }
    } catch (Exception e) {
      LOG.warning("Unable to read " + file + ": " + e.getMessage());
    }
    return props;
  }

  private synchronized void wipeSolrCore(String coreName) {
    // Was a literal http://localhost:8983/solr. This method issues
    // deleteByQuery("*:*"), so pointing it at a compiled-in address means
    // that on a host where 8983 belongs to another OODT deployment, a DRAT
    // reset empties a core that is not DRAT's. Take the address the
    // deployment configured.
    String baseUrl = ProteusEndpointConstants.SOLR_BASE_URL + "/solr";
    String finalUrl = baseUrl + "/" + coreName;
    SolrClient server = null;
    try {
      // HttpSolrServer is SolrJ 4 and is gone in SolrJ 10; HttpJdkSolrClient
      // is what cas-filemgr's SolrIndexer uses against the same Solr.
      server = new HttpJdkSolrClient.Builder(finalUrl).build();
      server.deleteByQuery("*:*");
      server.commit();
    } catch (Exception e) {
      e.printStackTrace();
      LOG.warning("Error wiping Solr core: [" + coreName + "]: Message: "
          + e.getLocalizedMessage());
    } finally {
      if (server != null) {
        try {
          server.close();
        } catch (IOException e) {
          LOG.warning("Error closing Solr client for core: [" + coreName
              + "]: Message: " + e.getLocalizedMessage());
        }
      }
    }
  }
  
  
  public void dumpToFile(DratRequestWrapper body) throws IOException {
    File repo = new File(FileConstants.CURRENT_REPO_DETAILS_FILE);
    Files.write(repo.toPath(),new Gson().toJson(body).getBytes());
  }
  
  private class DratLog{
      private static final String MODULE = "DRAT_LOG";
      long startTime =0;
      private long lastActionTime=-1L;
      private long timeDiff  =-1L;
      private ZonedDateTime zdt;
      private String action;
      public DratLog(String action) {
          this.action = action;
          
      }
      
      private void logWarning(String status,String desc) {
          LOG.warning(getMsg(status,desc));
      }
      
      private void logWarning(String desc) {
          LOG.warning(MODULE+" : "+desc);
      }
      
      private void logInfo(String status,String desc) {
          LOG.info(getMsg(status,desc));
      }
      
      private void logInfo(String desc) {
          LOG.info(MODULE+" : "+desc);
      }
      
      private void logSevere(String status,String desc) {
          LOG.fine(getMsg(status,desc));
      }
      
      private String getMsg(String status,String desc) {
          String basic = "";
          if(startTime==0) {
              startTime = System.currentTimeMillis();
              zdt = ZonedDateTime.ofInstant(Instant.ofEpochMilli(startTime), ZoneId.systemDefault());
              basic = String.format("%1$s : %2$s : %3$s, at time %4$s", MODULE,action,status,
                      zdt.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
          }else {
              lastActionTime = System.currentTimeMillis();
              timeDiff = lastActionTime - startTime;
              zdt = ZonedDateTime.ofInstant(Instant.ofEpochMilli(startTime), ZoneId.systemDefault());
              basic =  String.format("%1$s : %2$s : %3$s, at time %4$s with duration %5$s", MODULE,action,status,
                      zdt.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),DurationFormatUtils.formatDuration(timeDiff,"MM-dd T HH-mm-ss"));
          }
          
          if(desc==null) {
              return basic;
          }else {
              return String.format("%1$s : %2$s", basic,desc);
          }
      }
      
      
  }

}
