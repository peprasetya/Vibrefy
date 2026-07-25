

const MIN_SAVE_INTERVAL=10000; // 10 seconds
const MIN_PROGRESS_DELTA=5; //seconds
let lastSaveTime=0;
let lastSavedPosition=0;
let targetTime=0;
let lastDisplayedSecond=-1;
let checkWatch=false;
let menu,menucb,menubtn,panel,mediaFile,targetMedia,targetSubtitle,targetSubtitleList,video,progressBar,progressFill,currentTimeEl,totalDurationEl;
let playerMenus=[],subtitleBtn,subtitleMenu;

function makeRequest(url,data,savingState) 
{
  var httpRequest;

  if (window.XMLHttpRequest) 
  {
    httpRequest = new XMLHttpRequest();
    if (httpRequest.overrideMimeType) 
    {
      httpRequest.overrideMimeType('text/xml');
    }
  }

  if (!httpRequest) 
  {
    alert('Plase use modern browser!');
    return false;
  }
  httpRequest.onreadystatechange=function(){alertContents(httpRequest);};
  var saveState=savingState;
  if (history)
  {
   if (saveState && history.state && history.state.url && history.state.data) if (history.state.url==url && history.state.data==data)saveState=false;
   if (saveState)history.pushState({url:url,data:data},"","/"+url);
  }
  if(!menu)menu=document.getElementById('menu');
  if (menu)
  {
   var mItems=menu.getElementsByTagName('li');
   for (var i=0,ni=mItems.length;i<ni;i++)
   {
    var page=mItems[i].getAttribute('page');
    if (page)mItems[i].classList.toggle("select",page==url);
   }
  }
  if(!menucb)menucb=document.getElementById('menucb');
  if (menucb)menucb.checked=false;
  httpRequest.open('POST','/'+url+'?ajaxcall=1', true);
  httpRequest.setRequestHeader('Content-Type', 'application/x-www-form-urlencoded');
  httpRequest.send(data);
}

function ajaxSubmit(url,form,saveState)
{
  var eleme=form.elements;
  var data='';
  for (var i=0;i<eleme.length;i++)
  {
    var item=eleme[i];
    if (item.tagName=='INPUT')
    {
      if (item.type=='checkbox')
      {
        if (item.checked && item.name!='')data+=item.name+'='+encodeURIComponent(item.value)+'&';
      } else if (item.name!='')
        data+=item.name+'='+encodeURIComponent(item.value)+'&';
      if (item.type=='password')item.value='';
    } else if (item.tagName=='SELECT')
    {
      if (item.name!='')
        data+=item.name+'='+encodeURIComponent(item.value)+'&';
    } else if (item.tagName=='TEXTAREA')
    {
    	data+=item.name+'='+encodeURIComponent(item.value)+'&';
    }
  }
  makeRequest(url,data,saveState);
  return false;
}

