<%@page import="id.prasetya.vibrefy.data.FileItem,id.prasetya.vibrefy.beans.*,id.prasetya.vibrefy.tools.Escape" pageEncoding="UTF-8"%><jsp:useBean id="bean" scope="request" class="id.prasetya.vibrefy.beans.HomeBean" /><?xml version="1.0" encoding="UTF-8" ?>
<ajax>
<%
String etitle="";
FileItem[] files=new FileItem[0];
switch (bean.getOrder())
{
  case HomeBean.ORDWatch:
    files=bean.getResumable();
    etitle="Watching...";
    break;
  case HomeBean.ORDNew:
    etitle="New Titles";
    break;
  case HomeBean.ORDRandom:
    etitle="Random Selection";
    break;
}
%><replacehtml id="homepart<%=bean.getOrder()%>">
<%
short type=-1;
int ni=files.length;
for (int i=0;i<ni;)
{
  FileItem file=files[i];
  String tagClass="";
  switch (file.getType())
  {
    case FileItem.TYPEFILE:
      tagClass="file";
      String fname=file.getPath().toLowerCase();
      if (fname.endsWith(".mp4") || fname.endsWith(".m4v"))tagClass="video";
      break;
  }
  if (type!=file.getType())
  {
    type=file.getType();
    %><div class="groupShow" uninit><div><%=etitle%></div><ul>
    <%
  }
  %><li data-url="<%=Escape.html(file.getPath()!=null?file.getPath():"")%>" data-type="<%=file.getType()%>" data-time="<%=file.getTimeProgress()%>" class="<%=tagClass%>" tabindex="0"><%
  if ("video".equals(tagClass))
  {
    %><img lsrc="/<%=Escape.html(BrowseBean.CMDThumbMP4+"/"+file.getPath())%>"><%
  }
  %><span><%=Escape.html(file.getName())%></span></li><%
  if ((++i<ni && type!=files[i].getType()) || i==ni)
  {
    %></ul></div>
    <%
  }
}
%></replacehtml>
<command>initList</command>
</ajax>
 