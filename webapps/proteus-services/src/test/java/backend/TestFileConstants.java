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

import junit.framework.TestCase;

/**
 * DRAT_HOME is the deploy directory, and everything the webapp drives lives
 * directly beneath it. The derivation used to chop DRAT_HOME at the last
 * occurrence of the literal "drat" and re-append "/deploy", which cancels out
 * only for a DRAT_HOME ending in exactly "drat/deploy" -- every other layout
 * got paths that do not exist, and go() failed writing data/repo.
 */
public class TestFileConstants extends TestCase {

  public void testResolvesUnderTheConventionalLayout() {
    assertEquals("/opt/drat/deploy/data/repo",
        FileConstants.dratSubdirectory("/opt/drat/deploy", "/data/repo"));
  }

  /** A deploy directory that merely contains "drat" must not be truncated. */
  public void testDoesNotTruncateADirectoryNamedAfterDrat() {
    assertEquals("/srv/audit/drat-deploy/data/repo",
        FileConstants.dratSubdirectory("/srv/audit/drat-deploy", "/data/repo"));
  }

  /** "drat" appearing in a parent directory name must not be an anchor. */
  public void testIgnoresDratInsideAParentDirectoryName() {
    assertEquals("/home/drattest/deploy/bin/drat",
        FileConstants.dratSubdirectory("/home/drattest/deploy", "/bin/drat"));
  }

  /** A DRAT_HOME with no "drat" in it at all must still resolve. */
  public void testResolvesWhenTheNameDoesNotContainDrat() {
    assertEquals("/var/lib/audits/bin/oodt",
        FileConstants.dratSubdirectory("/var/lib/audits", "/bin/oodt"));
  }

  /** Every path the webapp drives must stay inside the deploy directory. */
  public void testAllPathsStayUnderDratHome() {
    String home = "/srv/audit/drat-deploy";
    for (String sub : new String[] {"/bin/oodt", "/bin/drat", "/data/repo",
        "/data/clones", "/data/staging", "/workflow/bin/wmgr-client",
        "/filemgr/etc/indexer.properties", "/crawler/policy/crawler-config.xml"}) {
      String resolved = FileConstants.dratSubdirectory(home, sub);
      assertTrue("[" + resolved + "] escaped DRAT_HOME", resolved.startsWith(home + "/"));
    }
  }
}
