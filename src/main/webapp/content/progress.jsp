<%@page session="false"%><jsp:useBean id="bean" scope="request" class="id.prasetya.vibrefy.beans.ProgressBean" /><%
out.print(bean.getJson());
%>