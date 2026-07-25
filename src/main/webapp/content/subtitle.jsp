<%@page import="java.io.*"%><jsp:useBean id="bean" scope="request" class="id.prasetya.vibrefy.beans.SubtitleBean" /><%
String vtt = bean.getVttContent();
if (vtt != null) {
    out.print(vtt);
}
%>
