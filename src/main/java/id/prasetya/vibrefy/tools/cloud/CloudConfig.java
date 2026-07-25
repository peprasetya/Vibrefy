package id.prasetya.vibrefy.tools.cloud;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.json.JSONArray;
import org.json.JSONObject;

import id.prasetya.vibrefy.Portal;
import id.prasetya.vibrefy.SessionTracker;
import id.prasetya.vibrefy.beans.AuthOpenIDBean;
import id.prasetya.vibrefy.data.CloudItem;
import jakarta.servlet.http.HttpSession;

/**
 * Owns the per-user "clouds" array inside .vibed: reads it, builds providers from it,
 * and caches the path-to-id lookups that browsing depends on.
 *
 * Cloud mounts are deliberately per-user and are never added to the shared Libraries
 * list, so one user linking their Drive grants nobody else access to it.
 */
public class CloudConfig
{
  public static final String KeyKind="kind";
  public static final String KeyLabel="label";
  public static final String KeyAccount="account";
  public static final String KeyRoot="root";
  public static final String KeyRootName="rootname";
  public static final String KeyClient="client";
  public static final String KeyRefresh="refresh";
  public static final String KeyAccess="access";
  public static final String KeyExpire="expire";
  public static final String KeyApiKey="key";
  public static final String KeyHost="host";
  public static final String KeyAdded="added";

  /** Marks a library slot as a cloud mount. Unreserved in RFC 3986, so no encoding. */
  public static final String CLOUDPREFIX="~";

  private static final int MAX_CACHE=20000;

  // issSubId|label|relativePath -> provider item id
  private static final Map<String,String> idCache=java.util.Collections.synchronizedMap(
      new LinkedHashMap<String,String>(256,0.75f,true)
      {
        private static final long serialVersionUID=1L;
        protected boolean removeEldestEntry(Map.Entry<String,String> eldest)
        {
          return size()>MAX_CACHE;
        }
      });

  // Providers are cached so one account keeps a single CloudToken, which keeps its
  // synchronized refresh meaningful across concurrent requests. The token rides along
  // so its persistence listener can be rebound to the caller's live session - a
  // listener left on an expired session would pin it in heap and flush into the void.
  private static final class CachedProvider
  {
    final CloudProvider provider;
    final CloudToken token;
    CachedProvider(CloudProvider provider,CloudToken token)
    {
      this.provider=provider;
      this.token=token;
    }
  }

  private static final ConcurrentHashMap<String,CachedProvider> providerCache=new ConcurrentHashMap<>();

  public static String getUserId(HttpSession session)
  {
    if (session==null)return null;
    return (String)session.getAttribute(Portal.SessionAccountID);
  }

  public static JSONArray getClouds(HttpSession session)
  {
    if (session==null)return new JSONArray();
    JSONObject data=SessionTracker.getSessionData(session);
    JSONArray clouds=data.optJSONArray(SessionTracker.DataClouds);
    if (clouds==null)
    {
      clouds=new JSONArray();
      data.put(SessionTracker.DataClouds,clouds);
    }
    return clouds;
  }

  public static JSONObject findCloud(HttpSession session,String label)
  {
    if (label==null)return null;
    JSONArray clouds=getClouds(session);
    for (int i=0;i<clouds.length();i++)
    {
      JSONObject entry=clouds.getJSONObject(i);
      if (label.equals(entry.optString(KeyLabel)))return entry;
    }
    return null;
  }

  /** Labels become URL path segments, so keep them to a safe, predictable shape. */
  public static boolean isValidLabel(String label)
  {
    if (label==null)return false;
    if (label.length()<1 || label.length()>40)return false;
    for (int i=0;i<label.length();i++)
    {
      char c=label.charAt(i);
      boolean ok=(c>='A' && c<='Z') || (c>='a' && c<='z') || (c>='0' && c<='9') || c=='.' || c=='_' || c=='-';
      if (!ok)return false;
    }
    return true;
  }

