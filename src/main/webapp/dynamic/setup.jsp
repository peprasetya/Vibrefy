<%@page import="id.prasetya.vibrefy.beans.SetupBean,id.prasetya.vibrefy.tools.Escape" pageEncoding="UTF-8" %><jsp:useBean id="bean" scope="request" class="id.prasetya.vibrefy.beans.SetupBean" /><?xml version="1.0" encoding="UTF-8" ?>
<ajax>
<replacehtml id="panel">
<%
String base=bean.getRedirectBase();
boolean firstRun=bean.isFirstRun();
if (firstRun)
{
%><h1>Welcome to Vibrefy!</h1>
<h3>Let's set up sign-in first.</h3>
<p>Vibrefy signs you in with your own Google account, so you need a Google OAuth client.
Create one at <b>console.cloud.google.com</b> &rarr; APIs &amp; Services &rarr; Credentials &rarr;
Create Credentials &rarr; OAuth client ID &rarr; Web application.</p>
<%
} else
{
%><h3>Sign-in Setup</h3>
<%
}
%>
<p>Register these exact <b>Authorised redirect URIs</b> with the provider:</p>
<ul>
 <li><code><%=Escape.html(base)%>authopenid</code> &mdash; used for both signing in and connecting cloud storage</li>
</ul>
<form id="mainform" onsubmit="return false;">
<div class="formtable">
 <div>
  <div>Google<br>Client ID</div>
  <div><input type="text" name="gclient" value="<%=Escape.html(bean.getGclient())%>"></div>
 </div>
 <div>
  <div>Google<br>Secret</div>
  <div><input type="password" name="gsecret" value="" placeholder="<%=bean.hasGsecret()?"stored - leave blank to keep":"required"%>"></div>
 </div>
<%
if (!firstRun)
{
%> <div>
  <div>Microsoft<br>Client ID</div>
  <div><input type="text" name="mclient" value="<%=Escape.html(bean.getMclient())%>"></div>
 </div>
 <div>
  <div>Microsoft<br>Secret</div>
  <div><input type="password" name="msecret" value="" placeholder="<%=bean.hasMsecret()?"stored - leave blank to keep":"optional"%>"></div>
 </div>
 <div>
  <div>pCloud<br>Client ID</div>
  <div><input type="text" name="pclient" value="<%=Escape.html(bean.getPclient())%>"></div>
 </div>
 <div>
  <div>pCloud<br>Secret</div>
  <div><input type="password" name="psecret" value="" placeholder="<%=bean.hasPsecret()?"stored - leave blank to keep":"optional"%>"></div>
 </div>
<%
}
%> <div>
  <div></div>
  <div><button value="<%=SetupBean.ORDSave%>" onclick="submitForm(event)">💾 Save</button></div>
 </div>
</div>
</form>
<%
if (firstRun)
{
%><div class="logbuttons" id="logbuttons" style="display:none;">
<div class="button" login="G" method="openid"><div style="background-color:#4285f4;border:none;color:#fff;box-shadow:0 2px 4px 0 rgba(0,0,0,.25);box-sizing:border-box;"><div style="border:1px solid transparent;height:100%;width:100%;"><div style="padding:8px;float:left;color:#fff;background-color:#fff;"><svg height="18px" viewBox="0 0 480 480"><path fill="#EA4335" d="M240 95c35 0 67 12 92 36l69-69C359 24 304 0 240 0 146 0 65 54 26 132l80 62C124 137 177 95 240 95z"></path><path fill="#4285F4" d="M470 246c0-16-2-31-4-46H240v90h129c-6 30-23 55-48 72l77 60c45-42 71-104 71-177z"></path><path fill="#FBBC05" d="M106 286c-5-15-8-30-8-46s3-31 8-46l-80-62C9 165 0 201 0 240c0 39 9 75 26 108l80-62z"></path><path fill="#34A853" d="M240 480c65 0 119-21 159-58l-77-60c-22 15-49 23-82 23-63 0-116-42-135-99l-80 62C65 426 146 480 240 480z"></path></svg></div><span style="font-size:16px;color:#fff;display:inline-block;padding:8px;">Sign in to finish setup</span></div></div></div>
</div>
<%
}
%>
</replacehtml>
<script id="pageScript">
function submitForm(event)
{
 var form=event.currentTarget.form;
 var data='order='+event.currentTarget.value;
 data+='&gclient='+encodeURIComponent(form.gclient.value);
 data+='&gsecret='+encodeURIComponent(form.gsecret.value);
 if (form.mclient)data+='&mclient='+encodeURIComponent(form.mclient.value);
 if (form.msecret)data+='&msecret='+encodeURIComponent(form.msecret.value);
 if (form.pclient)data+='&pclient='+encodeURIComponent(form.pclient.value);
 if (form.psecret)data+='&psecret='+encodeURIComponent(form.psecret.value);
 makeRequest('<%=SetupBean.CMDSetup%>',data,false);
}
<%
if (firstRun)
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
var setupLogin=document.getElementById('logbuttons');
if (setupLogin)
{
 // Only offer the sign-in test once credentials have actually been stored.
 if (<%=bean.isSuccess()?"true":"false"%>)setupLogin.style.display='';
 var logins=setupLogin.getElementsByTagName("div");
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
}
<%
}
%>
</script>
</ajax>
