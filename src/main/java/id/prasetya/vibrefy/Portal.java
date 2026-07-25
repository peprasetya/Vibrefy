package id.prasetya.vibrefy;

import java.beans.*;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;

import org.json.*;

import id.prasetya.vibrefy.beans.BeanObject;
import jakarta.servlet.*;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.*;



@WebFilter(urlPatterns = "/*") 
public class Portal extends HttpServlet implements Filter
{
  /**
   * 
   */
  private static final long serialVersionUID=6970029988797653197L;
  private static final String jsonFile="vibrefy.json";
  private static final String version="0.2";
  
  public static final String SessionEmail="LoginEmail";
  public static final String SessionAccountID="LoginID";
  public static final String SessionData="LoginData";
  public static final String SessionAdmin="Admin";
  public static final String MediaList="media";
  public static final String ID="id";
  public static final String Progress="progress";
  public static final String Update="last";
  public static final String PropAdmin="Administrators";
  public static final String PropLibraries="Libraries";
  public static final String LibraryName="name";
  public static final String LibraryPath="path";
  public static final String LibraryAccounts="accounts";
  public static final String ROOTFOLDER="root";
  public static final String MULTIPART_FORMDATA_TYPE = "multipart/form-data";
  public static final String DEFAULT_TYPE = "text/html; charset=utf-8";
  public static final String VIDEO_MP4_TYPE = "video/mp4";
  
  private FilterConfig filterConfig;
  private static JSONObject data=new JSONObject();
  private static final Object dataLock=new Object();



  public static JSONArray getProperties(String key)
  {
    synchronized (dataLock)
    {
      if (!data.has(key))return null;
      return data.getJSONArray(key);
    }
  }

  public static void setProperties(String key, JSONArray value)
  {
    synchronized (dataLock)
    {
      data.put(key,value);
      saveData();
    }
  }

  public static JSONObject getProperty(String key)
  {
    synchronized (dataLock)
    {
      if (!data.has(key))return null;
      return data.getJSONObject(key);
    }
  }

  public static void setProperty(String key, JSONObject value)
  {
    synchronized (dataLock)
    {
      data.put(key,value);
      saveData();
    }
  }

  public static void reloadProperty()
  {
    synchronized (dataLock)
    {
      try
      {
        Reader reader=new FileReader(jsonFile);
        data=new JSONObject(new JSONTokener(reader));
        reader.close();
      }catch (IOException e)
      {
        e.printStackTrace(System.out);
      }
    }
  }

  private static void saveData()
  {
    synchronized (dataLock)
    {
      try
      {
        FileWriter file=new FileWriter(jsonFile);
        file.write(data.toString(2));
        file.flush();
        file.close();
      }catch (IOException e)
      {
        System.out.println(e.getMessage());
      }
    }
  }
  
  
  @Override
  public void init(FilterConfig filterConfig) throws ServletException
  {
    this.filterConfig = filterConfig;
    filterConfig.getServletContext().setSessionTimeout(240);
    System.out.println("Vibrefy Initialize, version: "+version);
  }

  private void filter(ServletRequest request,ServletResponse response,FilterChain filterChain)
  {
    try
    {
      filterChain.doFilter(request,response);
    } catch (ServletException sx)
    {
      System.out.println("Filter-ServletExcetpion");
      filterConfig.getServletContext().log(sx.getMessage());
    } catch (IOException iox)
    {
      System.out.println("Filter-IOExcetpion");
      filterConfig.getServletContext().log(iox.getMessage());
    }
  }