  public static void invalidate(HttpSession session,String label)
  {
    String user=getUserId(session);
    if (user==null)return;
    providerCache.remove(user+"|"+label);
    String prefix=user+"|"+label+"|";
    synchronized (idCache)
    {
      idCache.keySet().removeIf(key -> key.startsWith(prefix));
    }
  }

  public static CloudProvider getProvider(HttpSession session,String label)
  {
    String user=getUserId(session);
    if (user==null || label==null)return null;
    String cacheKey=user+"|"+label;
    CachedProvider cached=providerCache.get(cacheKey);
    if (cached!=null)
    {
      // Rebind persistence to the caller's session: the user may have logged in again
      // since this provider was built, and rotated tokens must land in the live .vibed.
      if (cached.token!=null)
      {
        JSONObject entry=findCloud(session,label);
        if (entry!=null)bindToken(cached.token,session,entry);
      }
      return cached.provider;
    }

    JSONObject entry=findCloud(session,label);
    if (entry==null)return null;
    CloudToken[] tokenOut=new CloudToken[1];
    CloudProvider provider=create(session,entry,tokenOut);
    if (provider!=null)providerCache.put(cacheKey,new CachedProvider(provider,tokenOut[0]));
    return provider;
  }

  /**
   * Builds a provider from one .vibed entry. OAuth providers borrow the client
   * credentials configured for sign-in, since both Google and Microsoft support
   * incremental authorisation on a single app registration.
   */
  public static CloudProvider create(HttpSession session,JSONObject entry)
  {
    return create(session,entry,null);
  }

  private static CloudProvider create(HttpSession session,JSONObject entry,CloudToken[] tokenOut)
  {
    String kindText=entry.optString(KeyKind);
    if (kindText==null || kindText.length()==0)return null;
    char kind=kindText.charAt(0);
    String label=entry.optString(KeyLabel);

    if (kind==FileLuProvider.KIND)return new FileLuProvider(label,entry.optString(KeyApiKey),entry.optString(KeyRoot,"0"));

    // pCloud's token never expires and has no refresh token, so it is used directly
    // and needs no CloudToken. Its API host is per-user (US or EU) and stored per mount.
    if (kind==PCloudProvider.KIND)return new PCloudProvider(label,entry.optString(KeyAccess,null),entry.optString(KeyHost,PCloudProvider.HOST_US),entry.optString(KeyRoot,"0"));

    JSONObject auth=findAuth(kind);
    if (auth==null)return null;
    String clientId=auth.optString(AuthOpenIDBean.PropAuthClientID);
    String secret=auth.optString(AuthOpenIDBean.PropAuthSecret);

    CloudToken token=new CloudToken(tokenEndpoint(kind),clientId,secret,scope(kind),
        entry.optString(KeyRefresh,null),entry.optString(KeyAccess,null),entry.optLong(KeyExpire,0));
    bindToken(token,session,entry);
    if (tokenOut!=null)tokenOut[0]=token;

    if (kind==DriveProvider.KIND)return new DriveProvider(label,token,entry.optString(KeyRoot,"root"));
    if (kind==GraphProvider.KIND)return new GraphProvider(label,token,entry.optString(KeyRoot,null));
    return null;
  }

  /**
   * Points the token's persistence at this session's .vibed entry. Rotated tokens are
   * written straight away - a refresh token lost to a crash means the user has to link
   * the account again, and some providers rotate it on every use.
   */
  private static void bindToken(CloudToken token,HttpSession session,JSONObject entry)
  {
    token.setListener(new CloudToken.Listener()
    {
      public void tokenRefreshed(CloudToken refreshed)
      {
        entry.put(KeyRefresh,refreshed.getRefreshToken());
        entry.put(KeyAccess,refreshed.getAccessTokenRaw());
        entry.put(KeyExpire,refreshed.getExpire());
        SessionTracker.flush(session);
      }
    });
  }

