package id.prasetya.vibrefy.beans;

import java.io.File;
import java.io.IOException;

import org.json.JSONArray;

import id.prasetya.vibrefy.data.CloudItem;
import id.prasetya.vibrefy.tools.cloud.CloudConfig;
import id.prasetya.vibrefy.tools.cloud.CloudProvider;

/**
 * Names of the media files sitting beside the addressed file, as a JSON array.
 *
 * Exists for series auto-play: an episode started from the Home watch list has no
 * folder listing on the page to find the next episode in, so the player asks here.
 * Read-only - it stores nothing and takes the same session-in-URL shape as stream
 * and subtitles, so the player can derive its URL from the stream URL.
 */
public class MediaListBean extends BeanObject
{
  public static final String CMDMediaList="medialist";

  private JSONArray names=new JSONArray();

  protected void processData()
  {
    contentType="application/json";
    if (path.getLength()<4)return;
    if (path.getSession()==null)return;
    try
    {
      if (path.isCloud())listCloud();
      else listLocal();
    } catch (IOException e)
    {
      // An unreachable provider simply yields no auto-play candidates.
    }
  }

  private void listLocal()
  {
    if (path.getRealPath()==null)return;
    File parent=new File(path.getRealPath()).getParentFile();
    if (parent==null || !parent.isDirectory())return;
    File[] files=parent.listFiles();
    if (files==null)return;
    for (File file:files)
      if (file.isFile() && BrowseBean.isMedia(file.getName()))names.put(file.getName());
  }

  private void listCloud() throws IOException
  {
    CloudProvider provider=path.getProvider();
    if (provider==null || !provider.isValid())return;
    String cloudPath=path.getCloudPath();
    int slash=cloudPath.lastIndexOf('/');
    String parentPath=slash>=0?cloudPath.substring(0,slash):"";
    String parentId=CloudConfig.resolveId(path.getSession(),provider,parentPath);
    if (parentId==null)return;
    CloudItem[] children=provider.listChildren(parentId);
    CloudConfig.cacheChildren(path.getSession(),provider,parentPath,children);
    for (CloudItem child:children)
      if (!child.isFolder() && BrowseBean.isMedia(child.getName()))names.put(child.getName());
  }

  public String getMediaList()
  {
    return names.toString();
  }
}
