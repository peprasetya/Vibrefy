package id.prasetya.vibrefy.beans;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;

import org.json.*;

import id.prasetya.vibrefy.Portal;
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
    // Only the home command answers JSON; homepart stays on the Ajax/XML path that the
    // three browser shelves use, and has no content JSP of its own.
    if (COMMAND.equalsIgnoreCase(path.getCommand()) && wantsJson())contentType=Portal.JSON_TYPE;
  }

  /**
   * The watch list as JSON. New and Random are not emitted: neither has any backing data,
   * and without a database working them out would mean a recursive walk of every library
   * on each load.
   */
  public String getJson()
  {
    JSONObject result=new JSONObject();
    JSONArray watching=new JSONArray();
    for (FileItem file:getResumable())watching.put(file.toJSON());
    result.put("watching",watching);
    return result.toString();
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