function alertContents(httpRequest) 
{
  var statdiv=document.getElementById("logstat");
  if (httpRequest.readyState == 4) 
  {
    if (httpRequest.status == 200) 
    {
      if (statdiv) statdiv.innerHTML='';
      var xd=httpRequest.responseXML;
      var newMsgs=xd.getElementsByTagName('message');
      if (newMsgs.length>0)
      {
        var newMsg=newMsgs.item(0).firstChild.data;
        alert(newMsg);
      }

      var newHTMLs=xd.getElementsByTagName('replacehtml');
      if (newHTMLs.length>0)
      for (var i=0;i<newHTMLs.length;i++)//>
      {
        var item=newHTMLs.item(i);
        var replacedId=item.getAttribute('id');
        var tagChange=document.getElementById(replacedId);
        if (item.textContent)
          tagChange.innerHTML=item.textContent;
        else if (item.text)
          tagChange.innerHTML=item.text;
        else 
          tagChange.innerHTML="";
      }
      if (window.sr)window.sr.sync();
      var newCmds=xd.getElementsByTagName('command');
      if (newCmds.length>0)
      for (var i=0;i<newCmds.length;i++)
      {
       var item=newCmds.item(i).firstChild.data;
       if ('refresh'==item) window.location.href=window.location.href;
       if ('home'==item) window.location.href='/';
       if ('historyback'==item) window.history.back();
       else if ('initMenu'==item)
       {
    	 initMenu();
       } else if ('initList'==item && initList)initList();
      }
      var newScripts=xd.getElementsByTagName('script');
      if (newScripts.length>0)
      for (var i=0;i<newScripts.length;i++)
      {
       var item=newScripts.item(i);
       var scriptId=item.getAttribute('id');
       if (scriptId)
       {
        var scp=document.getElementById(scriptId);
        if (scp)scp.parentNode.removeChild(scp);
       }
       var scrp=document.createElement("script");
       if (scriptId)scrp.id=scriptId;
       var src=item.getAttribute('src');
       if (src) scrp.src=src;
       else scrp.innerHTML=newScripts.item(i).textContent;
       document.body.appendChild(scrp); 
      }
    } else 
    {
//    alert('There was a problem with the request.');
    }
  } else if (statdiv)
  {
    if (httpRequest.readyState == 0)
    {
      statdiv.innerHTML="Ready.";
    } else if (httpRequest.readyState == 1) 
    {
      statdiv.innerHTML="Connecting...";
    } else if (httpRequest.readyState == 2) 
    {
      statdiv.innerHTML="Connected.";
    } else if (httpRequest.readyState == 3) 
    {
      statdiv.innerHTML="Transfering...";
    }
  }
}

var timeoutHanler=[];
var intervalHandler=[];

function clearTimer()
{
 while (timeoutHanler.length>0)clearTimeout(timeoutHanler.pop());
 while (intervalHandler.length>0)clearInterval(intervalHandler.pop());
}

function toggleMenu()
{
 if (menu && menu.classList.contains("floatmenu"))
  menu.classList.toggle("close");
}

function showMenu(show)
{
 if (menu && menu.classList.contains("floatmenu"))
  menu.classList.toggle("close",!show);
}

function stateChange(event)
{
 makeRequest(event.state.url,event.state.data,false);
}

function clearPanel(loading)
{
 if(!panel)return;
 while (panel.firstChild)panel.removeChild(panel.lastChild);
 if (loading)panel.innerHTML='<div class="loadsign"><div></div>Loading...</div>';
}

let messageListener=false;

function setMessageListener(fnc)
{
 messageListener=fnc;
}

function formatTime(seconds)
{
 seconds=Math.floor(seconds);
 const h=Math.floor(seconds/3600);
 const m=Math.floor((seconds%3600)/60);
 const s=seconds%60;
 const formattedS=s.toString().padStart(2,'0');
 const formattedM=m.toString().padStart(2,'0');
 if (h>0)return `${h}:${formattedM}:${formattedS}`;
 return `${m}:${formattedS}`;
}

function saveProgress(timeSaved)
{
 if (video.mediaFile)makeRequest('progress','time='+timeSaved+'&media='+encodeURIComponent(mediaFile));
 if (checkWatch)
 {
  checkWatch=false;
  let fileWatch=document.getElementById("fileswatch");
  if (fileWatch)makeRequest('fileswatch/'+fileWatch.getAttribute('path'),'',false);

 }
}


// The menus live inside the player node, which Document PiP relocates into another
// document. Holding element references keeps them reachable wherever that node is.
function closePlayerMenus()
{
  for (var i=0,ni=playerMenus.length;i<ni;i++)playerMenus[i].classList.add('hidden');
}

function isPlayerMenuOpen()
{
  for (var i=0,ni=playerMenus.length;i<ni;i++)if (!playerMenus[i].classList.contains('hidden'))return true;
  return false;
}

function togglePlayerMenu(id)
{
  var menu=null;
  for (var i=0,ni=playerMenus.length;i<ni;i++)if (playerMenus[i].id===id)menu=playerMenus[i];
  if (!menu)return;
  var show=menu.classList.contains('hidden');
  closePlayerMenus();
  if (show)menu.classList.remove('hidden');
}

function isFullWindow()
{
  var player=document.getElementById('player');
  return player!=null && player.classList.contains('fullwindow');
}

