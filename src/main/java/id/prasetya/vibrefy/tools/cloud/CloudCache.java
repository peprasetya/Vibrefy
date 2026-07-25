package id.prasetya.vibrefy.tools.cloud;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;

/**
 * Small on-disk cache for artefacts that are expensive to derive from a remote file.
 *
 * Subtitle extraction is the reason this exists. Subtitle samples are interleaved
 * across the whole MP4, so pulling them from a cloud file costs roughly one HTTP
 * request per cue - measured at ~300 requests for 300 cues. That is tolerable once
 * and intolerable on every playback, so the finished WEBVTT is kept here.
 */
public class CloudCache
{
  private static final String CACHE_DIR="vibecache";
  private static final long MAX_AGE=30L*24L*60L*60L*1000L; // 30 days

  private static File directory()
  {
    File dir=new File(CACHE_DIR);
    if (!dir.exists())dir.mkdirs();
    return dir;
  }

  /** Hashed so a cache filename never leaks a path or an account id. */
  private static String keyFor(String kind,String identity)
  {
    try
    {
      MessageDigest digest=MessageDigest.getInstance("SHA-256");
      byte[] hash=digest.digest((kind+"|"+identity).getBytes(StandardCharsets.UTF_8));
      StringBuilder text=new StringBuilder(hash.length*2);
      for (byte b:hash)
      {
        String hex=Integer.toHexString(b & 0xFF);
        if (hex.length()==1)text.append('0');
        text.append(hex);
      }
      return text.toString();
    } catch (Exception e)
    {
      return null;
    }
  }

  public static String read(String kind,String identity)
  {
    String key=keyFor(kind,identity);
    if (key==null)return null;
    File file=new File(directory(),key);
    if (!file.exists() || !file.isFile())return null;
    if (System.currentTimeMillis()-file.lastModified()>MAX_AGE)
    {
      file.delete();
      return null;
    }
    try
    {
      return new String(Files.readAllBytes(file.toPath()),StandardCharsets.UTF_8);
    } catch (IOException e)
    {
      return null;
    }
  }

  public static void write(String kind,String identity,String content)
  {
    String key=keyFor(kind,identity);
    if (key==null || content==null)return;
    File target=new File(directory(),key);
    // Write to a sibling and rename, so a crash mid-write cannot leave a half file
    // that would later be served as if it were complete.
    File temp=new File(directory(),key+".tmp");
    try
    {
      try (FileOutputStream out=new FileOutputStream(temp))
      {
        out.write(content.getBytes(StandardCharsets.UTF_8));
      }
      Files.move(temp.toPath(),target.toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    } catch (IOException e)
    {
      temp.delete();
    }
  }
}
