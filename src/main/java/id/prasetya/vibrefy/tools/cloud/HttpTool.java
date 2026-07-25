package id.prasetya.vibrefy.tools.cloud;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.json.JSONObject;
import org.json.JSONTokener;

/**
 * Small HTTP helper for the cloud providers. Built on java.net.http (Java 11) so no new
 * jar is needed. Kept separate from tools/google/Token, whose httpGet reads line by line
 * and so cannot carry binary media.
 */
public class HttpTool
{
  private static final Duration CONNECT_TIMEOUT=Duration.ofSeconds(15);
  private static final Duration REQUEST_TIMEOUT=Duration.ofSeconds(60);
  private static final int MAX_REDIRECT=3;

  private static final ThreadLocal<Integer> lastStatus=ThreadLocal.withInitial(() -> 0);

  // Redirects are followed by hand rather than by the client, so that an Authorization
  // header is never replayed onto a different host. Providers redirect API URLs to CDN
  // hosts, and forwarding the token there would leak it.
  private static ExecutorService executor=null;
  private static HttpClient client=buildClient();

  /**
   * The client's worker and selector threads inherit the creating thread's context
   * classloader - here that would be the webapp classloader, and a long-lived thread
   * holding it keeps every class and static of this deployment reachable. Each hot
   * redeploy would then leak the entire previous webapp (seen as one extra
   * HttpClient-N-SelectorManager thread per redeploy) until the heap fills. Building
   * the client and its executor under a null context classloader breaks that pin.
   */
  private static HttpClient buildClient()
  {
    Thread self=Thread.currentThread();
    ClassLoader saved=self.getContextClassLoader();
    self.setContextClassLoader(null);
    try
    {
      executor=Executors.newCachedThreadPool(task ->
      {
        Thread worker=new Thread(task,"vibrefy-http");
        worker.setDaemon(true);
        worker.setContextClassLoader(null);
        return worker;
      });
      return HttpClient.newBuilder()
          .connectTimeout(CONNECT_TIMEOUT)
          .followRedirects(HttpClient.Redirect.NEVER)
          .executor(executor)
          .build();
    } finally
    {
      self.setContextClassLoader(saved);
    }
  }

  /**
   * Undeploy cleanup. Java 11's HttpClient has no close(); dropping the reference and
   * stopping its executor lets the instance wind down and be garbage collected instead
   * of surviving into the next deployment.
   */
  public static void shutdown()
  {
    client=null;
    if (executor!=null)executor.shutdownNow();
    executor=null;
  }

  /** Status of the most recent call on this thread, so callers can tell 401 from 404. */
  public static int getLastStatus()
  {
    return lastStatus.get();
  }

  public static String encode(String value)
  {
    if (value==null)return "";
    return URLEncoder.encode(value,StandardCharsets.UTF_8);
  }

  /**
   * Host-only form of a URL for log/exception messages. Some providers (FileLu in
   * particular) issue links that work indefinitely with no further authentication, so
   * the full URL is as sensitive as the file itself and must never reach a log file -
   * only the host is safe to print.
   */
  private static String redacted(String url)
  {
    try
    {
      return URI.create(sanitizeUrl(url)).getHost()+" (redacted)";
    } catch (Exception e)
    {
      return "(redacted)";
    }
  }

  private static boolean isHex(char c)
  {
    return (c>='0'&&c<='9')||(c>='a'&&c<='f')||(c>='A'&&c<='F');
  }

  /**
   * A character that may stay unencoded in a URL: the unreserved set plus the delimiters
   * that carry structure (scheme/host/path/query). Everything else - spaces, non-ASCII
   * (CJK and the like), brackets, and other illegal characters - is percent-encoded.
   * Note '[' and ']' are treated as encodable, not structural: real filenames use them
   * (e.g. "[Group] Title.mkv") far more often than these CDN URLs use IPv6 literals.
   */
  private static boolean isSafeUrlChar(char c)
  {
    if ((c>='A'&&c<='Z')||(c>='a'&&c<='z')||(c>='0'&&c<='9'))return true;
    switch (c)
    {
      case '-': case '.': case '_': case '~':
      case ':': case '/': case '?': case '#': case '@':
      case '!': case '$': case '&': case '\'': case '(': case ')':
      case '*': case '+': case ',': case ';': case '=':
        return true;
    }
    return false;
  }

