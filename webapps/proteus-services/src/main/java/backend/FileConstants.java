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

import org.apache.oodt.cas.metadata.util.PathUtils;

/**
 * Created by stevenfrancus on 10/13/15.
 */
public class FileConstants {
  public static final String DRAT_SUPER_DIR = getDratDirectory();
  public static final String OODT_PATH = buildDratSubdirectoryPath("/bin/oodt");
  public static final String WORKFLOW_PATH = buildDratSubdirectoryPath("/workflow/bin/wmgr-client");
  public static final String DRAT_PATH = buildDratSubdirectoryPath("/bin/drat");
  public static final String DRAT_CLONES = buildDratSubdirectoryPath("/data/clones");
  public static final String DRAT_TEMP_UNZIPPED_PATH = buildDratSubdirectoryPath("/data/staging");
  public static final String CURRENT_REPO_DETAILS_FILE = buildDratSubdirectoryPath("/data/repo");
  public static final String DRAT_TEMP_LOG_OUTPUT = buildDratSubdirectoryPath("/data/drat_output.log");
  public static final String SOLR_INDEXER_CONFIG_PATH = buildDratSubdirectoryPath("/filemgr/etc/indexer.properties");

  public static final String FILEMGR_URL=PathUtils.replaceEnvVariables("[FILEMGR_URL]");
  public static final String SOLR_DRAT_URL=PathUtils.replaceEnvVariables("[SOLR_DRAT_URL]");
  public static final String CLIENT_URL=PathUtils.replaceEnvVariables("[WORKFLOW_URL]");
  public static final String OPSUI_URL=PathUtils.replaceEnvVariables("[OPSUI_URL]");
  
  public static final String MET_EXT_CONFIG_PATH =buildDratSubdirectoryPath("/extractors/code/default.cpr.conf");
  public static final String CRAWLER_CONFIG = buildDratSubdirectoryPath("/crawler/policy/crawler-config.xml");
  public static final String SOLR_INDEXER_CONFIG = "SOLR_INDEXER_CONFIG";
  
  private static String getDratDirectory() {
    // This used to chop DRAT_HOME at the last occurrence of the literal
    // "drat" and every constant below re-appended "/deploy" -- a round trip
    // that only cancels out when DRAT_HOME ends in exactly "drat/deploy".
    // Any other layout silently produced paths that do not exist: a
    // DRAT_HOME of ".../drat-deploy" became ".../drat" + "/deploy", so go()
    // reset the right directories and then failed writing data/repo with
    // NoSuchFileException. A path such as /home/drattest/deploy was mangled
    // the same way. DRAT_HOME is already the deploy directory -- bin/drat
    // uses it as $DRAT_HOME/bin -- so use it as given.
    return PathUtils.replaceEnvVariables("[DRAT_HOME]");
  }

  public static String buildDratSubdirectoryPath(String additionalPath) {
    return dratSubdirectory(DRAT_SUPER_DIR, additionalPath);
  }

  /**
   * Resolve a path inside the deploy directory. Split out from
   * {@link #buildDratSubdirectoryPath(String)} so the rule can be exercised
   * against a DRAT_HOME other than this JVM's -- the constants above are
   * static finals read from the real environment at class-init time.
   */
  static String dratSubdirectory(String dratHome, String additionalPath) {
    return dratHome + additionalPath;
  }
  
  
}