  /** Called when a user's last session ends, so cached providers stop pinning it. */
  public static void releaseUser(String userId)
  {
    if (userId==null)return;
    String prefix=userId+"|";
    providerCache.keySet().removeIf(key -> key.startsWith(prefix));
    synchronized (idCache)
    {
      idCache.keySet().removeIf(key -> key.startsWith(prefix));
    }
  }

  /** Undeploy cleanup, so no cached provider or resolver entry survives a redeploy. */
  public static void shutdown()
  {
    providerCache.clear();
    synchronized (idCache)
    {
      idCache.clear();
    }
  }

  public static JSONObject findAuth(char kind)
  {
    JSONArray auth=Portal.getProperties(AuthOpenIDBean.PropAuth);
    if (auth==null)return null;
    for (int i=0;i<auth.length();i++)
    {
      JSONObject entry=auth.getJSONObject(i);
      String code=entry.optString(AuthOpenIDBean.PropAuthProvider);
      if (code!=null && code.length()>0 && code.charAt(0)==kind)return entry;
    }
    return null;
  }

  public static String tokenEndpoint(char kind)
  {
    if (kind==GraphProvider.KIND)return "https://login.microsoftonline.com/common/oauth2/v2.0/token";
    return "https://oauth2.googleapis.com/token";
  }

  public static String authEndpoint(char kind)
  {
    if (kind==GraphProvider.KIND)return "https://login.microsoftonline.com/common/oauth2/v2.0/authorize";
    if (kind==PCloudProvider.KIND)return "https://my.pcloud.com/oauth2/authorize";
    return "https://accounts.google.com/o/oauth2/v2/auth";
  }

  public static String scope(char kind)
  {
    if (kind==GraphProvider.KIND)return "offline_access Files.Read.All User.Read";
    // pCloud grants full access on approval and takes no scope parameter.
    if (kind==PCloudProvider.KIND)return "";
    return "https://www.googleapis.com/auth/drive.readonly";
  }

  public static String kindName(char kind)
  {
    if (kind==DriveProvider.KIND)return "Google Drive";
    if (kind==GraphProvider.KIND)return "OneDrive";
    if (kind==FileLuProvider.KIND)return "FileLu";
    if (kind==PCloudProvider.KIND)return "pCloud";
    return "Cloud";
  }

  /**
   * Walks a mount-relative path to a provider item id, caching each step. Normal
   * browsing lists a folder before you can click into it, so the cache is warm for
   * free; the full walk only happens on a deep link after a restart.
   */
  public static String resolveId(HttpSession session,CloudProvider provider,String relativePath) throws IOException
  {
    if (provider==null)return null;
    String user=getUserId(session);
    String rootId=provider.getRootId();
    if (relativePath==null || relativePath.length()==0)return rootId;

    String[] parts=relativePath.split("/");
    String currentId=rootId;
    StringBuilder walked=new StringBuilder();
    for (String part:parts)
    {
      if (part==null || part.length()==0)continue;
      if (walked.length()>0)walked.append('/');
      walked.append(part);
      String cacheKey=user+"|"+provider.getLabel()+"|"+walked;
      String known=idCache.get(cacheKey);
      if (known!=null)
      {
        currentId=known;
        continue;
      }
      CloudItem child=provider.findChild(currentId,part);
      if (child==null)return null;
      currentId=child.getId();
      idCache.put(cacheKey,currentId);
    }
    return currentId;
  }

  /** Called while listing so later lookups of these children are already resolved. */
  public static void cacheChildren(HttpSession session,CloudProvider provider,String parentPath,CloudItem[] items)
  {
    String user=getUserId(session);
    if (user==null || items==null)return;
    String base=(parentPath==null || parentPath.length()==0)?"":parentPath+"/";
    for (CloudItem item:items)
      idCache.put(user+"|"+provider.getLabel()+"|"+base+item.getName(),item.getId());
  }
}
