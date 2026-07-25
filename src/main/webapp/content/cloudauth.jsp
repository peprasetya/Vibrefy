<jsp:useBean id="bean" scope="request" class="id.prasetya.vibrefy.beans.CloudAuthBean" /><html>
<head>
<meta charset="utf-8">
<title>Vibrefy Cloud</title>
<script>
// Tell the opener to refresh its connected list, then close, like the login popup.
if (window.opener) window.opener.postMessage('checkCloud', window.location.origin);
window.close();
</script>
</head>
</html>
