package id.prasetya.vibrefy.beans;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

import id.prasetya.vibrefy.Command;
import id.prasetya.vibrefy.Portal;
import id.prasetya.vibrefy.data.PathMap;
import jakarta.servlet.http.*;

public class BeanObject
{
  public static final String CMDWelcome="welcome";
  public static final String CMDMenu="menu";
  public static final String CMDHome="home";
  
  protected HttpServletRequest request=null;
  protected Command command=null;
  protected String redirect=null;
  protected String contentDisposition=null;
  protected String contentType=Portal.DEFAULT_TYPE;
  protected boolean html=true;
  private String order=null;
  private boolean ajaxCall=false;
  protected boolean success=false;
  protected HttpSession session=null;
  protected String account=null;
  protected boolean partial=false;
  protected String contentRange=null;
  protected long contentLength=-1;
  protected PathMap path=null;
  
  
  public Command getCommand(){return command;}
  public String getRedirect(){return redirect;}
  public String getContentDisposition(){return contentDisposition;}
  public String getContentType(){return contentType;}
  public boolean isPartial(){return partial;}
  public String getContentRange(){return contentRange;}
  public long getContentLength(){return contentLength;}
  public boolean isAjaxCall(){return ajaxCall;}
  public boolean isSuccess(){return success;}
  public boolean isHtml(){return html;}
  public String getAccount(){return account;}
  protected String message=null;

  //protected ArrayList<ItemUpload> uploads=null;

  public void setBeanObjects(HttpServletRequest request,Command command,HttpSession session,String account)
  {
    this.request=request;
    this.command=command;
    this.session=session;
    this.account=account;
  }
  
  public void setAjaxcall(String newValue)
  {
    ajaxCall="1".equals(newValue);
    if (isAjaxCall()) contentType="text/xml";
  }
  
  public void setOrder(String newValue)
  {
    order=newValue;
  }
  
  public String getOrder() {return order;}

  /**
   * True when this caller wants JSON instead of the HTML shell or the Ajax/XML UI. A bean
   * that answers JSON must set contentType before its own guards, the way MediaListBean
   * does, so a path that fails to resolve still answers as JSON.
   */
  protected boolean wantsJson()
  {
    return request!=null && Portal.wantsJson(request);
  }

  protected void processData()
  {
  }

  public void processRequest()
  {
    path=new PathMap(URLDecoder.decode(request.getRequestURI().substring(request.getContextPath().length()),StandardCharsets.UTF_8),session);
    try
    {
      processData();
    } catch (Exception e)
    {
      e.printStackTrace(System.out);
    }
  }

  public String getMessage()
  {
    return message;
  }
  
  protected void logActivity(boolean success,String message)
  {
    this.success=success;
  }
  
  public boolean isAdmin()
  {
    if (session==null)return false;
    Boolean admin=(Boolean)session.getAttribute(Portal.SessionAdmin);
    return admin==null?false:admin;
  }
  
  public String getSessionId()
  {
    return session.getId();
  }

  /**
   * The externally visible base URL, ending in '/'. Behind a TLS-terminating proxy
   * (Cloudflare, nginx) the request arrives as plain http on an internal host, so the
   * forwarded headers are consulted first - otherwise an OAuth redirect_uri built from
   * getRequestURL would be http://internal and never match what was registered.
   */
  protected String publicBase()
  {
    String scheme=header("X-Forwarded-Proto");
    if (scheme==null)scheme=request.getScheme();
    String host=header("X-Forwarded-Host");
    if (host==null)host=header("Host");
    if (host==null)
    {
      host=request.getServerName();
      int port=request.getServerPort();
      if (!(port==80 && "http".equals(scheme)) && !(port==443 && "https".equals(scheme)))host=host+":"+port;
    }
    // A forwarded host list is comma-separated; the first entry is the client-facing one.
    int comma=host.indexOf(',');
    if (comma>0)host=host.substring(0,comma).trim();
    return scheme+"://"+host+"/";
  }

  private String header(String name)
  {
    String value=request.getHeader(name);
    if (value==null || value.trim().length()==0)return null;
    return value.trim();
  }
  

  
/*
  public void addItemUpload(ItemUpload upload)
  {
    if (uploads==null)uploads=new ArrayList<ItemUpload>();
    uploads.add(upload);
  }
*/
}
