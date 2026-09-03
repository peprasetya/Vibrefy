<%@page session="false"%><jsp:useBean id="bean" scope="request" class="id.prasetya.vibrefy.beans.BrowseBean" /><%
out.print(bean.getJson());
%>