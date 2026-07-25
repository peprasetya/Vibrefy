<%@page import="id.prasetya.vibrefy.beans.CloudBean,id.prasetya.vibrefy.beans.CloudAuthBean,id.prasetya.vibrefy.tools.cloud.CloudConfig,id.prasetya.vibrefy.tools.cloud.DriveProvider,id.prasetya.vibrefy.tools.cloud.GraphProvider,id.prasetya.vibrefy.tools.cloud.FileLuProvider,id.prasetya.vibrefy.tools.cloud.PCloudProvider,id.prasetya.vibrefy.tools.Escape,org.json.JSONArray,org.json.JSONObject" pageEncoding="UTF-8" %><jsp:useBean id="bean" scope="request" class="id.prasetya.vibrefy.beans.CloudBean" /><?xml version="1.0" encoding="UTF-8" ?>
<ajax>
<replacehtml id="panel">
<h3>Connected Storage</h3>
<%
JSONArray clouds=bean.getClouds();
if (clouds.length()>0)
{
%><div class="listSelect">
<ul>
<%
  for (int i=0;i<clouds.length();i++)
  {
    JSONObject entry=clouds.getJSONObject(i);
    String label=entry.optString(CloudConfig.KeyLabel);
    String who=entry.optString(CloudConfig.KeyAccount);
%>  <li class="cloudrow"><span><b><%=Escape.html(label)%></b> &mdash; <%=Escape.html(bean.kindName(entry.optString(CloudConfig.KeyKind)))%><%if (who!=null && who.length()>0){%> (<%=Escape.html(who)%>)<%}%></span><button class="rowdelete" data-label="<%=Escape.html(label)%>" onclick="removeCloud(event)">🗑️ Remove</button></li>
<%
  }
%></ul></div>
<%
} else
{
%><p>No cloud storage connected yet.</p>
<%
}
%>
<h3>Add Storage</h3>
<form id="addform" onsubmit="return false;">
<div class="formtable">
 <div>
  <div>Provider</div>
  <div><select name="provider" onchange="onProviderChange()">
   <option value="">— select —</option>
<%
if (bean.canLink(DriveProvider.KIND))
{
%>   <option value="G">🟢 Google Drive (sign in)</option>
<%
}
if (bean.canLink(GraphProvider.KIND))
{
%>   <option value="M">🔵 OneDrive (sign in)</option>
<%
}
if (bean.canLink(PCloudProvider.KIND))
{
%>   <option value="Poauth">🟣 pCloud (sign in)</option>
<%
}
%>   <option value="Ptoken">🟣 pCloud (access token)</option>
   <option value="F">🟠 FileLu (API key)</option>
  </select></div>
 </div>
 <div id="addname" style="display:none;">
  <div>Name</div>
  <div><input type="text" name="label" value="" placeholder="a name for this connection"></div>
 </div>
 <div id="addsecret" style="display:none;">
  <div id="secretlabel">Secret</div>
  <div><input type="password" name="secret" value=""></div>
 </div>
 <div id="addbtnrow" style="display:none;">
  <div></div>
  <div><button id="addbtn" onclick="doConnect(event)">💾 Connect</button></div>
 </div>
</div>
</form>
<p id="addhelp"><small>&nbsp;</small></p>
<%
if (!bean.canLink(DriveProvider.KIND) || !bean.canLink(GraphProvider.KIND) || !bean.canLink(PCloudProvider.KIND))
{
%><p><small>Google, OneDrive and pCloud sign-in appear only after an administrator enters their Client ID and Secret on the Setup page. FileLu and pCloud access token need no server setup.</small></p>
<%
}
%>
</replacehtml>
<script id="pageScript">
var cloudwin;
setMessageListener(function(e)
{
 if (e.data=="checkCloud")
 {
  if (cloudwin)
  {
   cloudwin.close();
   cloudwin=false;
  }
  makeRequest('<%=CloudBean.CMDCloud%>','',false);
 }
});

// Each provider: whether it needs a pasted secret, that field's label, and a hint.
var providers={
 'G':{secret:false,help:'Sign in to your Google account in the popup.'},
 'M':{secret:false,help:'Sign in to your Microsoft account in the popup.'},
 'Poauth':{secret:false,help:'Sign in to your pCloud account in the popup.'},
 'Ptoken':{secret:true,label:'Access Token',help:'Paste a pCloud access token, for example from an rclone config. The region is detected automatically.'},
 'F':{secret:true,label:'API Key',help:'Paste your FileLu API key from your account settings.'}
};

function onProviderChange()
{
 var form=document.getElementById('addform');
 var p=providers[form.provider.value];
 var show=!!p;
 document.getElementById('addname').style.display=show?'':'none';
 document.getElementById('addbtnrow').style.display=show?'':'none';
 document.getElementById('addsecret').style.display=(show&&p.secret)?'':'none';
 document.getElementById('addhelp').firstChild.textContent=show?p.help:' ';
 if (show&&p.secret)document.getElementById('secretlabel').textContent=p.label;
 var btn=document.getElementById('addbtn');
 if (show)btn.textContent=p.secret?'💾 Connect':'💾 Sign in';
}

function doConnect(event)
{
 var form=document.getElementById('addform');
 var sel=form.provider.value;
 var p=providers[sel];
 if (!p)return;
 var label=form.label.value;
 if (!label)
 {
  alert('Give this connection a name first.');
  return;
 }
 if (p.secret)
 {
  var secret=form.secret.value;
  if (!secret)
  {
   alert('Enter the '+p.label+' first.');
   return;
  }
  if (sel=='F')makeRequest('<%=CloudBean.CMDCloud%>','order=<%=CloudBean.ORDAdd%>&kind=<%=FileLuProvider.KIND%>&label='+encodeURIComponent(label)+'&apikey='+encodeURIComponent(secret),false);
  else makeRequest('<%=CloudBean.CMDCloud%>','order=<%=CloudBean.ORDAdd%>&kind=<%=PCloudProvider.KIND%>&label='+encodeURIComponent(label)+'&token='+encodeURIComponent(secret),false);
  return;
 }
 // OAuth providers: map the select value to the provider kind and open the popup.
 var kind=(sel=='Poauth')?'<%=PCloudProvider.KIND%>':sel;
 var top=screen.height/2-355;
 var left=screen.width/2-300;
 cloudwin=window.open('/<%=CloudAuthBean.COMMAND%>?kind='+encodeURIComponent(kind)+'&label='+encodeURIComponent(label),'Vibrefy Cloud','toolbar=no,location=no,directories=no,status=no,menubar=no,scrollbars=no,resizable=no,copyhistory=no,width=600,height=710,top='+top+',left='+left);
}

function removeCloud(event)
{
 var label=event.currentTarget.getAttribute('data-label');
 if (!confirm('Disconnect '+label+'? Your files stay in the cloud, only the link is removed.'))return;
 makeRequest('<%=CloudBean.CMDCloud%>','order=<%=CloudBean.ORDDelete%>&label='+encodeURIComponent(label),false);
}
</script>
</ajax>
