package id.prasetya.vibrefy.beans;

import org.json.*;

import id.prasetya.vibrefy.SessionTracker;

public class ProgressBean extends BeanObject
{
  private static final int ClearThreshold=-10;
  public static final String CMDProgress="progress";
  
  private String media=null;
  private long playtime=0;
  
  public void setMedia(String newValue)
  {
    media=newValue;
  }
  
  public void setTime(String newValue)
  {
    try {playtime=Long.parseLong(newValue);}catch(NumberFormatException e){}
  }
  
  protected void processData()
  {
    if (session==null||session.getId()==null||media==null)return;
    JSONObject userData=SessionTracker.getSessionData(session);
    JSONArray progress=userData.optJSONArray(SessionTracker.DataProgress);
    if (progress==null) 
    {
      if (playtime<ClearThreshold)return;
      progress=new JSONArray();
      userData.put(SessionTracker.DataProgress,progress);
    }

    int foundIndex=-1;
    JSONObject mediaEntry=null;
    for (int i=0;i<progress.length();i++)
    {
      JSONObject item=progress.getJSONObject(i);
      if (media.equals(item.optString(SessionTracker.DataProgressFile)))
      {
        mediaEntry=item;
        foundIndex=i;
        break;
      }
    }
    if (playtime<-10)
    {
      if (foundIndex != -1) progress.remove(foundIndex);
      return;
    }
    if (mediaEntry==null)
    {
      mediaEntry = new JSONObject();
      mediaEntry.put(SessionTracker.DataProgressFile,media);
      progress.put(mediaEntry);
    }
    mediaEntry.put(SessionTracker.DataProgressTime, this.playtime);
    mediaEntry.put(SessionTracker.DataProgressUpdate, System.currentTimeMillis());
  }
}
