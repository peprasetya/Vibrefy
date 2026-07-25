package id.prasetya.vibrefy.tools.cloud;

import java.io.IOException;
import java.util.ArrayList;

import org.json.JSONArray;
import org.json.JSONObject;

import id.prasetya.vibrefy.data.CloudItem;
import id.prasetya.vibrefy.data.CloudLink;

/**
 * pCloud, read-only.
 *
 * Two things set pCloud apart from the OAuth providers. Its access token never
 * expires and there is no refresh token, so there is no CloudToken here - the stored
 * token is used directly. And a user's data lives in one of two regions (US or EU); the
 * OAuth callback names the API host to use, and every call must go to that host, so it
 * is stored per mount rather than being a constant.
 */
public class PCloudProvider implements CloudProvider
{
  public static final char KIND='P';

  public static final String HOST_US="api.pcloud.com";
  public static final String HOST_EU="eapi.pcloud.com";

  /** pCloud getfilelink URLs are temporary; re-acquire rather than store. */
  private static final long LINK_LIFETIME=10L*60L*1000L;

  private String label=null;
  private String token=null;
  private String apiHost=null;
  private long rootId=0;
  private String account=null;
  private String error=null;

  public PCloudProvider(String label,String token,String apiHost,String rootFolderId)
  {
    this.label=label;
    this.token=token;
    this.apiHost=(apiHost==null || apiHost.length()==0)?HOST_US:apiHost;
    try
    {
      this.rootId=(rootFolderId==null || rootFolderId.length()==0)?0:Long.parseLong(rootFolderId);
    } catch (NumberFormatException e)
    {
      this.rootId=0;
    }
  }

  public char getKind(){return KIND;}
  public String getLabel(){return label;}
  public String getRootId(){return Long.toString(rootId);}
  public boolean isValid(){return token!=null && token.length()>0;}
  public String getError(){return error;}

  /**
   * Finds which region a directly-entered access token belongs to. A token is bound to
   * one region, and pCloud answers userinfo with result 0 only on the matching host
   * (the other returns "registered in another location"), so this both validates the
   * token and resolves the host. Returns the host, or null if the token works on
   * neither - which means it is invalid.
   */
  public static String detectHost(String token)
  {
    if (token==null || token.length()==0)return null;
    String[] header=new String[]{"Authorization","Bearer "+token};
    for (String host:new String[]{HOST_US,HOST_EU})
    {
      try
      {
        JSONObject info=HttpTool.getJSON("https://"+host+"/userinfo",header);
        if (info!=null && info.optInt("result",-1)==0)return host;
      } catch (IOException e)
      {
        // Try the other region before giving up.
      }
    }
    return null;
  }

  private String api(String method)
  {
    return "https://"+apiHost+"/"+method;
  }

  // Bearer header rather than an access_token query param, so the token never lands in
  // a URL that could be logged.
  private String[] auth()
  {
    return new String[]{"Authorization","Bearer "+token};
  }

  /** pCloud answers 200 with a non-zero "result" code on failure; surface it. */
  private JSONObject check(JSONObject response) throws IOException
  {
    if (response==null)throw new IOException("pCloud request failed ("+HttpTool.getLastStatus()+")");
    int result=response.optInt("result",0);
    if (result!=0)
    {
      String msg=response.optString("error","result "+result);
      error=msg;
      throw new IOException("pCloud error: "+msg);
    }
    return response;
  }

  public String getAccountName()
  {
    if (account!=null)return account;
    try
    {
      JSONObject info=check(HttpTool.getJSON(api("userinfo"),auth()));
      account=info.optString("email","");
    } catch (IOException e)
    {
      error=e.getMessage();
    }
    return account==null?"":account;
  }

  private CloudItem toItem(JSONObject entry)
  {
    boolean folder=entry.optBoolean("isfolder",false);
    String id;
    if (folder)id="d:"+entry.optLong("folderid");
    else id="f:"+entry.optLong("fileid");
    long modified=parseTime(entry.optString("modified"));
    return new CloudItem(id,entry.optString("name"),folder,entry.optLong("size",0),modified);
  }

  private long parseTime(String value)
  {
    if (value==null || value.length()==0)return 0;
    try
    {
      // pCloud uses RFC 1123 dates, e.g. "Sat, 01 Jan 2022 10:00:00 +0000".
      return java.time.ZonedDateTime.parse(value,java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli();
    } catch (Exception e)
    {
      return 0;
    }
  }

  private long folderIdOf(String id) throws IOException
  {
    // Accept both a bare number (root) and the "d:" prefixed form.
    String raw=id;
    if (raw.startsWith("d:"))raw=raw.substring(2);
    try
    {
      return Long.parseLong(raw);
    } catch (NumberFormatException e)
    {
      throw new IOException("Not a pCloud folder id: "+id);
    }
  }

  public CloudItem[] listChildren(String folderId) throws IOException
  {
    String url=api("listfolder")+"?folderid="+folderIdOf(folderId);
    JSONObject response=check(HttpTool.getJSON(url,auth()));
    JSONObject metadata=response.optJSONObject("metadata");
    if (metadata==null)return new CloudItem[0];
    JSONArray contents=metadata.optJSONArray("contents");
    if (contents==null)return new CloudItem[0];
    ArrayList<CloudItem> items=new ArrayList<>();
    for (int i=0;i<contents.length();i++)items.add(toItem(contents.getJSONObject(i)));
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
    if (itemId==null || itemId.startsWith("d:"))return null;
    String raw=itemId.startsWith("f:")?itemId.substring(2):itemId;
    // stat returns file metadata (size, name) without the checksum work checksumfile does.
    String url=api("stat")+"?fileid="+HttpTool.encode(raw);
    JSONObject response=check(HttpTool.getJSON(url,auth()));
    JSONObject metadata=response.optJSONObject("metadata");
    if (metadata==null)return null;
    return toItem(metadata);
  }

  public CloudLink getDownloadLink(String itemId) throws IOException
  {
    String raw=itemId.startsWith("f:")?itemId.substring(2):itemId;
    String url=api("getfilelink")+"?fileid="+HttpTool.encode(raw);
    JSONObject response=check(HttpTool.getJSON(url,auth()));
    JSONArray hosts=response.optJSONArray("hosts");
    String path=response.optString("path",null);
    if (hosts==null || hosts.length()==0 || path==null)throw new IOException("pCloud returned no download link");
    // The link is a temporary tokenised URL served by a plain HTTP host, so it needs
    // no auth header of its own.
    String link="https://"+hosts.getString(0)+path;
    return new CloudLink(link,null,System.currentTimeMillis()+LINK_LIFETIME);
  }
}
