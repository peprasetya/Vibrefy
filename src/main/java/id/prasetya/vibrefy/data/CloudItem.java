package id.prasetya.vibrefy.data;

/**
 * One entry in a cloud folder, in provider-neutral form. Deliberately separate from
 * FileItem: FileItem is the view model the JSPs render and carries watch progress,
 * while this only describes what the provider told us about the object.
 */
public class CloudItem
{
  private String id=null;
  private String name=null;
  private boolean folder=false;
  private long size=0;
  private long modified=0;

  public String getId(){return id;}
  public String getName(){return name;}
  public boolean isFolder(){return folder;}
  public long getSize(){return size;}
  public long getModified(){return modified;}

  public CloudItem(String id,String name,boolean folder,long size,long modified)
  {
    this.id=id;
    this.name=name;
    this.folder=folder;
    this.size=size;
    this.modified=modified;
  }
}