function setFullWindow(on)
{
  var player=document.getElementById('player');
  if (!player)return;
  if (on)player.classList.add('fullwindow');
  else player.classList.remove('fullwindow');
}

function selectSubtitle(index)
{
  if (!video || !video.textTracks)return;
  var tracks=video.textTracks;
  for (var i=0,ni=tracks.length;i<ni;i++)
  {
    // Leave chapter/metadata tracks alone - only subtitle tracks are switchable here.
    var kind=(tracks[i].kind || '').toLowerCase();
    if (kind!=='subtitles' && kind!=='captions')continue;
    tracks[i].mode=(i===index)?'showing':'disabled';
  }
  if (subtitleMenu)
  {
    var lis=subtitleMenu.getElementsByTagName('li');
    for (var i=0,ni=lis.length;i<ni;i++)
    {
      if (parseInt(lis[i].getAttribute('data-track'),10)===index)lis[i].setAttribute('selected','');
      else lis[i].removeAttribute('selected');
    }
  }
  closePlayerMenus();
}

// Builds the subtitle menu from whatever text tracks the video currently carries,
// whether they came in-band or were appended from the server track list.
function buildSubtitleMenu()
{
  if (!subtitleMenu)return;
  var list=subtitleMenu.firstElementChild;
  list.innerHTML='';
  var tracks=video.textTracks;
  var count=0;
  var active=-1;
  for (var i=0,ni=tracks.length;i<ni;i++)
  {
    var kind=(tracks[i].kind || '').toLowerCase();
    if (kind!=='subtitles' && kind!=='captions')continue;
    count++;
    if (tracks[i].mode==='showing')active=i;
    var li=document.createElement('li');
    li.textContent=tracks[i].label || tracks[i].language || ('Subtitle '+(i+1));
    li.setAttribute('data-track',i);
    li.setAttribute('tabindex','0');
    if (tracks[i].mode==='showing')li.setAttribute('selected','');
    list.appendChild(li);
  }
  // No subtitles: hide the button entirely so it can never open an empty overlay.
  if (subtitleBtn)subtitleBtn.style.display=(count>0)?'':'none';
  if (count===0)return;
  var off=document.createElement('li');
  off.textContent='🚫 Off';
  off.setAttribute('data-track',-1);
  off.setAttribute('tabindex','0');
  if (active<0)off.setAttribute('selected','');
  list.appendChild(off);
  var close=document.createElement('li');
  close.textContent='✕ Close';
  close.className='menuclose';
  close.setAttribute('tabindex','0');
  list.appendChild(close);
}

// Asks the server which text tracks the file has, then attaches one <track> per
// entry so each carries its real name and language instead of a fixed label.
function loadSubtitleTracks()
{
  if (!targetSubtitleList)
  {
    buildSubtitleMenu();
    return;
  }
  var request=new XMLHttpRequest();
  request.open('GET',targetSubtitleList,true);
  request.onreadystatechange=function()
  {
    if (request.readyState!==4)return;
    var tracks=[];
    if (request.status===200)try
    {
      tracks=JSON.parse(request.responseText);
    } catch (e)
    {
      console.warn('Unable to read subtitle track list:',e);
    }
    for (var i=0;i<tracks.length;i++)
    {
      var track=document.createElement('track');
      track.kind='subtitles';
      track.label=tracks[i].label;
      if (tracks[i].lang)track.srclang=tracks[i].lang;
      track.src=targetSubtitle+'?track='+tracks[i].index;
      track.setAttribute('data-added-by','external');
      if (i===0)track.default=true;
      video.appendChild(track);
    }
    buildSubtitleMenu();
  };
  request.send();
}

