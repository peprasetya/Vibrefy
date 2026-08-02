package id.prasetya.vibrefy.beans;

import java.io.*;
import java.util.*;

import org.json.*;

import id.prasetya.vibrefy.*;
import id.prasetya.vibrefy.tools.*;
import id.prasetya.vibrefy.tools.Random;

public class AuthOpenIDBean extends BeanObject
{
  public static final String COMMAND="authopenid";

  public static final String PropAuth="Authentication";
  public static final String PropAuthProvider="Provider";
  public static final String PropAuthDomain="Domain";
  public static final String PropAuthClientID="ClientId";
  public static final String PropAuthSecret="Secret";
  public static final String PropAuthScope="Scope";
  public static final String PropAuthPrompUser="Prompt";
  public static final String PropAuthPersistent="Persistent";





  private static final String HEADERREFERER="Referer";
  private static final String KeyIDToken="id_token";
  private static final String KeyEmail="email";
  //private static final String KeyName="name";
  private static final String KeyISS="iss";
  private static final String KeySUB="sub";
  private static final String OpenIDSession="oidState";
  private static final String OpenIDProvider="oidProvider";
  private static final String OpenIDReferrer="oidReferrer";
  
  private static ArrayList<OpenID> openIds=null;
  private static long configCheck=0;

  private static void initialize()
  {
    if (openIds==null || configCheck<System.currentTimeMillis()) try
    {
      if (openIds==null)openIds=new ArrayList<OpenID>(); else openIds.clear();
      JSONArray auth=Portal.getProperties(PropAuth);
      if (auth==null || auth.length()==0)
      {
        System.out.println("Authentication is not SET!!!!");
        return;
      }
      for (int i=0;i<auth.length();i++)
      {
        JSONObject ath=auth.getJSONObject(i);
        if (!ath.has(PropAuthProvider))continue;
        if (!ath.has(PropAuthClientID))continue;
        if (!ath.has(PropAuthSecret))continue;
        String config=null;
        char provider=ath.getString(PropAuthProvider).charAt(0);
        switch (provider)
        {
          case 'G':
            config="https://accounts.google.com/.well-known/openid-configuration";
            break;
          case 'M':
            config="https://login.microsoftonline.com/common/v2.0/.well-known/openid-configuration";
            break;
          case 'Y':
            config="https://api.login.yahoo.com/.well-known/openid-configuration";
            break;
        }
        if (config==null)continue;
        String scope="openid email profile";
        if (ath.has(PropAuthScope))scope=ath.getString(PropAuthScope);
        ArrayList<String> options=new ArrayList<>();
        if (ath.has(PropAuthPrompUser) && ath.getBoolean(PropAuthPrompUser))options.add("prompt=select_account");
        if (ath.has(PropAuthPersistent) && ath.getBoolean(PropAuthPersistent))options.add("access_type=offline");
        openIds.add(new OpenID(provider,ath.has(PropAuthDomain)?ath.getString(PropAuthDomain):null,config,ath.getString(PropAuthClientID),ath.getString(PropAuthSecret),scope,String.join("&",options)));
      }
      if (openIds.size()==0)System.out.println("No Valid Authentication Config!!");
      //scope="openid email profile https://www.googleapis.com/auth/drive";
      configCheck=System.currentTimeMillis()+3600000L;
    } catch (IOException e)
    {
      System.out.println("Authentication config error: "+e.getMessage()+" configs:"+openIds.size());
    }

  }
  
  /**
   * Drops the cached provider list so newly saved credentials take effect at once
   * instead of waiting out the hourly refresh.
   */
  public static void resetConfig()
  {
    configCheck=0;
  }

  private static OpenID getOpenID(char provider,String domain)
  {
    for (OpenID ch:openIds)
      if (ch.getProviderCode()==provider && (ch.getDomain()==null || ch.getDomain().equals(domain)))return ch;
    return null;
  }
  
  String provcall=null;
  String oiCode=null;
  String oiState=null;
  String referrer;
  
  public void setProvider(String newValue)
  {
    provcall=newValue;
  }
  
  public void setCode(String newValue)
  {
    oiCode=newValue;
  }
  
  public void setState(String newValue)
  {
    oiState=newValue; 
  }
  


