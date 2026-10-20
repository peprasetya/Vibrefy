package id.prasetya.vibrefy.tools.cloud;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Small in-memory cache for artefacts that are expensive to derive from a remote file.
 *
 * Subtitle extraction is the reason this exists. Subtitle samples are interleaved
 * across the whole MP4, so pulling them from a cloud file costs roughly one HTTP
 * request per cue - measured at ~300 requests for 300 cues. That is tolerable once
 * and intolerable on every playback, so the finished WEBVTT is kept for a while.
 *
 * Memory only, by design: nothing about what a user played is ever written to disk,
 * and a restart forgets all of it. Entries belong to one user and are never served to
 * another, even for the same mount label and path - two people can both have a mount
 * called "Drive" holding different files at the same path.
 */
public class CloudCache
{
  // Long enough to cover a film and a rewatch; short enough that nothing lingers.
  private static final long MAX_AGE=2L*60L*60L*1000L;
  private static final int MAX_ENTRIES=100;
  // One feature-length subtitle track is tens of kilobytes. This only stops a freak file.
  private static final int MAX_CHARS=2*1024*1024;
  // The directory an earlier version wrote to, relative to the server's working directory.
  private static final String LEGACY_DIR="vibecache";

  private static class Held
  {
    final String content;
    final long created=System.currentTimeMillis();
    Held(String content){this.content=content;}
  }

  private static final Map<String,Held> entries=java.util.Collections.synchronizedMap(
      new LinkedHashMap<String,Held>(32,0.75f,true)
      {
        private static final long serialVersionUID=1L;
        protected boolean removeEldestEntry(Map.Entry<String,Held> eldest)
        {
          return size()>MAX_ENTRIES;
        }
      });

  private static String keyFor(String user,String kind,String identity)
  {
    if (user==null || kind==null || identity==null)return null;
    return user+"|"+kind+"|"+identity;
  }

  /** The cached content for this user, or null when absent or expired. */
  public static String read(String user,String kind,String identity)
  {
    String key=keyFor(user,kind,identity);
    if (key==null)return null;
    Held entry=entries.get(key);
    if (entry==null)return null;
    if (System.currentTimeMillis()-entry.created>MAX_AGE)
    {
      entries.remove(key);
      return null;
    }
    return entry.content;
  }

  public static void write(String user,String kind,String identity,String content)
  {
    String key=keyFor(user,kind,identity);
    if (key==null || content==null || content.length()>MAX_CHARS)return;
    entries.put(key,new Held(content));
  }

  /** Forgets everything held for one user: a mount was re-linked, or their last session ended. */
  public static void forgetUser(String user)
  {
    if (user==null)return;
    String prefix=user+"|";
    synchronized (entries)
    {
      entries.keySet().removeIf(key -> key.startsWith(prefix));
    }
  }

  public static void clear()
  {
    entries.clear();
  }

  /**
   * Deletes what an earlier version left on disk. Run at startup, so the old subtitle
   * text goes away on the next restart wherever the server runs, without anyone having
   * to find the directory. Only touches regular files directly inside it.
   */
  public static void purgeLegacy()
  {
    File dir=new File(LEGACY_DIR);
    if (!dir.isDirectory())return;
    File[] files=dir.listFiles();
    if (files!=null)for (File file:files)if (file.isFile())file.delete();
    dir.delete();
  }
}