window.addEventListener("DOMContentLoaded",function()
{
 window.addEventListener('popstate',stateChange);
 window.addEventListener('message',function(e) 
 {
  if (e.origin.includes(window.location.hostname) && messageListener)messageListener(e);
 },false);
 header=document.getElementById('header');
 panel=document.getElementById('panel');
 video=document.getElementById('video');
 progressBar=document.getElementById('progressBar');
 progressBar.addEventListener('click',function(e)
 {
  if (!video.duration)return;
  const rect=progressBar.getBoundingClientRect();
  const clickX=e.clientX-rect.left;
  const width=rect.width;
  const newTime=(clickX/width)*video.duration;
  video.currentTime=newTime;
 });
 progressFill=document.getElementById('progressFill');
 currentTimeEl=document.getElementById('currentTime');
 totalDurationEl=document.getElementById('totalDuration');
 document.getElementById('playPauseBtn').addEventListener('click',function() 
 {
  if (video.paused)
  {
   video.play();
   this.textContent = '⏸️';
  } else 
  {
   video.pause();
   this.textContent = '▶️';
  }
 });
 document.getElementById('skipBackBtn').addEventListener('click',function()
 {
  video.currentTime=Math.max(video.currentTime-10,0);
 });
 document.getElementById('skipForwardBtn').addEventListener('click',function()
 {
  video.currentTime=Math.min(video.currentTime+10,video.duration);
 });
 function doFullscreen()
 {
  setFullWindow(false);
  // Check if we are in Document Picture-in-Picture mode
  if (window.documentPictureInPicture && window.documentPictureInPicture.window) 
  {
   // Close PiP window to restore player to main document
   window.documentPictureInPicture.window.close();
   
   // Wait for the player to be restored before requesting fullscreen
   setTimeout(() => {
    const playerContent = document.getElementById('player').firstElementChild;
    if (playerContent && playerContent.requestFullscreen) 
    {
     playerContent.requestFullscreen().catch(function(err){console.error("Error enabling fullscreen:", err);});
    }
   }, 100);
   return;
  }

  if (document.fullscreenElement) document.exitFullscreen();
  else
  {
   const playerContent = document.getElementById('player').firstElementChild;
   // Try to fullscreen the container (with controls) first
   if (playerContent && playerContent.requestFullscreen) 
   {
    playerContent.requestFullscreen().catch(function(err){console.error("Error enabling fullscreen:", err);});
   }
   // Fallback for Safari/iOS which might only support video fullscreen
   else if (video.webkitEnterFullscreen)
   {
    video.webkitEnterFullscreen();
   }
  }
 }
 async function doPictureInPicture()
 {
  setFullWindow(false);
  // Try Document Picture-in-Picture API first (Chrome 111+)
  if ('documentPictureInPicture' in window)
  {
   if (window.documentPictureInPicture.window)
   {
    // If already open, close it (which triggers pagehide to restore player)
    window.documentPictureInPicture.window.close();
    return;
   }

   try
   {
    const playerContainer = document.getElementById('player');
    const playerContent = playerContainer.firstElementChild; // The div inside aside
    
    // Define a default size for the PiP window (640x360 for video + 100 for controls)
    let pipWidth = 640;
    let pipHeight = 460;
    
    // Ensure the calculated size fits on the user's screen
    if (pipWidth > window.screen.availWidth) {
      const scale = window.screen.availWidth / pipWidth;
      pipWidth = window.screen.availWidth;
      pipHeight = Math.round(pipHeight * scale);
    }
    if (pipHeight > window.screen.availHeight) {
      const scale = window.screen.availHeight / pipHeight;
      pipHeight = window.screen.availHeight;
      pipWidth = Math.round(pipWidth * scale);
    }

    // Request a window with the calculated size
    const pipWindow = await window.documentPictureInPicture.requestWindow({
     width: pipWidth,
     height: pipHeight
    });

    // Copy all style sheets to the PiP window so controls look right
    [...document.styleSheets].forEach((styleSheet) => {
     try {
      const cssRules = [...styleSheet.cssRules].map((rule) => rule.cssText).join('');
      const style = document.createElement('style');
      style.textContent = cssRules;
      pipWindow.document.head.appendChild(style);
     } catch (e) {
      const link = document.createElement('link');
      link.rel = 'stylesheet';
      link.type = styleSheet.type;
      link.media = styleSheet.media;
      link.href = styleSheet.href;
      pipWindow.document.head.appendChild(link);
     }
    });
    
    // Explicitly set body style to match main theme basics
    pipWindow.document.body.style.margin = '0';
    pipWindow.document.body.style.display = 'flex';
    pipWindow.document.body.style.justifyContent = 'center';
    pipWindow.document.body.style.alignItems = 'center';
    pipWindow.document.body.style.background = getComputedStyle(document.body).getPropertyValue('--bgcolor');
    
    // Assign ID for styling in PiP
    playerContent.id = 'pip-root';

    // Inject PiP-specific styles to force responsive layout
    const pipStyle = pipWindow.document.createElement('style');
    pipStyle.textContent = `
      #pip-root { 
        width: 100% !important; 
        height: 100% !important; 
        display: flex !important; 
        flex-direction: column !important; 
        background: transparent !important;
      }
      #pip-root > video { 
        width: 100% !important; 
        height: 0 !important; 
        flex: 1 1 auto !important; 
        border-radius: 0 !important; 
        object-fit: contain !important; 
      }
      .player-controls { 
        width: 100%; 
        flex: 0 0 auto; 
        padding: 10px; 
        background: rgba(0,0,0,0.5); 
        box-sizing: border-box; 
      }
    `;
    pipWindow.document.head.appendChild(pipStyle);

    // Move the player content to the PiP window
    pipWindow.document.body.append(playerContent);
    
    // Ensure video fills the window (inline styles backup)
    playerContent.style.width = '100vw';
    playerContent.style.height = '100vh';
    playerContent.style.borderRadius = '0'; // Remove rounded corners for full window
    
    // Handle closing: Move player back to original container
    pipWindow.addEventListener('pagehide', (event) => {
     const playerPlaceholder = document.getElementById('player');
     if (playerPlaceholder) {
       // Reset styles
       playerContent.removeAttribute('id');
       playerContent.style.width = '';
       playerContent.style.height = '';
       playerContent.style.borderRadius = '';
       playerPlaceholder.append(playerContent);
     }
    });
    
    return; // Success, exit function
   } catch (err)
   {
    console.error("Document PiP failed, falling back to standard PiP:", err);
   }
  }

  // Fallback to Standard PiP
  if (!document.pictureInPictureEnabled||video.disablePictureInPicture)
  {
   console.warn("Picture-in-Picture is not enabled on this device.");
   return;
  }
  try 
  {
   if (document.fullscreenElement===video)await document.exitFullscreen();
   if (document.pictureInPictureElement)await document.exitPictureInPicture();
   else if (video!==document.pictureInPictureElement)await video.requestPictureInPicture();
  } catch (error) {console.error("Error with Picture-in-Picture:", error);}
 }
 function doCast()
 {
  if (video.webkitShowPlaybackTargetPicker)video.webkitShowPlaybackTargetPicker();
  else console.warn('Casting is not supported in this browser.');
 }
 function doFullWindow()
 {
  // Full window and PiP/fullscreen are mutually exclusive - leaving the others first
  // keeps the player node in the main document where the .fullwindow class applies.
  if (window.documentPictureInPicture && window.documentPictureInPicture.window)window.documentPictureInPicture.window.close();
  if (document.fullscreenElement)document.exitFullscreen();
  // Activate full window; Bottom Window is the explicit way back to the docked bar.
  setFullWindow(true);
 }
 function doBottomWindow()
 {
  // Return to the default docked bar by leaving whichever mode is active.
  if (window.documentPictureInPicture && window.documentPictureInPicture.window)window.documentPictureInPicture.window.close();
  if (document.fullscreenElement)document.exitFullscreen();
  setFullWindow(false);
 }
 subtitleBtn=document.getElementById('subtitleBtn');
 subtitleMenu=document.getElementById('subtitleMenu');
 // Hidden until a loaded video is found to actually have subtitle tracks.
 if (subtitleBtn)subtitleBtn.style.display='none';
 playerMenus=[document.getElementById('displayMenu'),subtitleMenu];
 document.getElementById('displayBtn').addEventListener('click',function(){togglePlayerMenu('displayMenu');});
 subtitleBtn.addEventListener('click',function(){togglePlayerMenu('subtitleMenu');});
 document.getElementById('displayMenu').addEventListener('click',function(event)
 {
  var li=event.target.closest('li');
  // A click on the overlay background (not an item) simply closes the menu.
  if (!li){closePlayerMenus();return;}
  closePlayerMenus();
  var action=li.getAttribute('data-action');
  if (action=='bottom')doBottomWindow();
  else if (action=='fullscreen')doFullscreen();
  else if (action=='fullwindow')doFullWindow();
  else if (action=='pip')doPictureInPicture();
  else if (action=='cast')doCast();
  // 'close' just falls through, the menu is already closed above.
 });
 subtitleMenu.addEventListener('click',function(event)
 {
  var li=event.target.closest('li');
  if (!li){closePlayerMenus();return;}
  var track=li.getAttribute('data-track');
  if (track==null){closePlayerMenus();return;}
  selectSubtitle(parseInt(track,10));
 });
 document.addEventListener('keydown',function(e)
 {
  var activeEl=document.activeElement;
  var playerFocused=activeEl && activeEl.parentElement && activeEl.parentElement.id==='player';
  var videoIsFullscreen=document.fullscreenElement===video;
  if (playerFocused||videoIsFullscreen)
  {
   if (e.key==='ArrowLeft')video.currentTime=Math.max(video.currentTime-10,0);
   if (e.key==='ArrowRight')video.currentTime=Math.min(video.currentTime+10,video.duration);
  }
  if (e.key==='Escape')
  {
   if (isPlayerMenuOpen())closePlayerMenus();
   else if (isFullWindow())setFullWindow(false);
  }
 });
 video.addEventListener('timeupdate',function()
 {
  const currentTime=Math.floor(video.currentTime);
  const now=Date.now();
  if (!video.ended&&now-lastSaveTime>MIN_SAVE_INTERVAL&&Math.abs(currentTime-lastSavedPosition)>=MIN_PROGRESS_DELTA)
  {
   saveProgress(currentTime);
   lastSaveTime=now;
   lastSavedPosition=currentTime;
  }
  const currentSecond=Math.floor(currentTime);
  if (currentSecond!==lastDisplayedSecond)
  {
   lastDisplayedSecond=currentSecond;
   currentTimeEl.textContent=formatTime(video.currentTime);
   if (video.duration)
   {
    progressFill.style.width=`${((video.currentTime/video.duration)*100)}%`;
    totalDurationEl.textContent=formatTime(video.duration-video.currentTime);
   } else
   {
	progressFill.style.width='0%';
	totalDurationEl.textContent='';
   }
  }
  
 });
 video.addEventListener('canplay',function()
 {
  if (targetTime>0)
  {
   video.currentTime=targetTime;
   targetTime=0;
   video.play().catch(error => {console.log('Autoplay was prevented:', error);});
  }
  checkWatch=true;
  totalDurationEl.textContent=formatTime(video.duration);
 });


 video.addEventListener('pause',function()
 {
  if (video.ended)return;
  saveProgress(Math.floor(video.currentTime));
 });

 video.addEventListener('ended',function()
 {
  saveProgress(-99);
  var finished=mediaFile;
  video.mediaFile=false;
  if (!autoPlayNext(finished))video.removeAttribute('src');
  if(document.getElementById("homepartwatch"))setTimeout(function(){makeRequest('homepart','order=watch');},1000);
  let fileWatch=document.getElementById("fileswatch");
  if (fileWatch)setTimeout(function(){makeRequest('fileswatch/'+fileWatch.getAttribute('path'),'',false);},1000);
 });
 
 /*
 window.addEventListener('orientationchange', function(e)
 {
  setTimeout(() => 
  {
   if (window.matchMedia("(orientation: landscape)").matches)
   {
    if (video.src)
    {
		console.log("Going to FullScreen");
	 if (video.requestFullscreen) video.requestFullscreen().catch(function(err){console.error("Error enabling fullscreen:", err);});
     else if (video.webkitEnterFullscreen)video.webkitEnterFullscreen();
    }
   } else
   {
	console.log("Going to inline video");
	if (document.exitFullscreen)document.exitFullscreen();
	else if (document.webkitExitFullscreen)document.webkitExitFullscreen();
   }
  },100);
 });
 */
});

