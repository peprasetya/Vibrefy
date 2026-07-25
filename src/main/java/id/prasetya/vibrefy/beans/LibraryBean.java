package id.prasetya.vibrefy.beans;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.json.*;

import id.prasetya.vibrefy.Portal;
import id.prasetya.vibrefy.data.LibraryItem;
import id.prasetya.vibrefy.data.PathMap;
import id.prasetya.vibrefy.tools.cloud.CloudConfig;


public class LibraryBean extends BeanObject
{
  public static final String CMDLibrary="library";
  public static final String ORDNew="New";
  public static final String ORDAdd="Add";
  public static final String ORDUpdate="Update";
  public static final String ORDDel="Delete";
  
  private String name=null;
  private String path=null;
  private List<String> accounts=null;
  
  JSONArray libraries=null;
  
  public void setName(String newValue)
  {
    name=newValue;
  }
  
  public void setPath(String newValue)
  {
    path=newValue;
  }
  
  public void setAccounts(String newValue)
  {
    this.accounts=Arrays.stream(newValue.split("[\\s,;]+")).map(String::trim).filter(s->!s.isEmpty()).collect(Collectors.toList());
  }
  
  public String getName(){return name;}
  public String getPath(){return path;}
  public String[] getAccountList(){return accounts==null?new String[0]:accounts.toArray(new String[0]);}

  protected void processData()
  {
    if (!isAdmin())return;
    libraries=Portal.getProperties(Portal.PropLibraries);
    if (libraries==null)libraries=new JSONArray();
    
    int fidx=-1;
    JSONObject clib=null;
    for (int i=0;i<libraries.length() && fidx==-1;i++)
    {
      JSONObject temp=libraries.getJSONObject(i);
      if (name!=null && temp.optString(Portal.LibraryName).equals(name))
      {
        fidx=i;
        clib=temp;
      } if (path!=null && temp.optString(Portal.LibraryPath).equals(path))
      {
        fidx=i;
        clib=temp;
      }
    }
    
    if (ORDAdd.equals(getOrder()))
    {
      if (fidx>-1)
      {
        message="Library with same name ("+name+") is already exists!";
        return;
      }
      // '~' is reserved for per-user cloud mounts in the URL's library slot.
      if (name!=null && name.startsWith(CloudConfig.CLOUDPREFIX))
      {
        message="Library name cannot start with "+CloudConfig.CLOUDPREFIX;
        return;
      }
      File cpath=new File(path);
      if (!(cpath.exists() && cpath.isDirectory()))
      {
        message="Path is not exists!";
        return;
      }
      clib=new JSONObject();
      clib.put(Portal.LibraryName,name);
      clib.put(Portal.LibraryPath,path);
      JSONArray accs=new JSONArray();
      if (accounts != null)accounts.forEach(accs::put);
      clib.put(Portal.LibraryAccounts,accs);
      libraries.put(clib);
      success=true;
      message="Library with ("+name+") is created.";
    } else if (fidx>-1)
    {
      if (ORDUpdate.equals(getOrder()))
      {
        File cpath=new File(path);
        if (!(cpath.exists() && cpath.isDirectory()))
        {
          message="Path is not exists!";
          return;
        }
        clib.put(Portal.LibraryPath,path);
        JSONArray accs=new JSONArray();
        if (accounts!=null)accounts.forEach(accs::put);
        clib.put(Portal.LibraryAccounts,accs);
        success=true;
        message="Library with ("+name+") is updated.";
      } else if (ORDDel.equals(getOrder()))
      {
        libraries.remove(fidx);
        success=true;
        message="Library with ("+name+") is deleted.";
        clib=null;
        fidx=-1;
        name=null;
        path=null;
      }
    } else if (getOrder()==null && name==null && path!=null)
    {
      PathMap pobj=new PathMap(path,session);
      path=pobj.getRealPath();    
    }
    if (success)Portal.setProperties(Portal.PropLibraries,libraries);
    if (clib!=null)
    {
      name=clib.optString(Portal.LibraryName);
      path=clib.optString(Portal.LibraryPath);
      JSONArray accs=clib.getJSONArray(Portal.LibraryAccounts);
      accounts=IntStream.range(0,accs.length()).mapToObj(accs::optString).filter(s -> s != null && !s.trim().isEmpty()).map(String::trim).collect(Collectors.toList());
    }
  }
  
  public LibraryItem[] getLibraries()
  {
    if (libraries==null || !isAdmin())return new LibraryItem[0];
    return IntStream.range(0,libraries.length()).mapToObj(libraries::getJSONObject).map(LibraryItem::new).toArray(LibraryItem[]::new);
   
  }

}
