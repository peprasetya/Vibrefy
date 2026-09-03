package id.prasetya.vibrefy.data;

import org.json.JSONObject;

public class FileItem
{
  public static final short TYPEVOLUMES=1;
  public static final short TYPEDIRECTORY=2;
  public static final short TYPEFILE=3;
  public static final short TYPELINK=4;
  // Cloud mounts, shown as their own section at the browse root. Only appears there,
  // where there are no folders/files to interleave, so its sort order is simply "after
  // local libraries".
  public static final short TYPECLOUD=5;
  
  private String name=null;
  private String target=null;
  private String path=null;
  private short type=0;
  private int timeProgress=0;
  private long sortTime=0;
  // When the underlying file was last changed. Distinct from sortTime, which is when
  // the user last watched it - a file can be recently added and never played, or long
  // untouched and watched this morning. Set after construction because only the file
  // listings know it; 0 means the source could not tell us.
  private long modified=0;

  public String getName() {return name;}
  public String getTarget() {return target;}
  public String getPath() {return path;}
  public short getType() {return type;}
  public int getTimeProgress() {return timeProgress;}
  public long getSortTime() {return sortTime;}
  public long getModified() {return modified;}
  public void setModified(long newValue) {modified=newValue;}


  public FileItem(String name,String target,String path,short type)
  {
    this.name=name;
    this.target=target;
    this.path=path;
    this.type=type;
  }
  
  public FileItem(String name,String target,String path,short type,int timeProgress,long sortTime)
  {
    this.name=name;
    this.target=target;
    this.path=path;
    this.type=type;
    this.timeProgress=timeProgress;
    this.sortTime=sortTime;
  }

  /**
   * The wire shape shared by the browse listing, the watch list, and any client that
   * renders them. Kept here rather than in each bean so the two listings cannot drift.
   * "type" is the TYPE* constants above, which the web client already keys on.
   */
  public JSONObject toJSON()
  {
    JSONObject item=new JSONObject();
    item.put("name",name==null?"":name);
    item.put("path",path==null?"":path);
    item.put("type",type);
    item.put("time",timeProgress);
    item.put("modified",modified);
    return item;
  }
}