/*
window.addEventListener('beforeunload',function()
{
 saveProgress(Math.floor(video.currentTime));
});
*/

// A name like "Show s03e02.mp4" marks a series episode. Season and episode parse out
// numerically, and the text before the marker identifies which series it belongs to.
function parseEpisode(name)
{
 var m=name.match(/s(\d+)\s*e(\d+)/i);
 if (!m)return null;
 return {season:parseInt(m[1],10),episode:parseInt(m[2],10),prefix:name.substring(0,m.index).toLowerCase()};
}

// Picks from candidate filenames the numerically-next episode of the same series:
// smallest (season,episode) strictly after the current one, so s4e9 precedes s4e10.
function pickNextEpisode(current,names)
{
 var best=null,bestName=null;
 for (var i=0,ni=names.length;i<ni;i++)
 {
  var ep=parseEpisode(names[i]);
  if (!ep || ep.prefix!==current.prefix)continue;
  if (ep.season<current.season || (ep.season===current.season && ep.episode<=current.episode))continue;
  if (best && (ep.season>best.season || (ep.season===best.season && ep.episode>=best.episode)))continue;
  best=ep;
  bestName=names[i];
 }
 return bestName;
}

// Retargets playback onto a sibling file and starts it. Every playback URL ends with
// the same library-relative path, so swapping that suffix moves stream, subtitles and
// progress key in one stroke - no page constants are needed in this shared script.
// A chained episode always starts from the beginning; nothing but its own progress
// is ever saved for it.
function startNextEpisode(finished,currentUrl,nextUrl)
{
 var slash=String(finished).indexOf('/');
 if (slash<0 || !targetMedia || targetMedia.length<=currentUrl.length)return false;
 targetMedia=targetMedia.substring(0,targetMedia.length-currentUrl.length)+nextUrl;
 if (targetSubtitle)targetSubtitle=targetSubtitle.substring(0,targetSubtitle.length-currentUrl.length)+nextUrl;
 if (targetSubtitleList)targetSubtitleList=targetSubtitleList.substring(0,targetSubtitleList.length-currentUrl.length)+nextUrl;
 mediaFile=String(finished).substring(0,slash+1)+nextUrl;
 targetTime=0;
 playTarget();
 video.play().catch(function(error){console.log('Autoplay was prevented:',error);});
 return true;
}

