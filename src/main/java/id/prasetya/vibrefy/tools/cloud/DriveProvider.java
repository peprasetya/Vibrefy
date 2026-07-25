package id.prasetya.vibrefy.tools.cloud;

import java.io.IOException;
import java.util.ArrayList;

import org.json.JSONArray;
import org.json.JSONObject;

import id.prasetya.vibrefy.data.CloudItem;
import id.prasetya.vibrefy.data.CloudLink;

/**
 * Google Drive, read-only.
 *
 * The media URL never expires; the bearer token does, so a stale read shows up as a
 * 401 and is fixed by refreshing rather than by fetching a new link.
 */
public class DriveProvider implements CloudProvider
{
  public static final char KIND='G';

  private static final String API="https://www.googleapis.com/drive/v3/";
  private static final String FOLDER_MIME="application/vnd.google-apps.folder";
  private static final String FIELDS="id,name,mimeType,size,modifiedTime";
  private static final String COMMON="&supportsAllDrives=true&includeItemsFromAllDrives=true";

  // A Drive account is not a single tree: My Drive, shared drives, and files shared with
  // the user are separate collections. The mount therefore roots at a virtual folder that
  // lists whichever of these the account actually has, each as its own sub-tree.
  private static final String VROOT="vroot";           // the virtual container
  private static final String MYDRIVE_ID="root";       // Drive's own My Drive root
  private static final String MYDRIVE_NAME="My Drive";
  private static final String VSHARED="vshared";       // folder listing the shared drives
  private static final String SHARED_NAME="Shared drives";
  private static final String SHARED_WITH_ME="sharedWithMe";
  private static final String SHARED_WITH_ME_NAME="Shared with me";

  private String label=null;
  private CloudToken token=null;
  private String rootId=null;
  private String account=null;
  private String error=null;

  public DriveProvider(String label,CloudToken token,String rootId)
  {
    this.label=label;
    this.token=token;
    // A whole-drive mount (the default, or the legacy "root") uses the virtual root; a
    // mount pinned to a specific folder roots directly there.
    if (rootId==null || rootId.length()==0 || MYDRIVE_ID.equals(rootId) || VROOT.equals(rootId))this.rootId=VROOT;
    else this.rootId=rootId;
  }

  public char getKind(){return KIND;}
  public String getLabel(){return label;}
  public String getRootId(){return rootId;}
  public boolean isValid(){return token!=null && token.isValid();}
  public String getError(){return error!=null?error:(token==null?"Not linked":token.getLastError());}

  public String getAccountName()
  {
    if (account!=null)return account;
    try
    {
      JSONObject about=HttpTool.getJSON(API+"about?fields=user(emailAddress,displayName)",token.authHeader());
      if (about!=null)
      {
        JSONObject user=about.optJSONObject("user");
        if (user!=null)account=user.optString("emailAddress",user.optString("displayName",""));
      }
    } catch (IOException e)
    {
      error=e.getMessage();
    }
    return account==null?"":account;
  }

  private CloudItem toItem(JSONObject file)
  {
    boolean folder=FOLDER_MIME.equals(file.optString("mimeType"));
    long size=0;
    // Google-native documents report no size; they are not playable media anyway.
    if (file.has("size"))try
    {
      size=Long.parseLong(file.getString("size"));
    } catch (NumberFormatException e)
    {
      size=0;
    }
    return new CloudItem(file.optString("id"),file.optString("name"),folder,size,parseTime(file.optString("modifiedTime")));
  }

  private long parseTime(String value)
  {
    if (value==null || value.length()==0)return 0;
    try
    {
      return java.time.Instant.parse(value).toEpochMilli();
    } catch (Exception e)
    {
      return 0;
    }
  }

  private String quote(String value)
  {
    // Drive query strings are single-quoted, so embedded quotes and backslashes escape.
    return value.replace("\\","\\\\").replace("'","\\'");
  }

  /** GET with a single automatic retry after refreshing an expired bearer. */
  private JSONObject get(String url) throws IOException
  {
    JSONObject result=HttpTool.getJSON(url,token.authHeader());
    if (result==null && HttpTool.getLastStatus()==401)
    {
      token.invalidate();
      result=HttpTool.getJSON(url,token.authHeader());
    }
    return result;
  }

  /** Runs a files.list query to completion, following pagination. */
  private CloudItem[] queryFiles(String q) throws IOException
  {
    ArrayList<CloudItem> items=new ArrayList<>();
    String pageToken=null;
    do
    {
      StringBuilder url=new StringBuilder(API);
      url.append("files?pageSize=1000&fields=nextPageToken,files(").append(FIELDS).append(")").append(COMMON);
      url.append("&q=").append(HttpTool.encode(q));
      if (pageToken!=null)url.append("&pageToken=").append(HttpTool.encode(pageToken));
      JSONObject result=get(url.toString());
      if (result==null)throw new IOException("Drive list failed ("+HttpTool.getLastStatus()+")");
      JSONArray files=result.optJSONArray("files");
      if (files!=null)for (int i=0;i<files.length();i++)items.add(toItem(files.getJSONObject(i)));
      pageToken=result.optString("nextPageToken",null);
    } while (pageToken!=null && pageToken.length()>0);
    return items.toArray(new CloudItem[0]);
  }

