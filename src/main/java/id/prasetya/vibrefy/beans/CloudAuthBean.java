package id.prasetya.vibrefy.beans;

import java.io.IOException;

import org.json.JSONObject;

import id.prasetya.vibrefy.SessionTracker;
import id.prasetya.vibrefy.tools.Random;
import id.prasetya.vibrefy.tools.cloud.CloudConfig;
import id.prasetya.vibrefy.tools.cloud.CloudProvider;
import id.prasetya.vibrefy.tools.cloud.DriveProvider;
import id.prasetya.vibrefy.tools.cloud.GraphProvider;
import id.prasetya.vibrefy.tools.cloud.HttpTool;
import id.prasetya.vibrefy.tools.cloud.PCloudProvider;

/**
 * OAuth round trip that attaches a cloud account to the signed-in user.
 *
 * Separate from AuthOpenIDBean on purpose. This requires an existing login, grants
 * storage scopes rather than identity, and lets one user link several accounts.
 * It also passes redirect_uri explicitly instead of going through OpenID, whose
 * instances are shared static state and already race between concurrent logins.
 */
public class CloudAuthBean extends BeanObject
{
  public static final String COMMAND="cloudauth";

  private static final String CloudState="cldState";
  // The OAuth state carries the flow marker, provider kind and label so the callback is
  // self-describing and does not depend on the session surviving the round trip:
  //   cld:<kind>:<label>:<nonce>
  // The label charset is [A-Za-z0-9._-] (enforced by CloudConfig.isValidLabel) and the
  // kind is a single letter, so ':' is an unambiguous separator.
  private static final String STATE_PREFIX="cld:";

  private String kind=null;
  private String label=null;
  private String code=null;
  private String state=null;
  private String error=null;
  private String hostname=null;
  private String result=null;

  public void setKind(String newValue){kind=newValue;}
  public void setLabel(String newValue){label=newValue;}
  public void setCode(String newValue){code=newValue;}
  public void setState(String newValue){state=newValue;}
  public void setError(String newValue){error=newValue;}
  // pCloud appends the regional API host to the callback; all later calls must use it.
  public void setHostname(String newValue){hostname=newValue;}

  public String getResult(){return result==null?"":result;}

  /**
   * True when a callback's state belongs to a cloud authorisation, recognised purely by
   * the state's format. The OAuth callback lands on the shared /authopenid redirect URI;
   * AuthOpenIDBean uses this to hand a cloud callback back to this bean via Portal's
   * command rewrite. Deliberately session-free, so the round trip works even if the
   * popup's session differs from the one that started the flow.
   */
  public static boolean isCloudCallbackState(String state)
  {
    return state!=null && state.startsWith(STATE_PREFIX);
  }

  private String redirectUri()
  {
    // Both legs, and the login flow, share one registered redirect URI: /authopenid.
    // The callback arrives there and is routed back here by AuthOpenIDBean. Built from
    // the public base so it is correct behind a TLS proxy.
    return publicBase()+AuthOpenIDBean.COMMAND;
  }

  protected void processData()
  {
    contentType="text/html";
    if (account==null)
    {
      result="Please sign in first.";
      return;
    }

    if (code==null && error==null)
    {
      startAuthorization();
      return;
    }
    completeAuthorization();
  }