// When a finished file is an sNNeNN episode, the same folder may hold the next one.
// The page's own file listing answers instantly when the folder is on screen; playing
// from the Home watch list instead asks the server for the sibling names. Returns true
// when a next episode is playing or being fetched; the fetch path cleans up itself.
function autoPlayNext(finished)
{
 if (!finished || !targetMedia)return false;
 var slash=String(finished).indexOf('/');
 if (slash<0)return false;
 var currentUrl=String(finished).substring(slash+1);
 var dirEnd=currentUrl.lastIndexOf('/');
 if (dirEnd<0)return false;
 var current=parseEpisode(currentUrl.substring(dirEnd+1));
 if (!current)return false;
 var dir=currentUrl.substring(0,dirEnd+1);
 var names=[];
 var seen={};
 var lis=document.querySelectorAll('li[data-type="3"]');
 for (var i=0,ni=lis.length;i<ni;i++)
 {
  var url=lis[i].getAttribute('data-url');
  if (!url || seen[url])continue;
  seen[url]=true;
  if (url.substring(0,dir.length)!==dir)continue;
  var name=url.substring(dir.length);
  if (name.indexOf('/')<0)names.push(name);
 }
 var next=pickNextEpisode(current,names);
 if (next)return startNextEpisode(finished,currentUrl,dir+next);
 // No listing on this page (Home): ask the server which files sit beside this one.
 if (targetMedia.indexOf('/stream/')!==0)return false;
 var listUrl='/medialist/'+targetMedia.substring('/stream/'.length);
 var request=new XMLHttpRequest();
 request.open('GET',listUrl,true);
 request.onreadystatechange=function()
 {
  if (request.readyState!==4)return;
  // Stale by now if something else started playing in the meantime.
  if (video.mediaFile || mediaFile!==finished)return;
  var served=[];
  if (request.status===200)try
  {
   served=JSON.parse(request.responseText);
  } catch (e)
  {
   console.warn('Unable to read the folder media list:',e);
  }
  var pick=pickNextEpisode(current,served);
  if (!pick || !startNextEpisode(finished,currentUrl,dir+pick))video.removeAttribute('src');
 };
 request.send();
 return true;
}

