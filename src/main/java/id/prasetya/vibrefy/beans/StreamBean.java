package id.prasetya.vibrefy.beans;

import java.io.*;

import org.json.*;

import id.prasetya.vibrefy.Portal;
import id.prasetya.vibrefy.SessionTracker;
import id.prasetya.vibrefy.data.CloudLink;
import id.prasetya.vibrefy.tools.cloud.CloudConfig;
import id.prasetya.vibrefy.tools.cloud.CloudProvider;

public class StreamBean extends BeanObject
{
  public static final String CMDStream="stream";
  
  File file=null;
  long start=0;
  CloudLink cloudLink=null;
  CloudProvider cloudProvider=null;
  String cloudItemId=null;
  boolean cloudCached=false;
  long cloudSize=0;

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
      CloudConfig.Stream stream=CloudConfig.openStream(path.getSession(),provider,itemId);
      if (stream==null)return 0;
      cloudProvider=provider;
      cloudItemId=itemId;
      cloudLink=stream.getLink();
      cloudCached=stream.isCached();
      cloudSize=stream.getSize();
      return stream.getSize();
    } catch (IOException e)
    {
      // Class only: the message of a failed cloud request can carry the file's URL.
      System.out.println("ERRSTREAMCLOUD:"+e.getClass().getSimpleName());
      cloudLink=null;
      return 0;
    }
  }

  /**
   * Called when the provider refused the link that was handed out, which for a cached link
   * means it expired or was revoked sooner than expected. Forgets it and fetches a fresh
   * one. False when there is nothing to retry: the link was already fresh, or the file is
   * no longer the size the response headers were written for.
   */
  public boolean refreshCloudLink()
  {
    if (!cloudCached || cloudProvider==null)return false;
    CloudConfig.forgetStream(path.getSession(),cloudProvider,cloudItemId);
    cloudCached=false;
    try
    {
      CloudConfig.Stream stream=CloudConfig.openStream(path.getSession(),cloudProvider,cloudItemId);
      if (stream==null || stream.getSize()!=cloudSize)return false;
      cloudLink=stream.getLink();
      return true;
    } catch (IOException e)
    {
      System.out.println("ERRSTREAMCLOUD:"+e.getClass().getSimpleName());
      return false;
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
