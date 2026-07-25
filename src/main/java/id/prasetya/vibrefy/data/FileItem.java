package id.prasetya.vibrefy.data;

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
  
  public String getName() {return name;}
  public String getTarget() {return target;}
  public String getPath() {return path;}
  public short getType() {return type;}
  public int getTimeProgress() {return timeProgress;}
  public long getSortTime() {return sortTime;}


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
}