function playTarget() {
  if (!video || !targetMedia) return;

  if (mediaFile) video.mediaFile = mediaFile;

  // Remove previously added external tracks
  var oldTracks = video.querySelectorAll('track[data-added-by="external"]');
  for (var i = 0; i < oldTracks.length; i++) video.removeChild(oldTracks[i]);

  video.src = targetMedia;

  function hasNativeSubtitles() {
    var tracks = video.textTracks;
    if (!tracks) return false;

    for (var i = 0; i < tracks.length; i++) {
      var kind = (tracks[i].kind || '').toLowerCase();
      if (kind === 'subtitles' || kind === 'captions') {
        return true;
      }
    }
    return false;
  }

  function decideAfterDelay() {
    // Wait a short time to allow iOS to register in-band tracks
    setTimeout(function () {

      // Check again AFTER delay
      if (hasNativeSubtitles()) buildSubtitleMenu();
      else loadSubtitleTracks();

    }, 150); // 100–250ms is usually enough
  }

  if (video.readyState >= 1) {
    decideAfterDelay();
  } else {
    video.addEventListener('loadedmetadata', decideAfterDelay, { once: true });
  }
}


function initMenu()
{
 if(!menu)menu=document.getElementById('menu');
 if(!menu)return;
 if(!menubtn)menubtn=document.getElementById('menubtn');
 var lis=menu.getElementsByTagName("li");
 if (lis.length>0)
 {
  if (menubtn)menubtn.style.display="";
  for (var i=0;i<lis.length;i++)
  {
   var page=lis[i].getAttribute('page');
   if (!page)continue;
   lis[i].addEventListener('click',function(event)
   {
    webDisconnect();
    clearPanel(true);
    clearTimer();
    var pagt=event.currentTarget.getAttribute('page');
	if (pagt=="logout" && video && video.src)video.removeAttribute('src');
    makeRequest(pagt,'',pagt!="logout");
    },false);
  }
 } else
 {
  if (menubtn)menubtn.style.display="none";
 }
}


