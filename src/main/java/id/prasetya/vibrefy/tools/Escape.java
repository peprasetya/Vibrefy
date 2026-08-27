package id.prasetya.vibrefy.tools;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public class Escape
{
  public static String html(String value)
  {
    if (value==null)return "";
    StringBuilder sb=new StringBuilder(value.length());
    for (int i=0,n=value.length();i<n;i++)
    {
      char c=value.charAt(i);
      switch (c)
      {
        case '&': sb.append("&amp;"); break;
        case '<': sb.append("&lt;"); break;
        case '>': sb.append("&gt;"); break;
        case '"': sb.append("&quot;"); break;
        case '\'': sb.append("&#39;"); break;
        default: sb.append(c);
      }
    }
    return sb.toString();
  }

  /**
   * Percent-encodes one path segment - a filename or folder name - for safe use in a
   * URL. URLEncoder is a form encoder, so a literal space would otherwise come out as
   * '+', which URLDecoder.decode (used to read requests back) would accept, but a raw
   * '+' already present in a filename would then be misread as a space on the way back.
   * Escaping the space as %20 keeps the round trip exact for both cases. Every other
   * reserved character - '?', '#', '&', '%' - survives correctly because URLEncoder
   * escapes it; a bare '?' is what let a request path get truncated into a query string.
   */
  public static String urlSegment(String value)
  {
    if (value==null)return "";
    return URLEncoder.encode(value,StandardCharsets.UTF_8).replace("+","%20");
  }

  /** Percent-encodes each '/'-separated segment of a path, leaving the separators alone. */
  public static String urlPath(String value)
  {
    if (value==null)return "";
    String[] segments=value.split("/",-1);
    StringBuilder sb=new StringBuilder();
    for (int i=0;i<segments.length;i++)
    {
      if (i>0)sb.append('/');
      sb.append(urlSegment(segments[i]));
    }
    return sb.toString();
  }
}
