<%@page import="id.prasetya.vibrefy.data.FileItem,id.prasetya.vibrefy.beans.StreamBean,id.prasetya.vibrefy.tools.Escape,java.util.*" pageEncoding="UTF-8"%><jsp:useBean id="bean" scope="request" class="id.prasetya.vibrefy.beans.BrowseBean" /><?xml version="1.0" encoding="UTF-8" ?>
<ajax>
<% 
if (bean.getMessage()==null)
{
%>
<replacehtml id="fileswatch"><%
  FileItem[] watchedFiles=bean.getWatchedFiles();
  if (watchedFiles.length>0)
  {
    %><div class="groupShow"><div>Watching</div><ul>
    <%
    for (int k=0,nk=watchedFiles.length;k<nk;k++)
    {
      FileItem watch = watchedFiles[k];
      String title2=watch.getName();
    %>  <li data-url="<%=Escape.html(watch.getPath())%>" data-type="<%=watch.getType()%>" <%if (watch.getTimeProgress()>0)out.print("data-time=\""+watch.getTimeProgress()+"\"");%> class="video" tabindex="0">
        <img src="/<%=Escape.html(bean.CMDThumbMP4+"/"+watch.getPath())%>" loading="lazy" title="<%=Escape.html(title2)%>">
        <span><%=Escape.html(title2)%></span></li>
    <%
    }
    %>
    </ul></div>
    <%
  }
}
%></replacehtml>
</ajax>