  private void startAuthorization()
  {
    if (kind==null || kind.length()==0)
    {
      result="No provider requested.";
      return;
    }
    char kindCode=kind.charAt(0);
    if (!CloudConfig.isValidLabel(label))
    {
      result="Invalid name for this connection.";
      return;
    }
    if (CloudConfig.findCloud(session,label)!=null)
    {
      result="You already have a cloud storage named "+label+".";
      return;
    }
    JSONObject auth=CloudConfig.findAuth(kindCode);
    if (auth==null)
    {
      result=CloudConfig.kindName(kindCode)+" is not configured on this server yet.";
      return;
    }

    // Self-describing state: the provider kind and label ride along so the callback
    // needs no session to know what to do. The nonce is kept in the session as a
    // best-effort CSRF check.
    String newState=STATE_PREFIX+kindCode+":"+label+":"+Random.getAlphanumeric(14);
    session.setAttribute(CloudState,newState);

    String redirectUri=redirectUri();
    StringBuilder url=new StringBuilder(CloudConfig.authEndpoint(kindCode));
    url.append("?response_type=code");
    url.append("&client_id=").append(HttpTool.encode(auth.optString(AuthOpenIDBean.PropAuthClientID)));
    url.append("&redirect_uri=").append(HttpTool.encode(redirectUri));
    url.append("&state=").append(HttpTool.encode(newState));
    String scope=CloudConfig.scope(kindCode);
    if (scope!=null && scope.length()>0)url.append("&scope=").append(HttpTool.encode(scope));
    if (kindCode==DriveProvider.KIND)
    {
      // access_type=offline plus a forced consent is what makes Google return a refresh
      // token even for an already-granted account; select_account lets the user pick
      // which Google account to link rather than silently reusing the signed-in one.
      url.append("&access_type=offline&prompt=").append(HttpTool.encode("select_account consent"));
    }
    else if (kindCode==GraphProvider.KIND) url.append("&prompt=select_account");
    // pCloud takes none of these extra parameters.
    // Assign the inherited BeanObject.redirect field (NOT a local) so Portal issues the
    // 302 to the provider instead of rendering this bean's page.
    redirect=url.toString();
  }

  private void completeAuthorization()
  {
    String expectedState=(String)session.getAttribute(CloudState);
    // Clear first, so a replayed callback cannot run the exchange a second time.
    session.removeAttribute(CloudState);

    if (error!=null && error.length()>0)
    {
      result="Authorisation was declined ("+error+").";
      return;
    }

    // Everything needed is in the state itself: cld:<kind>:<label>:<nonce>.
    String[] parts=(state==null)?new String[0]:state.split(":",4);
    if (parts.length!=4 || !("cld".equals(parts[0])) || parts[1].length()==0 || parts[2].length()==0)
    {
      result="This authorisation request is incomplete, please try again.";
      return;
    }
    // Best-effort CSRF check: if the session still holds the state it must match, but a
    // lost session (popup on a different session behind a proxy) does not block the flow -
    // the code still has to be exchanged with the client secret to be of any use.
    if (expectedState!=null && !expectedState.equals(state))
    {
      result="This authorisation request has expired, please try again.";
      return;
    }

    char kindCode=parts[1].charAt(0);
    String storedLabel=parts[2];
    JSONObject auth=CloudConfig.findAuth(kindCode);
    if (auth==null)
    {
      result=CloudConfig.kindName(kindCode)+" is no longer configured.";
      return;
    }

    if (kindCode==PCloudProvider.KIND)
    {
      completePCloud(auth,String.valueOf(kindCode),storedLabel);
      return;
    }

    String tokenRedirect=redirectUri();
    StringBuilder body=new StringBuilder();
    body.append("grant_type=authorization_code");
    body.append("&code=").append(HttpTool.encode(code));
    body.append("&client_id=").append(HttpTool.encode(auth.optString(AuthOpenIDBean.PropAuthClientID)));
    body.append("&client_secret=").append(HttpTool.encode(auth.optString(AuthOpenIDBean.PropAuthSecret)));
    body.append("&redirect_uri=").append(HttpTool.encode(tokenRedirect));

    try
    {
      JSONObject tokens=HttpTool.postForm(CloudConfig.tokenEndpoint(kindCode),body.toString(),null);
      if (tokens==null || !tokens.has("access_token"))
      {
        String reason=tokens==null?"no response":tokens.optString("error_description",tokens.optString("error","unknown error"));
        result="Could not connect: "+reason;
        return;
      }
      String refresh=tokens.optString("refresh_token",null);
      if (refresh==null || refresh.length()==0)
      {
        // An access-token-only mount would silently stop working within the hour, so
        // refuse it rather than store something that looks connected but is not.
        result="The provider did not grant offline access, so this connection would stop working within the hour. Please remove Vibrefy from your account's third-party apps and try again.";
        return;
      }

      JSONObject entry=new JSONObject();
      entry.put(CloudConfig.KeyKind,String.valueOf(kindCode));
      entry.put(CloudConfig.KeyLabel,storedLabel);
      entry.put(CloudConfig.KeyClient,auth.optString(AuthOpenIDBean.PropAuthClientID));
      entry.put(CloudConfig.KeyRefresh,refresh);
      entry.put(CloudConfig.KeyAccess,tokens.optString("access_token"));
      entry.put(CloudConfig.KeyExpire,System.currentTimeMillis()+(tokens.optLong("expires_in",3600L)*1000L));
      entry.put(CloudConfig.KeyRootName,CloudConfig.kindName(kindCode));
      entry.put(CloudConfig.KeyAdded,System.currentTimeMillis());
      CloudConfig.getClouds(session).put(entry);
      CloudConfig.invalidate(session,storedLabel);

      // Ask the provider who this is, purely so the UI can label the mount.
      try
      {
        CloudProvider provider=CloudConfig.getProvider(session,storedLabel);
        if (provider!=null)
        {
          entry.put(CloudConfig.KeyAccount,provider.getAccountName());
          entry.put(CloudConfig.KeyRoot,provider.getRootId());
        }
      } catch (IOException e)
      {
        System.out.println("ERRCLOUDAUTHROOT:"+e.getMessage());
      }

      // Written straight away: losing this refresh token to a crash would mean the
      // user has to link the account all over again.
      SessionTracker.flush(session);
      success=true;
      result="Connected "+storedLabel+".";
    } catch (IOException e)
    {
      result="Could not connect: "+e.getMessage();
    }
  }

