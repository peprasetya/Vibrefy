<%@page session="false"%><jsp:useBean id="bean" scope="request" class="id.prasetya.vibrefy.beans.MediaListBean" /><%
out.print(bean.getMediaList());
%>
