package id.prasetya.vibrefy.data;

import java.util.stream.IntStream;

import org.json.*;

import id.prasetya.vibrefy.Portal;

public class LibraryItem
{
  private String name=null;
  private String path=null;
  private String[] accounts=null;
  
  public String getName() {return name;}
  public String getPath() {return path;}
  public String[] getAccounts() {return accounts;}

  public LibraryItem(String name,String path,String[] accounts)
  {
    this.name=name;
    this.path=path;
    this.accounts=accounts;
  }

  public LibraryItem(JSONObject item)
  {
    this.name=item.optString(Portal.LibraryName,null);
    this.path=item.optString(Portal.LibraryPath,null);
    if (item.has(Portal.LibraryAccounts))
    {
      JSONArray accs=item.getJSONArray(Portal.LibraryAccounts);
      this.accounts=IntStream.range(0,accs.length()).mapToObj(accs::optString).filter(s -> s != null && !s.trim().isEmpty()).map(String::trim).toArray(String[]::new);
    }
  }
}
