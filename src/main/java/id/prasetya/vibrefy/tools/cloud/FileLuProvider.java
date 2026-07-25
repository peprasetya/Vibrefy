package id.prasetya.vibrefy.tools.cloud;

import java.io.IOException;
import java.util.ArrayList;

import org.json.JSONArray;
import org.json.JSONObject;

import id.prasetya.vibrefy.data.CloudItem;
import id.prasetya.vibrefy.data.CloudLink;

/**
 * FileLu, read-only, authenticated with an API key rather than OAuth.
 *
 * FileLu identifies folders by a numeric fld_id but files by an alphanumeric
 * file_code, so ids carry a "d:" or "f:" prefix to keep the two apart. The other
 * providers use a single id space and need no prefix.
 */
public class FileLuProvider implements CloudProvider
{
  public static final char KIND='F';

  private static final String API="https://filelu.com/api/";
  private static final String DIR="d:";
  private static final String FILE="f:";
  /**
   * Direct links are short lived, so they are re-acquired rather than stored.
   *
   * Verified against a live link: the CDN answers ranged GETs with 206 and a correct
   * Content-Range at arbitrary mid-file offsets, so seeking works. It also sends
   * Access-Control-Allow-Origin: https://filelu.com, meaning a browser could not fetch
   * it directly - proxying through the server is what makes this provider usable at
   * all. Responses carry Connection: close, so every range request costs a fresh TLS
   * handshake; that is why subtitle extraction results are cached to disk.
   */
  private static final long LINK_LIFETIME=10L*60L*1000L;

  private String label=null;
  private String apiKey=null;
  private String rootId=null;
  private String error=null;

  public FileLuProvider(String label,String apiKey,String rootId)
  {
    this.label=label;
    this.apiKey=apiKey;
    this.rootId=DIR+((rootId==null || rootId.length()==0)?"0":stripPrefix(rootId));
  }

  public char getKind(){return KIND;}
  public String getLabel(){return label;}
  public String getRootId(){return rootId;}
  public boolean isValid(){return apiKey!=null && apiKey.length()>0;}
  public String getError(){return error;}
  public String getAccountName(){return "";}

  private static String stripPrefix(String id)
  {
    if (id==null)return "";
    if (id.startsWith(DIR) || id.startsWith(FILE))return id.substring(2);
    return id;
  }

  /** Unwraps FileLu's {status, msg, result} envelope. */
  private JSONObject unwrap(JSONObject response) throws IOException
  {
    if (response==null)throw new IOException("FileLu request failed ("+HttpTool.getLastStatus()+")");
    int status=response.optInt("status",0);
    if (status!=200)
    {
      String msg=response.optString("msg","status "+status);
      error=msg;
      throw new IOException("FileLu error: "+msg);
    }
    return response;
  }

  public CloudItem[] listChildren(String folderId) throws IOException
  {
    // POST rather than GET so the API key stays out of the URL and cannot be captured
    // by any access log or proxy that records request lines.
    String body="fld_id="+HttpTool.encode(stripPrefix(folderId))+"&key="+HttpTool.encode(apiKey);
    JSONObject response=unwrap(HttpTool.postForm(API+"folder/list",body,null));
    ArrayList<CloudItem> items=new ArrayList<>();

    JSONObject result=response.optJSONObject("result");
    if (result==null)return new CloudItem[0];

    JSONArray folders=result.optJSONArray("folders");
    if (folders!=null)for (int i=0;i<folders.length();i++)
    {
      JSONObject entry=folders.getJSONObject(i);
      items.add(new CloudItem(DIR+entry.opt("fld_id"),entry.optString("name"),true,0,0));
    }

    JSONArray files=result.optJSONArray("files");
    if (files!=null)for (int i=0;i<files.length();i++)
    {
      JSONObject entry=files.getJSONObject(i);
      items.add(new CloudItem(FILE+entry.optString("file_code"),entry.optString("name"),false,
          entry.optLong("size",0),0));
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
    if (itemId==null || itemId.startsWith(DIR))return null;
    String body="file_code="+HttpTool.encode(stripPrefix(itemId))+"&key="+HttpTool.encode(apiKey);
    JSONObject response=unwrap(HttpTool.postForm(API+"file/info",body,null));
    JSONArray result=response.optJSONArray("result");
    if (result==null || result.length()==0)return null;
    JSONObject info=result.getJSONObject(0);
    return new CloudItem(itemId,info.optString("name"),false,info.optLong("size",0),0);
  }

  public CloudLink getDownloadLink(String itemId) throws IOException
  {
    String body="file_code="+HttpTool.encode(stripPrefix(itemId))+"&key="+HttpTool.encode(apiKey);
    JSONObject response=unwrap(HttpTool.postForm(API+"file/direct_link",body,null));
    JSONObject result=response.optJSONObject("result");
    if (result==null)throw new IOException("FileLu returned no direct link");
    String url=result.optString("url",null);
    if (url==null || url.length()==0)throw new IOException("FileLu returned no direct link");
    // FileLu answers status 200 / msg OK even when it refuses, putting a human
    // sentence such as "Upgrade to the Premium plan..." in the url field. Direct
    // links are a paid feature, so check we actually got a URL before using it.
    if (!url.startsWith("http://") && !url.startsWith("https://"))
    {
      error=url;
      throw new IOException("FileLu did not return a playable link: "+url);
    }
    return new CloudLink(url,null,System.currentTimeMillis()+LINK_LIFETIME);
  }
}
