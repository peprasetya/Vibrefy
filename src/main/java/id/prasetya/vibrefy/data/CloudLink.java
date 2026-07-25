package id.prasetya.vibrefy.data;

/**
 * A URL the server can issue byte-range requests against, plus whatever headers that
 * particular provider needs. Providers differ here: Drive wants a Bearer token on an
 * API URL, while OneDrive hands out a pre-authenticated CDN URL that must be fetched
 * with no Authorization header at all, so the headers travel with the link.
 */
public class CloudLink
{
  private static final String[] NOHEADERS=new String[0];

  private String url=null;
  private String[] headers=null;
  private long expire=0;

  public String getURL(){return url;}
  public String[] getHeaders(){return headers==null?NOHEADERS:headers;}
  public long getExpire(){return expire;}

  /** An expire of 0 means the link itself never goes stale. */
  public boolean isExpired()
  {
    return expire>0 && expire<System.currentTimeMillis();
  }

  public CloudLink(String url,String[] headers,long expire)
  {
    this.url=url;
    this.headers=headers;
    this.expire=expire;
  }
}
