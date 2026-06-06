package drat.proteus.filemgr.rest;

import backend.FileConstants;
import org.apache.oodt.cas.filemgr.structs.ProductType;
import org.apache.oodt.cas.metadata.util.PathUtils;
import org.apache.oodt.pcs.util.FileManagerUtils;
import org.wicketstuff.rest.annotations.MethodMapping;
import org.wicketstuff.rest.contenthandling.json.webserialdeserial.GsonWebSerialDeserial;
import org.wicketstuff.rest.resource.AbstractRestResource;
import org.wicketstuff.rest.utils.http.HttpMethod;

import java.util.logging.Logger;

public class FileManagerRestResource extends AbstractRestResource<GsonWebSerialDeserial> {
  
  private static final long serialVersionUID = -588588505908995065L;
  private FileManagerUtils fileManagerUtils;
  
  private static final Logger LOG = Logger.getLogger(FileManagerRestResource.class.getName());
  
  public FileManagerRestResource() {
    super(new GsonWebSerialDeserial());
    try{
      fileManagerUtils = new FileManagerUtils(
          PathUtils.replaceEnvVariables(FileConstants.FILEMGR_URL));
    }catch (Exception ex){
      LOG.severe(ex.getMessage());
    }
    
  }
  
  @MethodMapping(value = "/progress",httpMethod = HttpMethod.GET)
  public FileManagerProgressResponse getProgress() throws Exception {
    FileManagerProgressResponse response = new FileManagerProgressResponse();
    ProductType type = fileManagerUtils.safeGetProductTypeByName("GenericFile");
    response.crawledFiles = type == null ? 0 : fileManagerUtils.safeGetNumProducts(type);
    return response;
  }
 
  
}
