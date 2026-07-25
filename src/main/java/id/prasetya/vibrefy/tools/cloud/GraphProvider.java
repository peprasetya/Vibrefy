package id.prasetya.vibrefy.tools.cloud;

import java.io.IOException;
import java.util.ArrayList;

import org.json.JSONArray;
import org.json.JSONObject;

import id.prasetya.vibrefy.data.CloudItem;
import id.prasetya.vibrefy.data.CloudLink;

/**
 * Microsoft OneDrive through the Graph API, read-only.
 */
public class GraphProvider implements CloudProvider
{
  public static final char KIND='M';

  private static final String API="https://graph.microsoft.com/v1.0/me/drive/";
  private static final String SELECT="id,name,size,folder,file,lastModifiedDateTime";
  private static final String DOWNLOAD_KEY="@microsoft.graph.downloadUrl";
  /** Graph does not state how long the download URL lives; an hour is the observed value. */
  private static final long LINK_LIFETIME=45L*60L*1000L;

  private String label=null;
  private CloudToken token=null;
  private String rootId=null;
  private String account=null;
  private String error=null;

  public GraphProvider(String label,CloudToken token,String rootId)
  {
    this.label=label;
    this.token=token;
    this.rootId=(rootId==null || rootId.length()==0)?null:rootId;
  }

  public char getKind(){return KIND;}
  public String getLabel(){return label;}
  public boolean isValid(){return token!=null && token.isValid();}
  public String getError(){return error!=null?error:(token==null?"Not linked":token.getLastError());}

  public String getRootId() throws IOException
  {
    if (rootId!=null)return rootId;
    JSONObject result=request(API+"root?$select=id");
    if (result==null)throw new IOException("OneDrive root lookup failed ("+HttpTool.getLastStatus()+")");
    rootId=result.optString("id");
    return rootId;
  }

  public String getAccountName()
  {
    if (account!=null)return account;
    try
    {
      JSONObject me=request("https://graph.microsoft.com/v1.0/me?$select=userPrincipalName,displayName");
      if (me!=null)account=me.optString("userPrincipalName",me.optString("displayName",""));
    } catch (IOException e)
    {
      error=e.getMessage();
    }
    return account==null?"":account;
  }

  /** GET with one automatic retry after refreshing an expired bearer. */
  private JSONObject request(String url) throws IOException
  {
    JSONObject result=HttpTool.getJSON(url,token.authHeader());
    if (result==null && (HttpTool.getLastStatus()==401 || HttpTool.getLastStatus()==403))
    {
      token.invalidate();
      result=HttpTool.getJSON(url,token.authHeader());
    }
    return result;
  }

  private CloudItem toItem(JSONObject entry)
  {
    boolean folder=entry.has("folder");
    return new CloudItem(entry.optString("id"),entry.optString("name"),folder,
        entry.optLong("size",0),parseTime(entry.optString("lastModifiedDateTime")));
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

  public CloudItem[] listChildren(String folderId) throws IOException
  {
    ArrayList<CloudItem> items=new ArrayList<>();
    String url=API+"items/"+HttpTool.encode(folderId)+"/children?$select="+SELECT+"&$top=200";
    while (url!=null)
    {
      JSONObject result=request(url);
      if (result==null)throw new IOException("OneDrive list failed ("+HttpTool.getLastStatus()+")");
      JSONArray values=result.optJSONArray("value");
      if (values!=null)for (int i=0;i<values.length();i++)items.add(toItem(values.getJSONObject(i)));
      url=result.optString("@odata.nextLink",null);
      if (url!=null && url.length()==0)url=null;
    }
    return items.toArray(new CloudItem[0]);
  }

  public CloudItem findChild(String folderId,String name) throws IOException
  {
    for (CloudItem item:listChildren(folderId))
      if (name.equals(item.getName()))return item;
    return null;
  }

  public CloudItem getItem(String itemId) throws IOException
  {
    JSONObject result=request(API+"items/"+HttpTool.encode(itemId)+"?$select="+SELECT);
    if (result==null)return null;
    return toItem(result);
  }

  public CloudLink getDownloadLink(String itemId) throws IOException
  {
    // Ask for the property rather than hitting /content, which 302s to the same CDN
    // host - following that with the bearer attached would leak it cross-host.
    JSONObject result=request(API+"items/"+HttpTool.encode(itemId)+"?$select=id,"+HttpTool.encode(DOWNLOAD_KEY));
    if (result==null)throw new IOException("OneDrive link failed ("+HttpTool.getLastStatus()+")");
    String url=result.optString(DOWNLOAD_KEY,null);
    if (url==null || url.length()==0)throw new IOException("OneDrive returned no download URL");
    // The URL is pre-authenticated; sending an Authorization header can get it rejected.
    return new CloudLink(url,null,System.currentTimeMillis()+LINK_LIFETIME);
  }
}
