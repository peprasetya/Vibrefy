package id.prasetya.vibrefy;

import java.io.*;
import java.nio.charset.StandardCharsets;

import id.prasetya.vibrefy.beans.BeanObject;
import id.prasetya.vibrefy.tools.Escape;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.annotation.WebFilter;

@WebFilter(urlPatterns = "/dynamic/*",dispatcherTypes = {DispatcherType.INCLUDE})
public class AjaxModifier implements Filter
{

  public AjaxModifier()
  {
  }

  public void destroy()
  {
  }

  public void doFilter(ServletRequest request,ServletResponse response,FilterChain chain) throws IOException,ServletException
  {
    PrintWriter out=response.getWriter();
    CharArrayWriter caw=new CharArrayWriter();
    BeanObject bean=(BeanObject)request.getAttribute("bean");
    String message="";
    AjaxWrapper wrapper=new AjaxWrapper((HttpServletResponse)response);
    try
    {
      chain.doFilter(request,wrapper);
      caw.write(new String(wrapper.getData(),StandardCharsets.UTF_8));
    } catch (ServletException e)
    {
      if (e.getMessage().startsWith("JSP file ") && e.getMessage().endsWith(" not found"))
      {
        caw.write("<?xml version=\"1.0\" encoding=\"UTF-8\" ?><ajax></ajax>");
      } else throw e;
    } catch (FileNotFoundException e)
    {
      caw.write("<?xml version=\"1.0\" encoding=\"UTF-8\" ?><ajax></ajax>");
    }
    if (bean!=null && bean.getMessage()!=null && !"".equals(bean.getMessage()))
    {
      message="<message>"+Escape.html(bean.getMessage())+"</message>";
    }
    out.write(caw.toString().replaceAll("<ajax>","<ajax>"+message));
    out.close();
  }

  public void init(FilterConfig fConfig) throws ServletException
  {
  }

}