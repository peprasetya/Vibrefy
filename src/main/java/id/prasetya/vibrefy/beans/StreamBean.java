package id.prasetya.vibrefy.beans;

import java.io.*;

import org.json.*;

import id.prasetya.vibrefy.Portal;
import id.prasetya.vibrefy.SessionTracker;
import id.prasetya.vibrefy.data.CloudItem;
import id.prasetya.vibrefy.data.CloudLink;
import id.prasetya.vibrefy.tools.cloud.CloudConfig;
import id.prasetya.vibrefy.tools.cloud.CloudProvider;

public class StreamBean extends BeanObject
{
  public static final String CMDStream="stream";
  
  File file=null;
  long start=0;
  CloudLink cloudLink=null;

  public File getFile() {return file;}
  public long getStart() {return start;}
  /** Non-null when this stream must be proxied from a cloud provider. */
  public CloudLink getCloudLink() {return cloudLink;}

  protected void processData()
  {
    contentType=Portal.VIDEO_MP4_TYPE;
    if (path.getLength()<4)return;
    if (path.getSession()==null)return;

    long fileLength;
    if (path.isCloud())
    {
      fileLength=prepareCloud();
      if (fileLength<=0)return;
    } else
    {
      if (path.getRealPath()==null)return;
      file=new File(path.getRealPath());
      if (file==null || !file.exists() || !file.isFile())
      {
        file=null;
        return;
      }
      fileLength=file.length();
    }

    String range=request.getHeader("Range");
    long end=fileLength-1;
      
    if (range!=null) try
    {
      if (range.startsWith("bytes="))
      {
        String[] parts=range.substring("bytes=".length()).split("-");
        start=Long.parseLong(parts[0].trim());
        if (parts.length>1&&parts[1]!=null&&!parts[1].trim().isEmpty())end=Long.parseLong(parts[1].trim());
        if (end>fileLength-1)end=fileLength-1;
        contentRange="bytes "+start+"-"+end+"/"+fileLength;
        partial=true;
      }
    }catch (Exception e)
    {
      start=0;
      end=fileLength-1;
    }
    contentLength=end-start+1;
  }
  
  /**
   * Resolves the cloud item and its download link. Returns the file size, or 0 when the
   * item cannot be served. All media is proxied, so the browser only ever talks to us.
   */
  private long prepareCloud()
  {
    try
    {
      CloudProvider provider=path.getProvider();
      if (provider==null || !provider.isValid())return 0;
      String itemId=path.getCloudId();
      if (itemId==null)
      {
        // The path walked to a dead end on the provider (a network failure would have
        // thrown instead), so the file was moved or deleted - retire its watch entry.
        removeProgress();
        return 0;
      }
      CloudItem item=provider.getItem(itemId);
      if (item==null || item.isFolder() || item.getSize()<=0)return 0;
      cloudLink=provider.getDownloadLink(itemId);
      return item.getSize();
    } catch (IOException e)
    {
      System.out.println("ERRSTREAMCLOUD:"+e.getMessage());
      cloudLink=null;
      return 0;
    }
  }

  /** Drops this cloud path's watch-list entry once the file provably no longer exists. */
  private void removeProgress()
  {
    JSONObject userData=SessionTracker.getSessionData(path.getSession());
    JSONArray progress=userData.optJSONArray(SessionTracker.DataProgress);
    if (progress==null)return;
    String key=BrowseBean.CMDBrowse+"/"+CloudConfig.CLOUDPREFIX+path.getCloudLabel()+"/"+path.getCloudPath();
    for (int i=progress.length()-1;i>=0;i--)
      if (key.equals(progress.getJSONObject(i).optString(SessionTracker.DataProgressFile)))progress.remove(i);
  }

  public String[] getSubPaths()
  {
    return path.getSubPaths();
  }
  

}
