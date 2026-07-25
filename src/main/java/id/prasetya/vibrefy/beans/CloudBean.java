package id.prasetya.vibrefy.beans;

import org.json.JSONArray;
import org.json.JSONObject;

import id.prasetya.vibrefy.SessionTracker;
import id.prasetya.vibrefy.tools.cloud.CloudConfig;
import id.prasetya.vibrefy.tools.cloud.CloudProvider;
import id.prasetya.vibrefy.tools.cloud.FileLuProvider;
import id.prasetya.vibrefy.tools.cloud.PCloudProvider;

/**
 * Manages the current user's cloud mounts. Everything here is per-user and lives in
 * that user's encrypted .vibed file, never in the shared library list.
 */
public class CloudBean extends BeanObject
{
  public static final String CMDCloud="cloud";
  public static final String ORDAdd="Add";
  public static final String ORDDelete="Delete";

  private String label=null;
  private String kind=null;
  private String apikey=null;
  private String token=null;

  public void setLabel(String newValue){label=newValue;}
  public void setKind(String newValue){kind=newValue;}
  public void setApikey(String newValue){apikey=newValue;}
  public void setToken(String newValue){token=newValue;}

  public String getLabel(){return label==null?"":label;}

  protected void processData()
  {
    if (account==null)return;

    if (ORDDelete.equals(getOrder()) && label!=null)
    {
      JSONArray clouds=CloudConfig.getClouds(session);
      for (int i=0;i<clouds.length();i++)
      {
        if (!label.equals(clouds.getJSONObject(i).optString(CloudConfig.KeyLabel)))continue;
        clouds.remove(i);
        CloudConfig.invalidate(session,label);
        SessionTracker.flush(session);
        success=true;
        message="Disconnected "+label+".";
        break;
      }
      if (!success)message="No cloud storage named "+label+".";
      return;
    }

    if (ORDAdd.equals(getOrder()))
    {
      // Key/token providers are added here. Google and OneDrive go through the cloudauth
      // OAuth round trip instead; pCloud can go either way (OAuth button or this token).
      char kindCode=(kind==null || kind.length()==0)?' ':kind.charAt(0);
      if (kindCode!=FileLuProvider.KIND && kindCode!=PCloudProvider.KIND)
      {
        message="Use the connect button for that provider.";
        return;
      }
      if (!CloudConfig.isValidLabel(label))
      {
        message="Name must be 1-40 characters, letters, digits, dot, dash or underscore.";
        return;
      }
      if (CloudConfig.findCloud(session,label)!=null)
      {
        message="You already have a cloud storage named "+label+".";
        return;
      }
      if (kindCode==FileLuProvider.KIND)addFileLu();
      else addPCloudToken();
    }
  }

  private void addFileLu()
  {
    if (apikey==null || apikey.trim().length()==0)
    {
      message="An API key is required.";
      return;
    }
    JSONObject entry=new JSONObject();
    entry.put(CloudConfig.KeyKind,String.valueOf(FileLuProvider.KIND));
    entry.put(CloudConfig.KeyLabel,label);
    entry.put(CloudConfig.KeyApiKey,apikey.trim());
    entry.put(CloudConfig.KeyRoot,"0");
    entry.put(CloudConfig.KeyRootName,label);
    entry.put(CloudConfig.KeyAdded,System.currentTimeMillis());
    store(entry);
  }

  /**
   * Connects pCloud from a directly-entered access token (e.g. pulled from an rclone
   * config), so no admin OAuth client is needed. The region is auto-detected from the
   * token, which also confirms the token is valid before storing anything.
   */
  private void addPCloudToken()
  {
    if (token==null || token.trim().length()==0)
    {
      message="An access token is required.";
      return;
    }
    String host=PCloudProvider.detectHost(token.trim());
    if (host==null)
    {
      message="That access token was not accepted by pCloud.";
      return;
    }
    JSONObject entry=new JSONObject();
    entry.put(CloudConfig.KeyKind,String.valueOf(PCloudProvider.KIND));
    entry.put(CloudConfig.KeyLabel,label);
    entry.put(CloudConfig.KeyAccess,token.trim());
    entry.put(CloudConfig.KeyHost,host);
    entry.put(CloudConfig.KeyRoot,"0");
    entry.put(CloudConfig.KeyRootName,CloudConfig.kindName(PCloudProvider.KIND));
    entry.put(CloudConfig.KeyAdded,System.currentTimeMillis());
    store(entry);
    // Label the mount with the account email now that we know the region.
    try
    {
      CloudProvider provider=CloudConfig.getProvider(session,label);
      if (provider!=null)
      {
        entry.put(CloudConfig.KeyAccount,provider.getAccountName());
        SessionTracker.flush(session);
      }
    } catch (Exception e)
    {
      System.out.println("ERRCLOUDPCLOUDTOKEN:"+e.getMessage());
    }
  }

  private void store(JSONObject entry)
  {
    CloudConfig.getClouds(session).put(entry);
    CloudConfig.invalidate(session,label);
    SessionTracker.flush(session);
    success=true;
    message="Connected "+label+".";
  }

  public JSONArray getClouds()
  {
    return CloudConfig.getClouds(session);
  }

  public String kindName(String kindCode)
  {
    if (kindCode==null || kindCode.length()==0)return "Cloud";
    return CloudConfig.kindName(kindCode.charAt(0));
  }

  /** True when a provider can be linked, i.e. its app credentials are configured. */
  public boolean canLink(char kindCode)
  {
    return CloudConfig.findAuth(kindCode)!=null;
  }
}
