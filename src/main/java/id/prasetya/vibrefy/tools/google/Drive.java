package id.prasetya.vibrefy.tools.google;

public class Drive
{
  static final String APIDrive="https://www.googleapis.com/drive/v3/";
  
  private Token token=null;

  private int pageSize=1000;
  private String fileMeta="iconLink,thumbnailLink,id,description,fileExtension,mimeType,name,webContentLink";
  
  public Drive(Token token)
  {
    this.token=token;
  }
  
  public void setPageSize(int pageSize)
  {
    this.pageSize=pageSize;
  }
  
  public void setFileMeta(String fileMeta)
  {
    this.fileMeta=fileMeta;
  }
  
  public String about()
  {
    return token.httpGet(APIDrive+"about?fields=*");
  }
  
  public String list(String query,String pageToken)
  {
    return token.httpGet(APIDrive+"files?pageSize="+pageSize+"&fields=nextPageToken,files("+fileMeta+")&supportsAllDrives=true&includeItemsFromAllDrives=true"+(pageToken==null?"":"&pageToken="+pageToken)+(query==null?"":"&q="+Token.encode(query)));
  }
  
  public String getMetadata(String fileId)
  {
    return token.httpGet(APIDrive+"files/"+fileId+"?fields=*&supportsAllDrives=true&includeItemsFromAllDrives=true");
  }


}
