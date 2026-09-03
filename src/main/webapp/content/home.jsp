<%@page session="false"%><jsp:useBean id="bean" scope="request" class="id.prasetya.vibrefy.beans.HomeBean" /><%
out.print(bean.getJson());
%>