  /**
   * Makes a provider URL parseable by URI. Some providers (FileLu, notably) return
   * download links with the raw filename in the path, so a movie called
   * "Title (2026).mp4" or a Chinese/Japanese title arrives with unencoded spaces and
   * non-ASCII characters that URI.create rejects. Illegal characters are percent-encoded
   * as UTF-8, while existing %xx escapes are left alone so a properly-encoded CDN URL is
   * not double-encoded.
   */
  static String sanitizeUrl(String url)
  {
    if (url==null)return null;
    StringBuilder sb=new StringBuilder(url.length()+16);
    int n=url.length();
    for (int i=0;i<n;)
    {
      char c=url.charAt(i);
      // Preserve an existing percent-escape verbatim.
      if (c=='%' && i+2<n && isHex(url.charAt(i+1)) && isHex(url.charAt(i+2)))
      {
        sb.append(url,i,i+3);
        i+=3;
        continue;
      }
      if (isSafeUrlChar(c))
      {
        sb.append(c);
        i++;
        continue;
      }
      // Encode this whole code point's UTF-8 bytes, so surrogate pairs and multi-byte
      // characters (a lone % included) come out as valid %XX sequences.
      int codePoint=url.codePointAt(i);
      byte[] bytes=new String(Character.toChars(codePoint)).getBytes(StandardCharsets.UTF_8);
      for (byte b:bytes)sb.append('%').append(String.format("%02X",b & 0xFF));
      i+=Character.charCount(codePoint);
    }
    return sb.toString();
  }

  private static URI toUri(String url) throws IOException
  {
    try
    {
      return URI.create(sanitizeUrl(url));
    } catch (IllegalArgumentException e)
    {
      // Should be rare now that illegal characters are encoded, but a genuinely
      // malformed URL still must not surface as a 500.
      throw new IOException("Not a usable URL: "+redacted(url));
    }
  }

  private static HttpRequest.Builder build(String url,String[] headers) throws IOException
  {
    HttpRequest.Builder builder=HttpRequest.newBuilder(toUri(url)).timeout(REQUEST_TIMEOUT);
    if (headers!=null)for (int i=0;i+1<headers.length;i+=2)builder.header(headers[i],headers[i+1]);
    return builder;
  }

  private static boolean isRedirect(int status)
  {
    return status==301 || status==302 || status==303 || status==307 || status==308;
  }

  public static JSONObject getJSON(String url,String[] headers) throws IOException
  {
    try
    {
      HttpResponse<InputStream> response=client.send(build(url,headers).GET().build(),
          HttpResponse.BodyHandlers.ofInputStream());
      lastStatus.set(response.statusCode());
      if (response.statusCode()<200 || response.statusCode()>299)
      {
        response.body().close();
        return null;
      }
      try (InputStream in=response.body())
      {
        return new JSONObject(new JSONTokener(new java.io.InputStreamReader(in,StandardCharsets.UTF_8)));
      }
    } catch (InterruptedException e)
    {
      Thread.currentThread().interrupt();
      throw new IOException("Interrupted requesting "+redacted(url),e);
    }
  }

  public static JSONObject postForm(String url,String body,String[] headers) throws IOException
  {
    try
    {
      HttpRequest.Builder builder=build(url,headers)
          .header("Content-Type","application/x-www-form-urlencoded")
          .POST(HttpRequest.BodyPublishers.ofString(body==null?"":body,StandardCharsets.UTF_8));
      HttpResponse<InputStream> response=client.send(builder.build(),HttpResponse.BodyHandlers.ofInputStream());
      lastStatus.set(response.statusCode());
      try (InputStream in=response.body())
      {
        // Error bodies still carry the provider's reason, so parse them either way.
        return new JSONObject(new JSONTokener(new java.io.InputStreamReader(in,StandardCharsets.UTF_8)));
      }
    } catch (InterruptedException e)
    {
      Thread.currentThread().interrupt();
      throw new IOException("Interrupted posting "+redacted(url),e);
    }
  }

  /**
   * Reads a byte range. Returns null when the server refuses, leaving the status in
   * getLastStatus() so the caller can decide whether to re-acquire the link.
   */
  public static byte[] getRange(String url,String[] headers,long offset,int length) throws IOException
  {
    String target=url;
    String[] active=headers;
    for (int hop=0;hop<=MAX_REDIRECT;hop++)
    {
      try
      {
        HttpRequest.Builder builder=build(target,active).header("Range","bytes="+offset+"-"+(offset+length-1)).GET();
        HttpResponse<byte[]> response=client.send(builder.build(),HttpResponse.BodyHandlers.ofByteArray());
        int status=response.statusCode();
        lastStatus.set(status);
        if (isRedirect(status))
        {
          String location=response.headers().firstValue("Location").orElse(null);
          if (location==null)return null;
          target=toUri(target).resolve(sanitizeUrl(location)).toString();
          active=null; // never forward credentials across a redirect
          continue;
        }
        if (status!=200 && status!=206)return null;
        return response.body();
      } catch (InterruptedException e)
      {
        Thread.currentThread().interrupt();
        throw new IOException("Interrupted reading "+redacted(target),e);
      }
    }
    return null;
  }