  /**
   * pCloud's exchange is a plain GET on the region host named in the callback, and it
   * returns a permanent access_token with no refresh token, so the standard OAuth path
   * above (which requires a refresh token) does not apply.
   */
  private void completePCloud(JSONObject auth,String storedKind,String storedLabel)
  {
    String host=(hostname==null || hostname.length()==0)?PCloudProvider.HOST_US:hostname;
    // Only the two known pCloud hosts are allowed, so a tampered callback cannot point
    // the token exchange (which carries the client secret) at an attacker's server.
    if (!PCloudProvider.HOST_US.equals(host) && !PCloudProvider.HOST_EU.equals(host))
    {
      result="pCloud returned an unexpected region host.";
      return;
    }

    StringBuilder url=new StringBuilder("https://"+host+"/oauth2_token");
    url.append("?client_id=").append(HttpTool.encode(auth.optString(AuthOpenIDBean.PropAuthClientID)));
    url.append("&client_secret=").append(HttpTool.encode(auth.optString(AuthOpenIDBean.PropAuthSecret)));
    url.append("&code=").append(HttpTool.encode(code));

    try
    {
      JSONObject tokens=HttpTool.getJSON(url.toString(),null);
      if (tokens==null || !tokens.has("access_token"))
      {
        String reason=tokens==null?"no response":tokens.optString("error","result "+(tokens==null?"?":tokens.optInt("result")));
        result="Could not connect: "+reason;
        return;
      }

      JSONObject entry=new JSONObject();
      entry.put(CloudConfig.KeyKind,storedKind);
      entry.put(CloudConfig.KeyLabel,storedLabel);
      entry.put(CloudConfig.KeyClient,auth.optString(AuthOpenIDBean.PropAuthClientID));
      entry.put(CloudConfig.KeyAccess,tokens.optString("access_token"));
      entry.put(CloudConfig.KeyHost,host);
      entry.put(CloudConfig.KeyRoot,"0");
      entry.put(CloudConfig.KeyRootName,CloudConfig.kindName(PCloudProvider.KIND));
      entry.put(CloudConfig.KeyAdded,System.currentTimeMillis());
      CloudConfig.getClouds(session).put(entry);
      CloudConfig.invalidate(session,storedLabel);

      try
      {
        CloudProvider provider=CloudConfig.getProvider(session,storedLabel);
        if (provider!=null)entry.put(CloudConfig.KeyAccount,provider.getAccountName());
      } catch (Exception e)
      {
        System.out.println("ERRCLOUDAUTHPCLOUD:"+e.getMessage());
      }

      SessionTracker.flush(session);
      success=true;
      result="Connected "+storedLabel+".";
    } catch (IOException e)
    {
      result="Could not connect: "+e.getMessage();
    }
  }
}
