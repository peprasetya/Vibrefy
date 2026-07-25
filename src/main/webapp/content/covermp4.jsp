<%@page import="java.io.*"%><jsp:useBean id="bean" scope="request" class="id.prasetya.vibrefy.beans.BrowseBean" /><%
try
{
  ByteArrayInputStream bis=bean.getCover();
  if (bis!=null)
  {
    bis.transferTo(response.getOutputStream());
    bis.close();
  }
} catch (Exception e) 
{
  e.printStackTrace(System.out);
}

%>