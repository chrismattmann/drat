package drat.proteus.filemgr.rest;

import backend.OodtClientPool;
import org.apache.oodt.cas.filemgr.system.FileManagerClient;
import org.apache.oodt.cas.filemgr.structs.ProductType;
import org.wicketstuff.rest.annotations.MethodMapping;
import org.wicketstuff.rest.contenthandling.json.webserialdeserial.GsonWebSerialDeserial;
import org.wicketstuff.rest.resource.AbstractRestResource;
import org.wicketstuff.rest.utils.http.HttpMethod;

import java.util.logging.Logger;

public class FileManagerRestResource extends AbstractRestResource<GsonWebSerialDeserial> {
  
  private static final long serialVersionUID = -588588505908995065L;
  
  private static final Logger LOG = Logger.getLogger(FileManagerRestResource.class.getName());
  
  public FileManagerRestResource() {
    super(new GsonWebSerialDeserial());
  }
  
  @MethodMapping(value = "/progress",httpMethod = HttpMethod.GET)
  public FileManagerProgressResponse getProgress() throws Exception {
    FileManagerProgressResponse response = new FileManagerProgressResponse();
    try {
      FileManagerClient client = OodtClientPool.getFileManagerClient();
      synchronized (client) {
        ProductType type = client.getProductTypeByName("GenericFile");
        response.crawledFiles = type == null ? 0 : client.getNumProducts(type);
      }
    } catch (Exception ex) {
      OodtClientPool.resetFileManagerClient();
      LOG.warning("Unable to get File Manager progress: " + ex.getMessage());
      response.crawledFiles = 0;
    }
    return response;
  }
 
  
}
