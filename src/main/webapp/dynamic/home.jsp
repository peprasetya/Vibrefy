<%@page import="id.prasetya.vibrefy.data.FileItem,id.prasetya.vibrefy.beans.*" pageEncoding="UTF-8"%><jsp:useBean id="bean" scope="request" class="id.prasetya.vibrefy.beans.HomeBean" /><?xml version="1.0" encoding="UTF-8" ?>
<ajax>
<replacehtml id="panel">
<div id="homepartwatch"></div>
<div id="homepartnew"></div>
<div id="homepartrandom"></div>
<style>
#showCover>div>img
{
width:min(80vw,calc(50vh * var(--img-aspect,1.77778)));;
height:min(50vh,calc(80vw / var(--img-aspect,1.77778)));;
}
</style>
<dialog id="showCover"><div></div><form method="dialog" class="buttonContainer"><button onclick="playTarget()" tabindex="0">▶️ Continue</button><button tabindex="0">⏏️ Close</button></form></dialog>
</replacehtml>
<script id="pageScript">
var showCover=document.getElementById("showCover");

function itemClick(event)
{
	if (event.currentTarget && event.currentTarget.dataset && event.currentTarget.dataset.url && event.currentTarget.dataset.type)
	{
		var type=event.currentTarget.dataset.type;
		if (type==3)
		{
			showCover.firstElementChild.innerHTML='';
			var img=document.createElement("img");
			img.src='/<%=BrowseBean.CMDCoverMP4%>/'+encodePath(event.currentTarget.dataset.url);
			img.addEventListener('load',function(){img.style.setProperty('--img-aspect',img.naturalWidth/img.naturalHeight);})
			showCover.firstElementChild.appendChild(img);
			targetMedia='/<%=StreamBean.CMDStream%>/<%=bean.getSessionId()%>/'+event.currentTarget.dataset.url;
			targetSubtitle='/<%=SubtitleBean.CMDSubtitle%>/<%=bean.getSessionId()%>/'+event.currentTarget.dataset.url;
			targetSubtitleList='/<%=SubtitleBean.CMDSubtitleList%>/<%=bean.getSessionId()%>/'+event.currentTarget.dataset.url;
			targetTime=event.currentTarget.dataset.time?event.currentTarget.dataset.time:0;
			mediaFile='<%=BrowseBean.CMDBrowse%>/'+event.currentTarget.dataset.url
			showCover.showModal();
		}
	}
}

function imageLoad()
{
	event.currentTarget.setAttribute("cload","true");
}

function imageError()
{
	event.currentTarget.setAttribute("cerr","true");
}

function initList()
{
	var groupShow=document.getElementsByClassName("groupShow");
	for (var i=0,ni=groupShow.length;i<ni;i++)
	{
		if (!groupShow[i].hasAttribute('uninit'))continue;
		groupShow[i].removeAttribute('uninit');
		var lis=groupShow[i].getElementsByTagName("li");
		for (var j=0,nj=lis.length;j<nj;j++)
		{
			lis[j].addEventListener("click",itemClick);
			var ims=lis[j].getElementsByTagName("img");
			if (ims.length>0)for (var k=0,nk=ims.length;k<nk;k++)
			{
				ims[k].setAttribute("loading","lazy");
				ims[k].addEventListener("load",imageLoad);
				ims[k].addEventListener("error",imageError);
				ims[k].src=ims[k].getAttribute("lsrc");
				ims[k].removeAttribute("lsrc");
				ims[k].title=ims[k].nextElementSibling.textContent;
			}
		}
	}
}
makeRequest('homepart','order=<%=bean.ORDWatch%>');
</script>
</ajax>
 