<%@page import="id.prasetya.vibrefy.data.LibraryItem,id.prasetya.vibrefy.tools.Escape" pageEncoding="UTF-8" %><jsp:useBean id="bean" scope="request" class="id.prasetya.vibrefy.beans.LibraryBean" /><?xml version="1.0" encoding="UTF-8" ?>
<ajax>
<replacehtml id="panel">
<%
String[] accounts;

if (bean.getName()!=null)
{

%><h3>Modify Library: <%=Escape.html(bean.getName())%></h3>
<form id="mainform" onsubmit="return false;">
<input type="hidden" name="name" value="<%=Escape.html(bean.getName())%>">
<div class="formtable">
 <div>
  <div>Path</div>
  <div><input type="text" name="path" value="<%=Escape.html(bean.getPath())%>"></div>
 </div>
 <div>
  <div>Allowed<br>Emails</div>
  <%accounts=bean.getAccountList();%>
  <div><textarea cols="30" rows="5" name="accounts"><%for (String account:accounts) out.println(Escape.html(account));%></textarea></div>
 </div>
 <div>
  <div></div>
  <div><button value="<%=bean.ORDDel%>" onclick="submitForm(event)">🗑️</button><button value="<%=bean.ORDUpdate%>" onclick="submitForm(event)">💾</button></div>
 </div>
 
</div>
</form>
<%
} else if (bean.getPath()!=null)
{
  accounts=bean.getAccountList();
%><h3>New Library</h3>
<form id="mainform" onsubmit="return false;">
<div class="formtable">
 <div>
  <div>Name</div>
  <div><input name="name" value=""></div>
 </div>
 <div>
  <div>Path</div>
  <div><input name="path" value="<%=Escape.html(bean.getPath())%>"></div>
 </div>
 <div>
  <div>Allowed<br>Emails</div>
  <div><textarea cols="30" rows="5" name="accounts"><%if (accounts!=null)for (String account:accounts) out.println(Escape.html(account));%></textarea></div>
 </div>
 <div>
  <div></div>
  <div><button value="<%=bean.ORDAdd%>" onclick="submitForm(event)">💾</button></div>
 </div>
 
</div>
</form>
<%
} else
{
%><h3>Libraries Vibe</h3>
<div class="listSelect">
<ul>
<%
  LibraryItem[] libs=bean.getLibraries();
  for (LibraryItem item:libs)
  {
  %><li onclick="openLibrary(event)"><span><%=Escape.html(item.getName())%></span></li><%
  }
  %></ul></div><%
}

%>
</replacehtml>
<script id="pageScript">
function submitForm(event)
{
 let form=event.currentTarget.form;
 makeRequest('library','order='+event.currentTarget.value+'&name='+encodeURIComponent(form.name.value)+'&path='+encodeURIComponent(form.path.value)+'&accounts='+encodeURIComponent(form.accounts.value),false);
}
function openLibrary(event)
{
  console.log(event.currentTarget.textContent);
 makeRequest('library','name='+encodeURIComponent(event.currentTarget.textContent),false);
}
</script>
</ajax>
 