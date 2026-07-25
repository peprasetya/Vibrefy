<%@page import="id.prasetya.vibrefy.beans.*,id.prasetya.vibrefy.tools.Escape" pageEncoding="UTF-8" %><jsp:useBean id="bean" scope="request" class="id.prasetya.vibrefy.beans.BeanObject" /><?xml version="1.0" encoding="UTF-8" ?>
<ajax><%String account=bean.getAccount();%>
<replacehtml id="menu">
 <ul>
  <li class="group"><span><%=Escape.html(account)%></span></li>
  <li page="<%=LogoutBean.COMMAND%>" tabindex="0"><span>🛅 Logout</span></li>
  <li page="<%=BeanObject.CMDHome%>" tabindex="0"><span>🏠 Home</span></li>
  <li page="<%=BrowseBean.CMDBrowse%>" tabindex="0"><span>🗄️ Browse</span></li>
  <li page="<%=CloudBean.CMDCloud%>" tabindex="0"><span>☁️ Cloud</span></li>
<%
if (bean.isAdmin())
{
%>
  <li class="group"><span>Admin</span></li>
  <li page="library" tabindex="0"><span>🗃 Library</span></li>
  <li page="<%=SetupBean.CMDSetup%>" tabindex="0"><span>⚙️ Setup</span></li>
<%
}
%>
 </ul>
</replacehtml>
<script id="pageScript">
clearPanel(true);
if (history.state) stateChange(history);
else
{
 var target=window.location.pathname;
 if (target.startsWith('/logout') || target.startsWith('/menu') || target=='/')target='/home';
 makeRequest(target.substring(1),'',false);
}</script>
<command>initMenu</command>
</ajax>
 