<%@page import="id.prasetya.vibrefy.data.FileItem,id.prasetya.vibrefy.beans.StreamBean,id.prasetya.vibrefy.beans.SubtitleBean,id.prasetya.vibrefy.tools.Escape,java.util.*" pageEncoding="UTF-8"%><jsp:useBean id="bean" scope="request" class="id.prasetya.vibrefy.beans.BrowseBean" /><?xml version="1.0" encoding="UTF-8" ?>
<ajax>
<% 
if (bean.getMessage()==null)
{
%>
<replacehtml id="panel">
<div class="dirheader">
<% 
StringBuilder fpath=new StringBuilder();
if (bean.getPrePath()==null)
{
  %>Libraries:<% 
} else
{
  String[] subPaths=bean.getSubPaths();
  if (subPaths==null)
  {
    subPaths=new String[1];
    subPaths[0]="/";
  }
  fpath.append(bean.getPrePath());
  %><span data-url="/" data-type="1" tabstop="0">Library</span>/<span data-url="<%=Escape.html(fpath.toString())%>" data-type="1" tabindex="0"><%=Escape.html(bean.getPrePath())%></span>/<%
  for (String path:subPaths)
  {
    fpath.append("/"+path);
    %><span data-url="<%=Escape.html(fpath.toString())%>" data-type="2" tabindex="0"><%=Escape.html(path)%></span>/<%
  }
  if (subPaths.length>0 && bean.isAdmin() && !bean.isCloudPath())
  {
    %><button id="addlibbtn" path="/files/<%=Escape.html(fpath.toString())%>">🗂 ️Add Library</button><%
  }
}
%></div><% 
FileItem[] files=bean.listFiles();
short type=-1;
int ni=files.length;
List<FileItem> videos = new ArrayList<>();
for (int i=0;i<ni;)
{
  FileItem file=files[i];
  String title="Unknown";
  String tagClass="";
  switch (file.getType())
  {
    case FileItem.TYPEVOLUMES:
      title="Libraries";
      tagClass="library";
      break;
    case FileItem.TYPECLOUD:
      title="Cloud";
      tagClass="cloud";
      break;
    case FileItem.TYPEDIRECTORY:
      title="";
      tagClass="folder";
      break;
    case FileItem.TYPEFILE:
      title="Media";
      tagClass="file";
      if (file.getPath() != null)
      {
        String fname=file.getPath().toLowerCase();
        if (fname.endsWith(".mp4") || fname.endsWith(".m4v"))
        {
          tagClass="video";
          videos.add(file);
        }
      }
      break;
  }
  if (type!=file.getType())
  {
    if (file.getType()==FileItem.TYPEFILE)
    {
      %><div id="fileswatch" path="<%=fpath%>"></div><%
    }
    
    
    type=file.getType();
    %><div class="groupShow"><div><%=title%></div><ul>
    <%
  }
  if (!tagClass.equals(""))
  {
    %><li data-url="<%=Escape.html(file.getPath()!=null?file.getPath():"")%>" data-type="<%=file.getType()%>" <%if (type!=FileItem.TYPEFILE)out.print("data-time=\""+file.getTimeProgress()+"\"");%> class="<%=tagClass%>" tabindex="0"><%
    if ("video".equals(tagClass))
    {
      %><img src="/<%=Escape.html(bean.CMDThumbMP4+"/"+file.getPath())%>" loading="lazy" title="<%=Escape.html(file.getName())%>"><%
    }
    %><span><%=Escape.html(file.getName())%></span></li><%
  }
  if ((++i<ni && type!=files[i].getType()) || i==ni)
  {
    %></ul></div>
    <%
  }
}

if (videos.size()>1)
{
  Collections.shuffle(videos);
  int count = Math.min(videos.size(), 20);
%><div class="groupShow"><div>Random Suggestions</div><ul>
<%
  for (int k = 0; k < count; k++)
  {
    FileItem file = videos.get(k);
%>  <li data-url="<%=Escape.html(file.getPath())%>" data-type="<%=file.getType()%>" <%if (file.getTimeProgress()>0)out.print("data-time=\""+file.getTimeProgress()+"\"");%> class="video" tabindex="0">
      <img src="/<%=Escape.html(bean.CMDThumbMP4+"/"+file.getPath())%>" loading="lazy" title="<%=Escape.html(file.getName())%>">
      <span><%=Escape.html(file.getName())%></span>
    </li>
<%
  }
%>
</ul></div><div>&nbsp;<br>&nbsp;<br>&nbsp;</div>
<%
}
%><style>
#showCover>div>img
{
width:min(80vw,calc(50vh * var(--img-aspect,1.77778)));;
height:min(50vh,calc(80vw / var(--img-aspect,1.77778)));;
}
</style>
<dialog id="showCover"><div></div><form method="dialog" class="buttonContainer"><button onclick="playTarget()" tabindex="0">▶️ Play</button><button tabindex="0">⏏️ Close</button></form></dialog>
</replacehtml>
<script id="pageScript">
var showCover=document.getElementById("showCover");

function itemClick(event)
{
	if (event.currentTarget && event.currentTarget.dataset && event.currentTarget.dataset.url && event.currentTarget.dataset.type)
	{
		var type=event.currentTarget.dataset.type;
		if (type==1 || type==2 || type==5)makeRequest('<%=bean.CMDBrowse%>/'+event.currentTarget.dataset.url,null,true);
		else if (type==3)
		{
			showCover.firstElementChild.innerHTML='';
			var img=document.createElement("img");
			img.src='/<%=bean.CMDCoverMP4%>/'+event.currentTarget.dataset.url;
			img.addEventListener('load',function(){img.style.setProperty('--img-aspect',img.naturalWidth/img.naturalHeight);})
			showCover.firstElementChild.appendChild(img);
			targetMedia='/<%=StreamBean.CMDStream%>/<%=bean.getSessionId()%>/'+event.currentTarget.dataset.url;
			targetSubtitle='/<%=SubtitleBean.CMDSubtitle%>/<%=bean.getSessionId()%>/'+event.currentTarget.dataset.url;
			targetSubtitleList='/<%=SubtitleBean.CMDSubtitleList%>/<%=bean.getSessionId()%>/'+event.currentTarget.dataset.url;
			targetTime=event.currentTarget.dataset.time?event.currentTarget.dataset.time:0;
			mediaFile='<%=bean.CMDBrowse%>/'+event.currentTarget.dataset.url
			showCover.showModal();
		}
	}
}

function imageLoad(event)
{
	event.target.setAttribute("cload","true");
}

function imageError(event)
{
	event.target.setAttribute("cerr","true");
}

function addLibrary(path)
{
	makeRequest('library','path='+encodeURIComponent(path),false);
}

var groupShow=document.getElementsByClassName("groupShow");
for (var i=0,ni=groupShow.length;i<ni;i++)
{
	var lis=groupShow[i].getElementsByTagName("li");
	for (var j=0,nj=lis.length;j<nj;j++)
	{
		lis[j].addEventListener("click",itemClick);
		var ims=lis[j].getElementsByTagName("img");
		if (ims.length>0)for (var k=0,nk=ims.length;k<nk;k++)
		{
			ims[k].addEventListener("load",imageLoad);
			ims[k].addEventListener("error",imageError);
		}
	}
}
var headers=document.getElementsByClassName("dirheader");
for (var i=0,ni=headers.length;i<ni;i++)
{
	var lis=headers[i].getElementsByTagName("span");
	for (var j=0,nj=lis.length;j<nj;j++)
	{
		lis[j].addEventListener("click",itemClick);
	}
}
var addbtn=document.getElementById("addlibbtn");
if (addbtn)addbtn.addEventListener("click",function(event)
{
	makeRequest('library','path='+encodeURIComponent(event.currentTarget.getAttribute('path')),false);
});
var fileWatch=document.getElementById("fileswatch");
if (fileWatch)
{
	fileWatch.addEventListener("click",function(event)
	{
		var li=event.target.closest("li");
		if (li)itemClick({currentTarget:li});
	});
	fileWatch.addEventListener("load",imageLoad,true);
	fileWatch.addEventListener("error",imageError,true);
	makeRequest('fileswatch/'+fileWatch.getAttribute('path'),'',false);
}
//if (fileWatch)makeRequest('fileswatch','path='+encodeURIComponent(fileWatch.getAttribute('path')),false);
</script>
<% 
} else
{
%><command>historyback</command><% 
}
%> 
</ajax>