package id.prasetya.vibrefy;

import java.io.*;
import java.nio.charset.StandardCharsets;

import jakarta.servlet.http.*;

public class AjaxWrapper extends HttpServletResponseWrapper
{
  private CharArrayWriter output;

  public AjaxWrapper(HttpServletResponse response)
  {
    super(response);
    output=new CharArrayWriter();
  }

  public byte[] getData() 
  { 
    String data = output.toString();
    ByteArrayOutputStream os=new ByteArrayOutputStream();
    
    int start=0;
    int begin;
    int nchk;
    String ctag;
    String utag=null;
    try
    {
      ctag="replacehtml";begin=data.indexOf('<'+ctag+' ',start);
      if (begin>=0)utag=ctag;
      ctag="script";nchk=data.indexOf('<'+ctag+' ',start);
      if (nchk>=0 && ((begin>=0 && nchk<begin) || begin<0))
      {
        begin=nchk;
        utag=ctag;
      }
      while (begin>0 && utag!=null)
      {
        int end=data.indexOf(">",begin);
        os.write(data.substring(start,end+1).getBytes(StandardCharsets.UTF_8));
        start=data.indexOf("</"+utag+'>',end);
        os.write(("<![CDATA["+data.substring(end+1,start)+"]]>").getBytes(StandardCharsets.UTF_8));
        ctag="replacehtml";begin=data.indexOf('<'+ctag+' ',start);
        if (begin>=0)utag=ctag;
        ctag="script";nchk=data.indexOf('<'+ctag+' ',start);
        if (nchk>=0 && ((begin>=0 && nchk<begin) || begin<0))
        {
          begin=nchk;
          utag=ctag;
        }
      }
      os.write(data.substring(start).getBytes(StandardCharsets.UTF_8));
    }catch (IOException e){}
    return os.toByteArray(); 
  } 

  public PrintWriter getWriter()
  {
    return new PrintWriter(output);
  }

}