  /** Upper bound on one proxied exchange, so a stalled or aborted playback can never
   * have more than this in flight however the HTTP client buffers internally. */
  private static final int PIPE_CHUNK=8*1024*1024; // 8 MB

  /**
   * Streams a byte range to the client as a series of bounded range requests.
   *
   * The previous form asked the provider for the file's whole remainder - gigabytes in
   * one exchange - and trusted the HTTP client's flow control and cancellation to keep
   * undelivered data out of heap. In practice a playback that stalls or aborts (both
   * routine: players pause, seek, and drop range requests constantly) left those
   * exchanges retaining body data, and two 2 GB films back to back filled a 2 GB heap.
   * Requesting at most PIPE_CHUNK per exchange and consuming every response fully caps
   * the worst case at one chunk, whatever the client's internals do, and fully-read
   * responses also let the pooled connection be reused between chunks.
   */
  public static long pipeRange(String url,String[] headers,long offset,long length,OutputStream out) throws IOException
  {
    String target=url;
    String[] active=headers;
    long written=0;
    byte[] buffer=new byte[8192];
    while (length<=0 || written<length)
    {
      long start=offset+written;
      long want=(length>0)?Math.min(PIPE_CHUNK,length-written):PIPE_CHUNK;
      HttpResponse<InputStream> response=null;
      for (int hop=0;hop<=MAX_REDIRECT && response==null;hop++)
      {
        try
        {
          HttpRequest.Builder builder=build(target,active).GET()
              .header("Range","bytes="+start+"-"+(start+want-1));
          response=client.send(builder.build(),HttpResponse.BodyHandlers.ofInputStream());
        } catch (InterruptedException e)
        {
          Thread.currentThread().interrupt();
          throw new IOException("Interrupted streaming "+redacted(target),e);
        }
        lastStatus.set(response.statusCode());
        if (isRedirect(response.statusCode()))
        {
          response.body().close();
          String location=response.headers().firstValue("Location").orElse(null);
          if (location==null)return written>0?written:-1;
          // The resolved target is kept for the following chunks, so a provider that
          // redirects to its CDN is only asked once per playback request.
          target=toUri(target).resolve(sanitizeUrl(location)).toString();
          active=null; // never forward credentials across a redirect
          response=null;
        }
      }
      if (response==null)return written>0?written:-1;
      int status=response.statusCode();
      if (status==200)return pipeWhole(response,start,length>0?length-written:0,out,buffer,written);
      if (status!=206)
      {
        response.body().close();
        return written>0?written:-1;
      }
      long got=0;
      try (InputStream in=response.body())
      {
        int read;
        while (got<want && (read=in.read(buffer,0,(int)Math.min(buffer.length,want-got)))!=-1)
        {
          out.write(buffer,0,read);
          got+=read;
        }
      }
      written+=got;
      // A short chunk means the file ended before the requested range did.
      if (got<want)break;
    }
    return written;
  }

  /**
   * Fallback for a provider that ignored the Range header and answered 200 with the
   * whole file. The bytes before the requested offset are read and discarded - the old
   * code relayed them as if they were the slice, corrupting any non-zero-offset read.
   * No current provider takes this path; all of them honour ranges.
   */
  private static long pipeWhole(HttpResponse<InputStream> response,long skip,long limit,OutputStream out,byte[] buffer,long alreadyWritten) throws IOException
  {
    long written=alreadyWritten;
    try (InputStream in=response.body())
    {
      long skipped=0;
      while (skipped<skip)
      {
        int read=in.read(buffer,0,(int)Math.min(buffer.length,skip-skipped));
        if (read==-1)return written>0?written:-1;
        skipped+=read;
      }
      long sent=0;
      int read;
      while ((limit<=0 || sent<limit) && (read=in.read(buffer,0,(limit>0)?(int)Math.min(buffer.length,limit-sent):buffer.length))!=-1)
      {
        out.write(buffer,0,read);
        sent+=read;
        written+=read;
      }
    }
    return written;
  }
}