  @Override
  public void doFilter(ServletRequest request,ServletResponse response,FilterChain filterChain) throws IOException,ServletException
  {
    try
    {
      HttpServletRequest req=(HttpServletRequest)request;
      String path=req.getRequestURI().substring(req.getContextPath().length());
      boolean filter=false;
      if (path.equals("/"))
      {
        
      } else if (path.endsWith(".css"))filter=true;
      else if (path.endsWith(".js"))filter=true;
      else if (path.endsWith(".txt"))filter=true;
      else if (path.endsWith(".ico"))filter=true;
      else if (path.startsWith("/images/"))filter=true;
      else if (path.startsWith("/resources/"))filter=true;
      else if (path.startsWith("/.well-known/"))filter=true;
      else if (path.equals("/auth"))filter=true;
      else if (path.startsWith("/socket/"))
      {
        req.getSession(true);
        filter=true;
      }
      if (filter)
      {
        filter(request,response,filterChain);
      } else 
      {
        String host=req.getHeader("Host");
        if (host!=null)
        {
          if (host.contains(":"))host=host.substring(0,host.indexOf(":"));
          doRequest(host,req,(HttpServletResponse)response,path);
        }
      }
    } catch (ClassCastException e)
    {
      System.out.println("Filter-ClassCastException");
      System.out.println(e.getMessage());
      filter(request,response,filterChain);
    }
  }

  public static void setProperties(Object bean, ServletRequest request)
  {
    Enumeration<?> e = request.getParameterNames();
    while (e.hasMoreElements())
    {
      String name=(String)e.nextElement();
      try
      {
        BeanInfo info=Introspector.getBeanInfo(bean.getClass());
        if (info==null)continue;
        Method method = null;
        Class<?> type = null;
        PropertyDescriptor pd[]=info.getPropertyDescriptors();
        for (int i=0;(i<pd.length)&&method==null;i++)
        {
          if (!pd[i].getName().equals(name)) continue;
          method = pd[i].getWriteMethod();
          type = pd[i].getPropertyType();
        }
        if (method==null)continue;
        if (type.isArray())
        {
          Class<?> t = type.getComponentType();
          String[] values=request.getParameterValues(name);
          if ((values!=null) && (t.equals(String.class))) method.invoke(bean, new Object[] {values});
        } else
        {
          String value=request.getParameter(name);
          if (value != null) if ((!value.equals("")) && type.equals(String.class))method.invoke(bean, new Object[] {value});
        }
      } catch (Exception ex) {}
    }
  }

