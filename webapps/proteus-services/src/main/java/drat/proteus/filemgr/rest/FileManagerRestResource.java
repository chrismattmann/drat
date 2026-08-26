package drat.proteus.filemgr.rest;

import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.core.MediaType;

import backend.OodtClientPool;
import org.apache.oodt.cas.filemgr.system.FileManagerClient;
import org.apache.oodt.cas.filemgr.structs.ProductType;

import java.util.logging.Logger;

@Path("/filemanager")
@Produces(MediaType.APPLICATION_JSON)
public class FileManagerRestResource {
  
  private static final long serialVersionUID = -588588505908995065L;
  
  private static final Logger LOG = Logger.getLogger(FileManagerRestResource.class.getName());
  
  public FileManagerRestResource() {
  }
  
  @GET
  @Path("/progress")
  public FileManagerProgressResponse getProgress() throws Exception {
    FileManagerProgressResponse response = new FileManagerProgressResponse();
    try {
      OodtClientPool.withFileManagerClient(client -> {
        ProductType type = client.getProductTypeByName("GenericFile");
        response.crawledFiles = type == null ? 0 : client.getNumProducts(type);
        return null;
      });
    } catch (Exception ex) {
      LOG.warning("Unable to get File Manager progress: " + ex.getMessage());
      response.crawledFiles = 0;
    }
    return response;
  }
 
  
}