var ws,wsTarget,wsOpenCallBack,wsMessageCallBack,wsCloseCallBack,reconnect;

function webSocketInit(target,openCallback,messageCallback,closeCallback)
{
  wsTarget=target;
  wsOpenCallBack=openCallback;
  wsMessageCallBack=messageCallback;
  wsCloseCallBack=closeCallback;
  webConnect();
}

function webConnect()
{
 webDisconnect();
 if (!window.WebSocket)if(!window.MozWebSocket)alert("WebSocket is not supported by this browser");
 var port=document.location.port;
 var location=document.location.protocol.replace('http','ws')+"//"+document.location.hostname+":"+port+"/socket/"+wsTarget; 
 if (window.WebSocket)ws=new WebSocket(location);
 else if (window.MozWebSocket)ws=new MozWebSocket(location);
 if (ws)
 {
  reconnect=true;
  ws.onopen=wsOpenCallBack;
  ws.onmessage=wsMessageCallBack;
  ws.onclose=wsClose;
 }
}

function webDisconnect()
{
 reconnect=false;
 if (ws) ws.close();
}

function wsClose(event)
{
 if (event.target==ws)
 {
  ws=false;
  if (wsCloseCallBack)wsCloseCallBack();
  if (reconnect) setTimeout(webConnect,1000);
 }
}

function wsSend(message)
{
if (ws)ws.send(message);
else alert("WebSocket Disconnected!");
return false;
}
