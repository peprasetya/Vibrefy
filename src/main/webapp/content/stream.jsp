<%@ page session="false"%><%@page import="java.io.*,id.prasetya.vibrefy.data.CloudLink,id.prasetya.vibrefy.tools.cloud.HttpTool"%><jsp:useBean id="bean" scope="request" class="id.prasetya.vibrefy.beans.StreamBean" /><%
CloudLink link=bean.getCloudLink();
if (link!=null)
{
  // Cloud media is proxied: the server fetches the range and relays it, so the
  // browser never contacts the storage provider directly.
  try
  {
    HttpTool.pipeRange(link.getURL(),link.getHeaders(),bean.getStart(),bean.getContentLength(),response.getOutputStream());
    response.getOutputStream().flush();
  } catch (IOException ioe)
  {
    // A player aborting a range request (seek, close, buffer switch) is normal and
    // shows up here as a broken pipe - not worth a stack trace.
    if (!isClientAbort(ioe))ioe.printStackTrace(System.out);
  }
} else
{
File file=bean.getFile();
RandomAccessFile inputFile=null;
if (file!=null)try
{
  inputFile=new RandomAccessFile(file, "r");
  OutputStream output=response.getOutputStream();
  byte[] buffer=new byte[8192];
  int bytesRead;
  inputFile.seek(bean.getStart());
  long bytesLeft=bean.getContentLength();

  while (bytesLeft>0 && (bytesRead=inputFile.read(buffer,0,(int)Math.min(buffer.length,bytesLeft)))!=-1)
  {
    output.write(buffer, 0, bytesRead);
    bytesLeft-=bytesRead;
  }
  output.flush();
} catch (IOException ioe)
{
  if (!isClientAbort(ioe))ioe.printStackTrace(System.out);
} finally
{
  if (inputFile!=null)try
  {
    inputFile.close();
  } catch (IOException ex){ex.printStackTrace();}
}
}
%><%!
// A disconnect by the player (EofException, connection reset, broken pipe) is expected
// during video playback and must not be logged as an error.
private boolean isClientAbort(Throwable t)
{
  while (t!=null)
  {
    String name=t.getClass().getName();
    String msg=t.getMessage();
    if (name.contains("EofException"))return true;
    // A player that has buffered ahead simply stops reading, so the write blocks until
    // the container's write-idle timeout fires. That is the player pacing us, not a
    // fault, and it arrives as an idle-timeout IOException rather than a reset.
    if (msg!=null && (msg.contains("Connection reset")||msg.contains("Broken pipe")||msg.contains("connection was aborted")||msg.contains("Idle timeout expired")))return true;
    t=t.getCause();
  }
  return false;
}
%>