  /**
   * Shared drives (Team Drives) as folder entries. Non-fatal: an account with no shared
   * drives, or one whose access is limited, simply contributes nothing.
   */
  private ArrayList<CloudItem> listSharedDrives()
  {
    ArrayList<CloudItem> drives=new ArrayList<>();
    try
    {
      String pageToken=null;
      do
      {
        String url=API+"drives?pageSize=100&fields=nextPageToken,drives(id,name)"
            +(pageToken!=null?"&pageToken="+HttpTool.encode(pageToken):"");
        JSONObject result=get(url);
        if (result==null)break;
        JSONArray arr=result.optJSONArray("drives");
        if (arr!=null)for (int i=0;i<arr.length();i++)
        {
          JSONObject d=arr.getJSONObject(i);
          drives.add(new CloudItem(d.optString("id"),d.optString("name"),true,0,0));
        }
        pageToken=result.optString("nextPageToken",null);
      } while (pageToken!=null && pageToken.length()>0);
    } catch (IOException e)
    {
      error=e.getMessage();
    }
    return drives;
  }

  /** Whether anything has been shared with the user, checked with a single-item query. */
  private boolean hasSharedWithMe()
  {
    try
    {
      String url=API+"files?pageSize=1&fields=files(id)"+COMMON+"&q="+HttpTool.encode("sharedWithMe=true and trashed=false");
      JSONObject result=get(url);
      if (result==null)return false;
      JSONArray files=result.optJSONArray("files");
      return files!=null && files.length()>0;
    } catch (IOException e)
    {
      return false;
    }
  }

  /** The virtual root: My Drive, plus shared drives and shared-with-me when present. */
  private CloudItem[] virtualRoots()
  {
    ArrayList<CloudItem> roots=new ArrayList<>();
    roots.add(new CloudItem(MYDRIVE_ID,MYDRIVE_NAME,true,0,0));
    if (!listSharedDrives().isEmpty())roots.add(new CloudItem(VSHARED,SHARED_NAME,true,0,0));
    if (hasSharedWithMe())roots.add(new CloudItem(SHARED_WITH_ME,SHARED_WITH_ME_NAME,true,0,0));
    return roots.toArray(new CloudItem[0]);
  }

  public CloudItem[] listChildren(String folderId) throws IOException
  {
    if (VROOT.equals(folderId))return virtualRoots();
    if (VSHARED.equals(folderId))return listSharedDrives().toArray(new CloudItem[0]);
    if (SHARED_WITH_ME.equals(folderId))return queryFiles("sharedWithMe=true and trashed=false");
    // My Drive ("root") and every real folder, including inside a shared drive.
    return queryFiles("'"+quote(folderId)+"' in parents and trashed=false");
  }

  public CloudItem findChild(String folderId,String name) throws IOException
  {
    if (VROOT.equals(folderId))
    {
      if (MYDRIVE_NAME.equals(name))return new CloudItem(MYDRIVE_ID,MYDRIVE_NAME,true,0,0);
      if (SHARED_NAME.equals(name))return new CloudItem(VSHARED,SHARED_NAME,true,0,0);
      if (SHARED_WITH_ME_NAME.equals(name))return new CloudItem(SHARED_WITH_ME,SHARED_WITH_ME_NAME,true,0,0);
      return null;
    }
    if (VSHARED.equals(folderId))
    {
      for (CloudItem d:listSharedDrives())if (name.equals(d.getName()))return d;
      return null;
    }
    String q;
    if (SHARED_WITH_ME.equals(folderId))q="sharedWithMe=true and name='"+quote(name)+"' and trashed=false";
    else q="'"+quote(folderId)+"' in parents and name='"+quote(name)+"' and trashed=false";
    String url=API+"files?pageSize=2&fields=files("+FIELDS+")"+COMMON+"&q="+HttpTool.encode(q);
    JSONObject result=get(url);
    if (result==null)return null;
    JSONArray files=result.optJSONArray("files");
    if (files==null || files.length()==0)return null;
    return toItem(files.getJSONObject(0));
  }

  private boolean isVirtual(String itemId)
  {
    return VROOT.equals(itemId) || VSHARED.equals(itemId) || SHARED_WITH_ME.equals(itemId);
  }

  public CloudItem getItem(String itemId) throws IOException
  {
    // Virtual folders are not real Drive files; never ask the API for them.
    if (isVirtual(itemId))return new CloudItem(itemId,itemId,true,0,0);
    String url=API+"files/"+HttpTool.encode(itemId)+"?fields="+FIELDS+COMMON;
    JSONObject result=get(url);
    if (result==null)return null;
    return toItem(result);
  }

  public CloudLink getDownloadLink(String itemId) throws IOException
  {
    String url=API+"files/"+HttpTool.encode(itemId)+"?alt=media"+COMMON;
    // Expiry tracks the bearer, since the URL itself stays valid indefinitely.
    return new CloudLink(url,token.authHeader(),token.getExpire());
  }
}
