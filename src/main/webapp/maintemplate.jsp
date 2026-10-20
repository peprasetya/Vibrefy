<%@page pageEncoding="UTF-8" %><jsp:useBean id="bean" scope="request" class="id.prasetya.vibrefy.beans.BeanObject" /><%!
// Versions an asset URL by the file's own modified time, so the browser keeps it cached
// until it actually changes and still picks up every edit straight away. Startup time is
// the fallback when the container serves from somewhere with no file to look at.
private static final long STARTED=System.currentTimeMillis();

private static long stamp(jakarta.servlet.ServletContext context,String path)
{
  String real=context.getRealPath(path);
  if (real==null)return STARTED;
  long modified=new java.io.File(real).lastModified();
  return modified>0?modified:STARTED;
}
%><html>
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1, minimum-scale=1, maximum-scale=1">
<meta name="application-name" content="Vibrefy">
<title>Vibrefy - Vibrate the Souls!!</title>
<link rel="icon" type="image/svg+xml" href="/images/vibrefy.svg">
<link rel="apple-touch-icon" href="/images/vibrefy.svg">
<link rel="stylesheet" href="/maintemplate.css?<%=stamp(application,"/maintemplate.css")%>">
<script src="/scriptlib.js?<%=stamp(application,"/scriptlib.js")%>"></script>
</head>
<body ontouchstart="">
<input id="menucb" type="checkbox"><label id="menubtn" for="menucb"><div></div></label><label class="menuback" for="menucb"></label>
<nav id="menu"></nav>
<main id="panel"></main>
<aside id="player" ><div>
 <video id="video" autoplay playsinline webkit-playsinline x-webkit-airplay="allow"></video>
 <div class="player-controls">
  <span>
   <button tabindex="0" id="skipBackBtn">⏪</button>
   <button tabindex="0" id="playPauseBtn">⏯️️</button>
   <button tabindex="0" id="skipForwardBtn">⏩</button>
   <button tabindex="0" id="subtitleBtn">💬</button>
   <button tabindex="0" id="displayBtn">📺</button>
  </span>
  <div id="progressBar">
   <div id="progressFill"></div>
   <span id="currentTime">0:00</span>
   <span id="totalDuration">0:00</span>
  </div>
 </div>
 <div id="displayMenu" class="playerMenu hidden"><ul>
  <li data-action="bottom" tabindex="0">📻 Bottom Window</li>
  <li data-action="fullwindow" tabindex="0">🖥️ Full Window</li>
  <li data-action="fullscreen" tabindex="0">⛶️ Full Screen</li>
  <li data-action="pip" tabindex="0">🔲️️️ Picture in Picture</li>
  <li data-action="cast" tabindex="0">🛜️️️ Cast</li>
  <li data-action="close" class="menuclose" tabindex="0">✕ Close</li>
 </ul></div>
 <div id="subtitleMenu" class="playerMenu hidden"><ul></ul></div>
</div></aside>
<footer><div><img src="/images/vibrefy.svg" alt="Vibrefy" class="brandmark"><span>Vibrefy, light-weight media server.<br>Licensed under the MIT License.</span></div></footer>
<script id="pageScript">
makeRequest('<%=bean.getAccount()==null?"welcome":"menu"%>','');
</script>
</body>
</html>
