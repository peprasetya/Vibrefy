package id.prasetya.vibrefy.beans;

import org.json.JSONArray;
import org.json.JSONObject;

import id.prasetya.vibrefy.Portal;

public class SetupBean extends BeanObject
{
  public static final String CMDSetup="setup";
  public static final String ORDSave="Save";

  public static final char ProviderGoogle='G';
  public static final char ProviderMicrosoft='M';
  public static final char ProviderPCloud='P';

  private static final String PropSetup="Setup";
  private static final String KeyPending="pending";

  private String gclient=null;
  private String gsecret=null;
  private String mclient=null;
  private String msecret=null;
  private String pclient=null;
  private String psecret=null;

  public void setGclient(String newValue){gclient=newValue;}
  public void setGsecret(String newValue){gsecret=newValue;}
  public void setMclient(String newValue){mclient=newValue;}
  public void setMsecret(String newValue){msecret=newValue;}
  public void setPclient(String newValue){pclient=newValue;}
  public void setPsecret(String newValue){psecret=newValue;}

  public String getGclient(){return gclient==null?"":gclient;}
  public String getMclient(){return mclient==null?"":mclient;}
  public String getPclient(){return pclient==null?"":pclient;}
  public boolean hasGsecret(){return gsecret!=null && gsecret.length()>0;}
  public boolean hasMsecret(){return msecret!=null && msecret.length()>0;}
  public boolean hasPsecret(){return psecret!=null && psecret.length()>0;}

  /**
   * True when no login provider has been configured yet, which is what puts the
   * server into first-run mode instead of showing the login button.
   */
  public static boolean isUnconfigured()
  {
    JSONArray auth=Portal.getProperties(AuthOpenIDBean.PropAuth);
    return auth==null || auth.length()==0;
  }

  /**
   * Marks that credentials were just saved, so the next successful login is the
   * person who configured this server and may claim admin. Without this one-shot
   * flag any stranger who happened to log in first would become admin.
   */
  public static void markPending()
  {
    JSONObject setup=new JSONObject();
    setup.put(KeyPending,true);
    Portal.setProperty(PropSetup,setup);
  }

  public static boolean consumePending()
  {
    JSONObject setup=Portal.getProperty(PropSetup);
    if (setup==null)return false;
    if (!setup.optBoolean(KeyPending,false))return false;
    setup.put(KeyPending,false);
    Portal.setProperty(PropSetup,setup);
    return true;
  }

  /**
   * The redirect URIs the operator has to register with the provider. Shown on the
   * setup screen because a mismatch here is the most common first-run failure.
   */
  public String getRedirectBase()
  {
    return publicBase();
  }

  public boolean isFirstRun()
  {
    return isUnconfigured();
  }

  protected void processData()
  {
    // Public only while unconfigured. Once a provider exists this is an admin screen.
    if (!isUnconfigured() && !isAdmin())
    {
      command=id.prasetya.vibrefy.Command.getCommand(CMDWelcome);
      return;
    }

    JSONArray auth=Portal.getProperties(AuthOpenIDBean.PropAuth);
    if (auth==null)auth=new JSONArray();

    if (ORDSave.equals(getOrder()))
    {
      boolean changed=false;
      if (gclient!=null && gclient.trim().length()>0)
      {
        applyProvider(auth,ProviderGoogle,gclient.trim(),gsecret);
        changed=true;
      }
      if (mclient!=null && mclient.trim().length()>0)
      {
        applyProvider(auth,ProviderMicrosoft,mclient.trim(),msecret);
        changed=true;
      }
      if (pclient!=null && pclient.trim().length()>0)
      {
        applyProvider(auth,ProviderPCloud,pclient.trim(),psecret);
        changed=true;
      }
      if (!changed)
      {
        message="Enter at least a Client ID to save.";
        return;
      }
      if (findProvider(auth,ProviderGoogle)==null)
      {
        message="Google sign-in must be configured before you can log in.";
        return;
      }
      boolean wasFirstRun=isUnconfigured();
      Portal.setProperties(AuthOpenIDBean.PropAuth,auth);
      // The provider list is cached for an hour; without this the new credentials
      // would not take effect until that cache expired.
      AuthOpenIDBean.resetConfig();
      JSONArray admins=Portal.getProperties(Portal.PropAdmin);
      if (wasFirstRun || admins==null || admins.length()==0)markPending();
      success=true;
      message=wasFirstRun?"Saved. Now sign in to finish setup - the account you sign in with becomes the administrator."
                        :"Sign-in settings saved.";
    }

    // Reload for display so the form always shows what is actually stored.
    JSONObject google=findProvider(auth,ProviderGoogle);
    JSONObject micro=findProvider(auth,ProviderMicrosoft);
    JSONObject pcloud=findProvider(auth,ProviderPCloud);
    gclient=google==null?null:google.optString(AuthOpenIDBean.PropAuthClientID);
    mclient=micro==null?null:micro.optString(AuthOpenIDBean.PropAuthClientID);
    pclient=pcloud==null?null:pcloud.optString(AuthOpenIDBean.PropAuthClientID);
    gsecret=(google!=null && google.optString(AuthOpenIDBean.PropAuthSecret).length()>0)?"set":null;
    msecret=(micro!=null && micro.optString(AuthOpenIDBean.PropAuthSecret).length()>0)?"set":null;
    psecret=(pcloud!=null && pcloud.optString(AuthOpenIDBean.PropAuthSecret).length()>0)?"set":null;
  }

  private JSONObject findProvider(JSONArray auth,char provider)
  {
    for (int i=0;i<auth.length();i++)
    {
      JSONObject entry=auth.getJSONObject(i);
      String code=entry.optString(AuthOpenIDBean.PropAuthProvider);
      if (code!=null && code.length()>0 && code.charAt(0)==provider)return entry;
    }
    return null;
  }

  private void applyProvider(JSONArray auth,char provider,String clientId,String secret)
  {
    JSONObject entry=findProvider(auth,provider);
    if (entry==null)
    {
      entry=new JSONObject();
      entry.put(AuthOpenIDBean.PropAuthProvider,String.valueOf(provider));
      entry.put(AuthOpenIDBean.PropAuthPrompUser,true);
      auth.put(entry);
    }
    entry.put(AuthOpenIDBean.PropAuthClientID,clientId);
    // A blank secret field means "leave the stored one alone", so the real secret
    // never has to be echoed back to the browser to survive an edit.
    if (secret!=null && secret.trim().length()>0)entry.put(AuthOpenIDBean.PropAuthSecret,secret.trim());
  }
}