  //@SuppressWarnings("rawtypes")
  private void doRequest(String host,HttpServletRequest request,HttpServletResponse response,String path)
  {
    /*
    String ipAddress=request.getRemoteAddr();
    String addIp=request.getHeader("X-Forwarded-For");
    if (addIp!=null && addIp.length()>0)
    {
      if (addIp.contains(ipAddress))
        ipAddress=addIp;
      else
        ipAddress+=","+addIp;
    }
    */
    /*
    Enumeration<String> uhm=request.getHeaderNames();
    while (uhm.hasMoreElements())
    {
      String key=uhm.nextElement();
      System.out.println("header |"+key+"|"+request.getHeader(key)+"|");
    }
    */
    HttpSession session=null;
    String account=null;
    String command=BeanObject.CMDMenu;
    if (path.length()>1)
    {
      command=path.substring(1);
      int sp=command.indexOf('/');
      if (sp>0)command=command.substring(0,sp);
    }
    Command cmd=null;
    BeanObject bean=null;
    //String message=null;
    do
    {
      if (cmd==null)cmd=Command.getCommand(command);
      if (cmd!=null) 
      {
        command=cmd.getCommand();
        session=request.getSession(cmd.isCreateSession());
        if (session!=null)
        {
          SessionTracker.sessionCheck(session);
          account=(String)session.getAttribute(SessionEmail);
        }
        if (cmd.getAccessType()>0 && account==null)
        {
          command=BeanObject.CMDWelcome;
          cmd=Command.getCommand(command);
        }
        try
        {
          Class<?>[] parameterType = null;
          //System.out.println("Create Command:"+path);
          bean=(BeanObject)cmd.getBean().getDeclaredConstructor(parameterType).newInstance();
        }catch (InstantiationException|IllegalAccessException | IllegalArgumentException | InvocationTargetException | NoSuchMethodException | SecurityException e)
        {
          bean=new BeanObject();
        }
      }
      if (bean==null) bean=new BeanObject();
      bean.setBeanObjects(request,cmd,session,account);
      setProperties(bean,request);
      
      String ct=request.getContentType();
      if (ct!=null && ct.startsWith(MULTIPART_FORMDATA_TYPE))
      {
       
      }
      
      /*
      if (ServletFileUpload.isMultipartContent(request))
      {
        ServletFileUpload upload = new ServletFileUpload();
        FileItemIterator iter;
        try
        {
          iter=upload.getItemIterator(request);
          while (iter.hasNext()) 
          {
            FileItemStream item = iter.next();
            if (item.isFormField()) 
            {
              //String name = item.getFieldName();
              //String value=Streams.asString(item.openStream());
              //Process Normal Form Field Here
            } else 
            { 
              ItemUpload currentItem=new ItemUpload(item.getName(),item.getFieldName(),item.getContentType());
              Streams.copy(item.openStream(),currentItem,true);
              bean.addItemUpload(currentItem);            
            }
          }
        }catch (FileUploadException|IOException e)
        {
          e.printStackTrace();
        }
      }
      */
      
      bean.processRequest();
      cmd=bean.getCommand();
      //System.out.println("Post Command:"+cmd.getCommand());
      account=bean.getAccount();
      //rights=bean.getRights();
    } while(!cmd.getCommand().equals(command));

    response.setContentType(bean.getContentType());
    if (VIDEO_MP4_TYPE.equals(bean.getContentType()))response.setHeader("Accept-Ranges","bytes");
    if (bean.isPartial())response.setStatus(HttpServletResponse.SC_PARTIAL_CONTENT);
    if (bean.getContentRange()!=null)response.setHeader("Content-Range",bean.getContentRange());
    if (bean.getContentLength()>=0)response.setHeader("Content-Length",String.valueOf(bean.getContentLength()));
    if (cmd.isPreventCache())
    {
      response.setHeader("Cache-Control","private, no-cache, no-store, must-revalidate, max-age = 0");
      response.setHeader("Pragma","no-cache");
    } else
    {
      response.setHeader("Cache-Control","private, max-age = 14400");
    }
    
    if (bean.getRedirect()!=null)try
    {
      response.sendRedirect(bean.getRedirect());
      return;
    }catch (IOException e){}
    
    request.setAttribute("bean",bean);
    String page="/maintemplate.jsp";  
    boolean ignoreExcept=false;
    if (bean.isAjaxCall())
    {
      page="/dynamic/"+command+".jsp";
      response.setContentType("text/xml; charset=UTF-8");
      response.setCharacterEncoding("UTF-8");
    } else if (!DEFAULT_TYPE.equals(bean.getContentType()))
    {
      if (bean.getContentDisposition()!=null)
        response.setHeader("Content-Disposition","attachment; filename=\""+bean.getContentDisposition()+"\"");
      page="/content/"+command+".jsp";
      ignoreExcept=true;
    }
    RequestDispatcher rd=request.getRequestDispatcher(page);
    try
    {
      rd.include(request,response);
    }catch (EOFException e)
    {
      System.out.println("EOFException Test *************************************************************");
    }catch (ServletException|IOException e)
    {
      if (e instanceof IOException && ((IOException)e).getCause() instanceof java.util.concurrent.TimeoutException)ignoreExcept=true;
      if (ignoreExcept)
      {
        //System.out.println(e.getMessage());
        return;
      }
      System.out.println("Portal NetTools trapped Exception");
      System.out.println("IP Address: "+request.getRemoteAddr());
      e.printStackTrace(System.out);
    } catch (Exception e)
    {
      System.out.println("Portal Catch Exception.");
      e.printStackTrace(System.out);
    }

  }

}
