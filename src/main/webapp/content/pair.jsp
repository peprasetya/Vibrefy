<%@page session="false"%><jsp:useBean id="bean" scope="request" class="id.prasetya.vibrefy.beans.PairBean" /><%
out.print(bean.getJson());
%>