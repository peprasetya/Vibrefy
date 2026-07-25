package id.prasetya.vibrefy.tools.cloud;

import java.io.IOException;

import org.json.JSONObject;

/**
 * OAuth access token for one linked cloud account, refreshed on demand.
 *
 * Refresh tokens can rotate - Microsoft issues a new one on every refresh - so a
 * listener is notified whenever the stored values change and can persist them
 * immediately. Losing a rotated refresh token means the account must be re-linked.
 */
public class CloudToken
{
  /** Refresh slightly early, so a slow request cannot expire mid-flight. */
  private static final long SKEW=60000L;

  public interface Listener
  {
    public void tokenRefreshed(CloudToken token);
  }

  private String tokenEndpoint=null;
  private String clientId=null;
  private String clientSecret=null;
  private String scope=null;
  private String refreshToken=null;
  private String accessToken=null;
  private long expire=0;
  private Listener listener=null;
  private String lastError=null;

  public CloudToken(String tokenEndpoint,String clientId,String clientSecret,String scope,String refreshToken,String accessToken,long expire)
  {
    this.tokenEndpoint=tokenEndpoint;
    this.clientId=clientId;
    this.clientSecret=clientSecret;
    this.scope=scope;
    this.refreshToken=refreshToken;
    this.accessToken=accessToken;
    this.expire=expire;
  }

  public void setListener(Listener listener){this.listener=listener;}
  public String getRefreshToken(){return refreshToken;}
  public String getAccessTokenRaw(){return accessToken;}
  public long getExpire(){return expire;}
  public String getLastError(){return lastError;}
  public boolean isValid(){return refreshToken!=null && refreshToken.length()>0;}

  public synchronized String getAccessToken()
  {
    if (accessToken!=null && expire>System.currentTimeMillis()+SKEW)return accessToken;
    refresh();
    return accessToken;
  }

  /** Forces the next call to fetch a new access token, after a 401 for instance. */
  public synchronized void invalidate()
  {
    expire=0;
  }

  public synchronized boolean refresh()
  {
    if (refreshToken==null || refreshToken.length()==0)
    {
      lastError="No refresh token stored";
      return false;
    }
    StringBuilder body=new StringBuilder();
    body.append("grant_type=refresh_token");
    body.append("&refresh_token=").append(HttpTool.encode(refreshToken));
    body.append("&client_id=").append(HttpTool.encode(clientId));
    if (clientSecret!=null && clientSecret.length()>0)body.append("&client_secret=").append(HttpTool.encode(clientSecret));
    if (scope!=null && scope.length()>0)body.append("&scope=").append(HttpTool.encode(scope));
    try
    {
      JSONObject result=HttpTool.postForm(tokenEndpoint,body.toString(),null);
      if (result==null)
      {
        lastError="No response from token endpoint";
        return false;
      }
      if (!result.has("access_token"))
      {
        lastError=result.optString("error_description",result.optString("error","Token refresh failed"));
        return false;
      }
      accessToken=result.getString("access_token");
      expire=System.currentTimeMillis()+(result.optLong("expires_in",3600L)*1000L);
      if (result.has("refresh_token"))refreshToken=result.getString("refresh_token");
      lastError=null;
      if (listener!=null)listener.tokenRefreshed(this);
      return true;
    } catch (IOException e)
    {
      lastError=e.getMessage();
      return false;
    }
  }

  public String[] authHeader()
  {
    String token=getAccessToken();
    if (token==null)return new String[0];
    return new String[]{"Authorization","Bearer "+token};
  }
}
