package id.prasetya.vibrefy.beans;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;

import org.json.*;

import id.prasetya.vibrefy.SessionTracker;
import id.prasetya.vibrefy.data.*;
import id.prasetya.vibrefy.tools.cloud.CloudConfig;

public class HomeBean extends BeanObject
{
  public static final String COMMAND="home";
  public static final String CMDPart="homepart";
  public static final String ORDWatch="watch";
  public static final String ORDNew="new";
  public static final String ORDRandom="random";
  
  protected void processData()
  {
  }
  
  public FileItem[] getResumable()
  {
    ArrayList<FileItem> rst=new ArrayList<>();
    JSONObject userData=SessionTracker.getSessionData(session);
    JSONArray progress=userData.optJSONArray(SessionTracker.DataProgress);
    if (progress==null)return new FileItem[0];
    for (int i=progress.length()-1;i>=0;i--)
    {
      JSONObject item=progress.getJSONObject(i);
      String path=item.optString(SessionTracker.DataProgressFile);
      if (path==null || path.trim().isEmpty())
      {
        progress.remove(i);
        continue;
      }
      PathMap mapped=new PathMap('/'+path,session);
      if (mapped.isCloud())
      {
        // Drop the entry only when the whole mount is gone. Checking each item would
        // cost one provider API call per watch-list entry on every home page load.
        if (CloudConfig.findCloud(session,mapped.getCloudLabel())==null)
        {
          progress.remove(i);
          continue;
        }
        String cloudPath=mapped.getCloudPath();
        int slash=cloudPath.lastIndexOf('/');
        String name=slash>=0?cloudPath.substring(slash+1):cloudPath;
        rst.add(new FileItem(name,name,mapped.getLibrary()+"/"+cloudPath,FileItem.TYPEFILE,
            item.optInt(SessionTracker.DataProgressTime),item.optLong(SessionTracker.DataProgressUpdate)));
        continue;
      }
      String realPath=mapped.getRealPath();
      if (realPath==null)
      {
        progress.remove(i);
        continue;
      }
      File check=new File(mapped.getRealPath().replaceAll("/",File.separator));
      if (!(check.exists() && check.isFile()))
      {
        progress.remove(i);
        continue;
      }
         rst.add(new FileItem(check.getName(),check.getName(),mapped.getLibrary()+"/"+mapped.getSuffix(),FileItem.TYPEFILE,item.optInt(SessionTracker.DataProgressTime),item.optLong(SessionTracker.DataProgressUpdate)));
    }
    rst.sort(Comparator.comparingLong(FileItem::getSortTime).reversed());
    return rst.toArray(new FileItem[0]);
  }
}
