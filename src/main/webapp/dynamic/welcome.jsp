<%@page import="id.prasetya.vibrefy.tools.Escape,id.prasetya.vibrefy.beans.SetupBean"%><jsp:useBean id="bean" scope="request" class="id.prasetya.vibrefy.beans.BeanObject" /><?xml version="1.0" ?>
<%boolean needSetup=SetupBean.isUnconfigured();%>
<ajax>
<replacehtml id="menu"></replacehtml>
<replacehtml id="panel">
<%
if (needSetup)
{
  // Nothing to sign in with yet - the setup screen takes over the first run.
} else if (bean.getAccount()==null)
{
%>
<style>
#logbuttons
{
text-align:center;
width:320px;
margin:auto;
margin-top:100px;
}
#logbuttons .button
{
width:200px;
padding:5px;
height:34px;
margin:auto;
cursor:pointer;
}
</style>
<div class="logbuttons" id="logbuttons">
<h1>Welcome to Vibrefy!</h1>
<h3>Let's Vibrate the Souls!!</h3>
<div class="button" login="G" method="openid"><div style="background-color:#4285f4;border:none;color:#fff;box-shadow:0 2px 4px 0 rgba(0,0,0,.25);box-sizing:border-box;"><div style="border:1px solid transparent;height:100%;width:100%;"><div style="padding:8px;float:left;color:#fff;background-color:#fff;"><svg height="18px" viewBox="0 0 480 480"><path fill="#EA4335" d="M240 95c35 0 67 12 92 36l69-69C359 24 304 0 240 0 146 0 65 54 26 132l80 62C124 137 177 95 240 95z"></path><path fill="#4285F4" d="M470 246c0-16-2-31-4-46H240v90h129c-6 30-23 55-48 72l77 60c45-42 71-104 71-177z"></path><path fill="#FBBC05" d="M106 286c-5-15-8-30-8-46s3-31 8-46l-80-62C9 165 0 201 0 240c0 39 9 75 26 108l80-62z"></path><path fill="#34A853" d="M240 480c65 0 119-21 159-58l-77-60c-22 15-49 23-82 23-63 0-116-42-135-99l-80 62C65 426 146 480 240 480z"></path></svg></div><span style="font-size:16px;color:#fff;display:inline-block;padding:8px;">Login with Google</span></div></div></div>
</div>
<%
} else
{
%>
<%=Escape.html(bean.getAccount())%>
<%
}
%>
</replacehtml>
<script id="pageScript">
<%
if (needSetup)
{
%>
makeRequest('<%=SetupBean.CMDSetup%>','');
<%
} else if (bean.getAccount()==null)
{
%>
var loginwin;
setMessageListener(function(e) 
{
 if (e.data=="checkLogin")
 {
  if (loginwin)
  {
   loginwin.close();
   loginwin=false;
  }
  makeRequest('menu','');
 }
});
var logins=document.getElementById('logbuttons').getElementsByTagName("div");
for (var i=0;i<logins.length;i++)
{
 var prov=logins[i].getAttribute('login');
 if (prov)logins[i].addEventListener('click',function(event)
 {
  var top=screen.height/2-355;
  var left=screen.width/2-300;
  loginwin=window.open('/auth'+event.currentTarget.getAttribute('method')+'?provider='+event.currentTarget.getAttribute('login'),'Vibrefy Login','toolbar=no,location=no,directories=no,status=no,menubar=no,scrollbars=no,resizable=no,copyhistory=no,width=600,height=710,top='+top+',left='+left);
 },false);
}
<%
} else
{
  %>
  history.pushState({url:"home"},"","/home");
  makeRequest('menu','');<%
}
%>

</script>
<command>initMenu</command>
</ajax>
 