  protected void processData()
  {
    initialize();
    contentType="text/html";
    // Cloud account linking registers this same redirect URI. A cloud callback is known
    // by the format of its state (cld:...), so recognising it needs no session - the
    // request is handed to CloudAuthBean via Portal's command rewrite.
    if ((provcall==null || provcall.length()==0) && oiCode!=null && CloudAuthBean.isCloudCallbackState(oiState))
    {
      command=Command.getCommand(CloudAuthBean.COMMAND);
      return;
    }
    if (provcall!=null && provcall.length()>0)
    {
      OpenID openid=getOpenID(provcall.charAt(0),request.getServerName());
      if (openid!=null)
      {
        
        String state=Random.getAlphanumeric(14);
        session.setAttribute(OpenIDSession,state);
        session.setAttribute(OpenIDProvider,provcall);
        referrer=request.getHeader(HEADERREFERER);
        session.setAttribute(OpenIDReferrer,referrer);
        // Built from the public base, not sliced out of the Referer: that header is
        // absent on a direct hit, a noreferrer popup, or a privacy-stripping browser,
        // and slicing a null threw. This is also the exact URI CloudAuthBean registers,
        // so both flows send the redirect_uri the provider has on file.
        redirect=openid.getAuthorizationEndpoint(publicBase()+COMMAND,state);
        return;
      } else
      {
        command=Command.getCommand("welcome");
      }
    } else
    {
      //String oiCode=request.getParameter("code");
      String type=null;
      String state=null;
      if (
          oiCode!=null && oiCode.length()>0 && (type=(String)session.getAttribute(OpenIDProvider))!=null &&
          (state=(String)session.getAttribute(OpenIDSession))!=null && state.equals(oiState)
         ) try
      {
        referrer=(String)session.getAttribute(OpenIDReferrer);
        session.removeAttribute(OpenIDSession);
        session.removeAttribute(OpenIDProvider);
        session.removeAttribute(OpenIDReferrer);
        char provider=type.charAt(0);
        //char mode='L';
        //if (type.length()>1)mode=type.charAt(1);
        OpenID oconf=getOpenID(provider,request.getServerName());
        if (oconf==null)return;
        int tCount=0;
        for (tCount=0;tCount<3;tCount++)try
        {
          JSONObject tokens=oconf.exchangeCode(oiCode);
          if (!tokens.has(KeyIDToken))
          {
            System.out.println("Token exchange returned no id_token. ("+provider+")");
            return;
          }
          String idToken=tokens.getString(KeyIDToken);
          String[] idparts=idToken.split("\\.");
          if (idparts.length!=3)
          {
            System.out.println("Malformed id_token from provider "+provider+": expected 3 parts, got "+idparts.length);
            return;
          }
          //JSONObject idHeader=new JSONObject(new JSONTokener(new String(Base64.getDecoder().decode(idparts[0]))));
          JSONObject idClaim=new JSONObject(new JSONTokener(new String(Base64.getDecoder().decode(idparts[1]))));
          String tEmai=idClaim.has(KeyEmail)?idClaim.getString(KeyEmail):null;
          String issSubId=(idClaim.has(KeyISS)?idClaim.getString(KeyISS):"")+"/"+(idClaim.has(KeySUB)?idClaim.getString(KeySUB):"");
          if (issSubId==null || issSubId.length()<2)System.out.println("Incomplete iss/sub claim from provider "+provider);
          
          JSONArray admins=Portal.getProperties(Portal.PropAdmin);
          session.setAttribute(Portal.SessionEmail,tEmai);
          session.setAttribute(Portal.SessionAccountID,issSubId);
          request.changeSessionId();
          SessionTracker.sessionCheck(session);
          // Admin is claimed only by whoever just completed the setup screen, not by
          // whoever happens to sign in first while the administrator list is empty.
          if (admins==null || admins.length()==0)
          {
            if (SetupBean.consumePending())
            {
              session.setAttribute(Portal.SessionAdmin,true);
              if (admins==null)admins=new JSONArray();
              admins.put(tEmai);
              Portal.setProperties(Portal.PropAdmin,admins);
            }
          } else for (int i=0;i<admins.length();i++)
          {
            if (admins.getString(i).equals(tEmai))session.setAttribute(Portal.SessionAdmin,true);
          }
          return;
        }catch (IOException e)
        {
          e.printStackTrace(System.out);
          try {Thread.sleep(1000);}catch (InterruptedException e1){}
        }
        
      }catch (NullPointerException e) {} else
      {
        command=Command.getCommand("welcome");
      }
      //PrintWriter out = response.getWriter();
      //String target=request.getRequestURL().toString().replaceAll("auth","");
    }
  }
  
  public String getReferrer()
  {
    return referrer;
  }
}
