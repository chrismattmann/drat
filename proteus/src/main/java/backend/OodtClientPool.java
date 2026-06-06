package backend;

import org.apache.oodt.cas.filemgr.structs.exceptions.ConnectionException;
import org.apache.oodt.cas.filemgr.system.FileManagerClient;
import org.apache.oodt.cas.filemgr.util.RpcCommunicationFactory;
import org.apache.oodt.cas.metadata.util.PathUtils;
import org.apache.oodt.cas.workflow.system.WorkflowManagerClient;
import org.apache.oodt.pcs.util.WorkflowManagerUtils;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;

public final class OodtClientPool {
  private static FileManagerClient fileManagerClient;
  private static WorkflowManagerUtils workflowManagerUtils;

  private OodtClientPool() {
  }

  public static synchronized FileManagerClient getFileManagerClient()
      throws MalformedURLException, ConnectionException {
    if (fileManagerClient == null) {
      fileManagerClient = RpcCommunicationFactory.createClient(
          new URL(PathUtils.replaceEnvVariables(FileConstants.FILEMGR_URL)));
    }
    return fileManagerClient;
  }

  public static synchronized WorkflowManagerClient getWorkflowManagerClient() {
    if (workflowManagerUtils == null) {
      workflowManagerUtils = new WorkflowManagerUtils(
          PathUtils.replaceEnvVariables(FileConstants.CLIENT_URL));
    }
    return workflowManagerUtils.getClient();
  }

  public static synchronized void resetFileManagerClient() {
    if (fileManagerClient != null) {
      try {
        fileManagerClient.close();
      } catch (IOException ignore) {
      }
      fileManagerClient = null;
    }
  }

  public static synchronized void resetWorkflowManagerClient() {
    if (workflowManagerUtils != null) {
      try {
        workflowManagerUtils.close();
      } catch (IOException ignore) {
      }
      workflowManagerUtils = null;
    }
  }
}
