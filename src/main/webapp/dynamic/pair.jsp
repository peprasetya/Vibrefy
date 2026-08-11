<%@page import="id.prasetya.vibrefy.beans.BeanObject,id.prasetya.vibrefy.beans.PairBean,id.prasetya.vibrefy.tools.Escape" pageEncoding="UTF-8" %><jsp:useBean id="bean" scope="request" class="id.prasetya.vibrefy.beans.PairBean" /><?xml version="1.0" encoding="UTF-8" ?>
<ajax>
<%
String mode=bean.getMode();
if (PairBean.MODEPoll.equals(mode))
{
  // A poll tick, or a claim that just succeeded. This branch must never replace the
  // panel: doing so would re-run the page script and start a second timer on every tick.
  String state=bean.getPollState();
  if (PairBean.POLLDone.equals(state))
  {
%><command>home</command><%
  } else if (PairBean.POLLExpired.equals(state))
  {
%><replacehtml id="pair-state">This code has expired. Ask for a new one.</replacehtml>
<script id="pair-stop">clearTimer();</script><%
  } else
  {
%><replacehtml id="pair-state">Waiting for approval&hellip; <%=bean.getRemaining()%>s</replacehtml><%
  }
} else
{
  // This command is reachable signed out by design, so Portal never rewrites it to
  // welcome the way it does every other page. That means a session which expired while
  // the menu was on screen would leave the menu sitting there - clear it here instead.
  boolean signedOut=(bean.getAccount()==null);
  if (signedOut)
  {
%><replacehtml id="menu"></replacehtml>
<%
  }
%>
<replacehtml id="panel">
<style>
#pair-panel
{
max-width:460px;
margin:auto;
margin-top:60px;
text-align:center;
}
#pair-code
{
font-size:44px;
font-weight:bold;
letter-spacing:6px;
padding:16px;
margin:16px 0;
}
#pair-panel input[type=text]
{
font-size:28px;
letter-spacing:4px;
text-align:center;
width:100%;
padding:8px;
box-sizing:border-box;
}
#pair-panel .device
{
text-align:left;
padding:12px;
margin:12px 0;
}
#pair-panel .device small
{
word-break:break-all;
}
#pair-panel .sep
{
margin-top:36px;
}
</style>
<div id="pair-panel">
<%
  if (PairBean.MODEWait.equals(mode))
  {
%><h2>Sign in from another device</h2>
<div id="pair-code"><%=Escape.html(bean.getDisplayCode())%></div>
<p>On a device where you are already signed in, open <b>&#128241; Devices</b> and enter this code.</p>
<div id="pair-state">Waiting for approval&hellip; <%=bean.getRemaining()%>s</div>
<p class="sep"><button onclick="makeRequest('<%=BeanObject.CMDWelcome%>','',true)">&#8617;&#65039; Back to sign in</button></p>
<%
  } else if (PairBean.MODEOffer.equals(mode))
  {
%><h2>Sign in on another device</h2>
<div id="pair-code"><%=Escape.html(bean.getDisplayCode())%></div>
<p>On the other device, open Vibrefy and choose <b>Sign in from another device</b>, then enter this code. It is valid for three minutes and can be used once.</p>
<p><button onclick="makeRequest('<%=PairBean.CMDPair%>','',false)">&#8617;&#65039; Done</button></p>
<%
  } else if (PairBean.MODEConfirm.equals(mode))
  {
%><h2>Approve this device?</h2>
<div class="device">
 <div><b><%=Escape.html(bean.getDeviceName())%></b></div>
 <div>Address: <%=Escape.html(bean.getDeviceAddress())%></div>
 <div><small><%=Escape.html(bean.getDeviceAgent())%></small></div>
</div>
<p>Approve only if this is a device you are holding right now. It will be signed in as you.</p>
<p><button onclick="approveDevice(event)" data-code="<%=Escape.html(bean.getCode())%>">&#9989; Approve</button>
<button onclick="makeRequest('<%=PairBean.CMDPair%>','',false)">&#10060; Cancel</button></p>
<%
  } else if (PairBean.MODEHome.equals(mode))
  {
%><h2>&#128241; Devices</h2>
<h3>Approve a device</h3>
<p>Enter the code shown on the other device.</p>
<form id="pair-form" onsubmit="return false;">
 <input type="text" name="code" value="" inputmode="numeric" pattern="[0-9]*" autocomplete="off" maxlength="11" placeholder="0000 0000">
 <p><button onclick="findDevice(event)">&#128269; Continue</button></p>
</form>
<div class="sep">
<h3>Sign in on another device</h3>
<p>Get a code to type into a device that cannot sign in on its own.</p>
<p><button onclick="makeRequest('<%=PairBean.CMDPair%>','order=<%=PairBean.ORDOffer%>',false)">&#128273; Show a code</button></p>
</div>
<%
  } else
  {
%><h2>Sign in from another device</h2>
<p>Use a device where you are already signed in to sign this one in. Nothing is typed here unless you want it to be.</p>
<p><button onclick="makeRequest('<%=PairBean.CMDPair%>','order=<%=PairBean.ORDRequest%>',false)">&#128421;&#65039; Show a code on this device</button></p>
<div class="sep">
<h3>Or enter a code</h3>
<p>If the other device is showing a code, type it here.</p>
<form id="pair-form" onsubmit="return false;">
 <input type="text" name="code" value="" inputmode="numeric" pattern="[0-9]*" autocomplete="off" maxlength="11" placeholder="0000 0000">
 <p><button onclick="claimCode(event)">&#128274; Sign in</button></p>
</form>
</div>
<p class="sep"><button onclick="makeRequest('<%=BeanObject.CMDWelcome%>','',true)">&#8617;&#65039; Back to sign in</button></p>
<%
  }
%>
</div>
</replacehtml>
<script id="pageScript">
clearTimer();
<%
  if (signedOut)
  {
%>
// Same reason the menu is cleared above: the player is a sibling of the panel and would
// otherwise keep playing over a page that is telling the user they are signed out.
if (video && video.src)video.removeAttribute('src');
setFullWindow(false);
<%
  }
%>

function pairCodeValue()
{
 var form=document.getElementById('pair-form');
 if (!form)return '';
 return form.code.value.replace(/[^0-9]/g,'');
}

function findDevice(event)
{
 var code=pairCodeValue();
 if (code.length<1)
 {
  alert('Enter the code shown on the other device.');
  return;
 }
 makeRequest('<%=PairBean.CMDPair%>','order=<%=PairBean.ORDFind%>&code='+encodeURIComponent(code),false);
}

function claimCode(event)
{
 var code=pairCodeValue();
 if (code.length<1)
 {
  alert('Enter the code shown on the other device.');
  return;
 }
 makeRequest('<%=PairBean.CMDPair%>','order=<%=PairBean.ORDClaim%>&code='+encodeURIComponent(code),false);
}

function approveDevice(event)
{
 var code=event.currentTarget.getAttribute('data-code');
 makeRequest('<%=PairBean.CMDPair%>','order=<%=PairBean.ORDApprove%>&code='+encodeURIComponent(code),false);
}
<%
  if (PairBean.MODEWait.equals(mode))
  {
%>
// The waiting device asks every few seconds whether it has been approved. Registered in
// intervalHandler so the menu's clearTimer() tears it down on navigation, and the poll
// response only ever replaces #pair-state, never this script's own panel.
var pairWaitCode='<%=Escape.html(bean.getCode())%>';
intervalHandler.push(setInterval(function()
{
 // Browser-back leaves the timer running with the waiting page already gone, and a
 // reply aimed at an element that no longer exists would throw. Stop instead.
 if (!document.getElementById('pair-state'))
 {
  clearTimer();
  return;
 }
 makeRequest('<%=PairBean.CMDPair%>','order=<%=PairBean.ORDPoll%>&code='+pairWaitCode,false);
},3000));
<%
  }
%>
</script>
<%
  // initMenu hides the menu button when the list it finds is empty, so an emptied menu
  // has to be re-initialised the same way welcome.jsp does it.
  if (signedOut)
  {
%><command>initMenu</command><%
  }
}
%>
</ajax>
