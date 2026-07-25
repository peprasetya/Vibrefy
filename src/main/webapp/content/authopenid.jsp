<jsp:useBean id="bean" scope="request" class="id.prasetya.vibrefy.beans.AuthOpenIDBean" /><%
%><html>
<head>
<script>
let chref=window.location.href;

console.log(window.location.href);
window.opener.postMessage('checkLogin', '<%=bean.getReferrer()%>');
</script>
</head>
</html>