/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE.txt file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to you under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package drat.tools;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Empties a JDBC workflow instance repository.
 *
 * <p>
 * Deleting the database files would be simpler and is wrong: the schema goes
 * with them, and the workflow manager then starts against a database with no
 * WORKFLOW_INSTANCES table and fails every query it is asked. The rows are
 * what a reset is about; the tables are not.
 * </p>
 *
 * <p>
 * Nothing must hold the database while this runs -- HSQLDB in file mode
 * allows one writer, so the manager has to be stopped first, which is what
 * the reset in bin/drat does.
 * </p>
 */
public final class ClearWorkflowInstances {

  /** Children before parents, so no delete trips over a reference. */
  private static final String[] TABLES = {
      "WORKFLOW_INSTANCE_METADATA", "WORKFLOW_INSTANCES"
  };

  private ClearWorkflowInstances() {
  }

  public static void main(String[] args) {
    if (args.length < 1) {
      System.err.println("usage: ClearWorkflowInstances <jdbcUrl> [user] [pass]");
      System.exit(2);
    }
    String url = args[0];
    String user = args.length > 1 ? args[1] : "sa";
    String pass = args.length > 2 ? args[2] : "";

    Connection connection = null;
    try {
      connection = DriverManager.getConnection(url, user, pass);
      Statement statement = connection.createStatement();
      for (int i = 0; i < TABLES.length; i++) {
        try {
          int rows = statement.executeUpdate("DELETE FROM " + TABLES[i]);
          System.out.println("Cleared " + rows + " rows from " + TABLES[i]);
        } catch (SQLException e) {
          // A repository that never held this table is already as empty as
          // it needs to be; say so rather than failing the reset.
          System.out.println("Skipped " + TABLES[i] + ": " + e.getMessage());
        }
      }
      // Leaves the files consistent, so the manager does not find a stale
      // lock the next time it starts.
      statement.execute("SHUTDOWN");
    } catch (SQLException e) {
      System.err.println("Unable to clear the workflow instance repository at ["
          + url + "]: " + e.getMessage());
      System.exit(1);
    } finally {
      if (connection != null) {
        try {
          connection.close();
        } catch (SQLException ignored) {
          // nothing useful to do about a connection that will not close
        }
      }
    }
  }
}
