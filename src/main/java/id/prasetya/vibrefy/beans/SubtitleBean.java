package id.prasetya.vibrefy.beans;

import java.io.*;
import java.nio.channels.FileChannel;
import java.util.*;

import org.json.JSONArray;
import org.json.JSONObject;

import com.coremedia.iso.IsoFile;
import com.coremedia.iso.boxes.*;

import id.prasetya.vibrefy.Portal;
import id.prasetya.vibrefy.data.CloudItem;
import id.prasetya.vibrefy.tools.cloud.CloudCache;
import id.prasetya.vibrefy.tools.cloud.CloudProvider;
import id.prasetya.vibrefy.tools.cloud.RemoteSource;

public class SubtitleBean extends BeanObject
{
  public static final String CMDSubtitle="subtitle";
  public static final String CMDSubtitleList="subtitles";

  private static final String CACHE_KIND="vtt";

  private static final HashMap<String,String> languageNames=new HashMap<>();

  static
  {
    for (String two:Locale.getISOLanguages())
    {
      Locale loc=new Locale(two);
      String three=loc.getISO3Language();
      if (three!=null && three.length()>0) languageNames.put(three,loc.getDisplayLanguage(Locale.ENGLISH));
    }
  }

  private String vttContent = "";
  private int track=0;
  private JSONArray trackList=new JSONArray();

  public String getVttContent() {
    return vttContent;
  }

  public String getTrackList()
  {
    return trackList.toString();
  }

  public void setTrack(String track)
  {
    try
    {
      this.track=Integer.parseInt(track);
    } catch (Exception e)
    {
      this.track=0;
    }
    if (this.track<0) this.track=0;
  }

  protected void processData()
  {
    // Set the content type first, so an unresolved path still answers as an empty track
    // list / empty caption file rather than falling through to the HTML shell.
    boolean listOnly=CMDSubtitleList.equalsIgnoreCase(path.getCommand());
    if (listOnly) contentType="application/json; charset=utf-8";
    else contentType="text/vtt; charset=utf-8";

    if (path.getLength()<4)return;

    if (path.isCloud())
    {
      processCloud(listOnly);
      return;
    }

    if (path.getRealPath()==null)return;
    File file=new File(path.getRealPath());
    if (file==null || !file.exists() || !file.isFile())
    {
      return;
    }

    IsoFile isoFile = null;
    RandomAccessFile raf = null;
    try
    {
      isoFile = new IsoFile(file.getAbsolutePath());
      raf = new RandomAccessFile(file, "r");

      List<MovieBox> movieBoxes = isoFile.getBoxes(MovieBox.class);
      if (!movieBoxes.isEmpty())
      {
        MovieBox moov = movieBoxes.get(0);
        List<TrackBox> trackBoxes = moov.getBoxes(TrackBox.class);
        List<TrackBox> textTracks = new ArrayList<>();

        for (TrackBox track : trackBoxes)
        {
          MediaBox media = track.getMediaBox();
          HandlerBox handler = media.getHandlerBox();
          if ("sbtl".equals(handler.getHandlerType()) || "text".equals(handler.getHandlerType()))
          {
            textTracks.add(track);
          }
        }

        for (int t=0;t<textTracks.size();t++)
        {
          TrackBox entry=textTracks.get(t);
          JSONObject info=new JSONObject();
          info.put("index",t);
          info.put("label",trackLabel(entry,t));
          info.put("lang",languageTag(trackLanguage(entry)));
          trackList.put(info);
        }

        if (listOnly) return;

        TrackBox textTrack = (this.track<textTracks.size())?textTracks.get(this.track):null;

        if (textTrack != null)
        {
          final RandomAccessFile source=raf;
          vttContent=buildVtt(textTrack,new SampleReader()
          {
            public void readAt(long offset,byte[] target) throws IOException
            {
              source.seek(offset);
              source.readFully(target);
            }
          });
        }
      }
      
    } catch (Exception e)
    {
      e.printStackTrace();
    } finally
    {
      try { if (isoFile != null) isoFile.close(); } catch (IOException e) {}
      try { if (raf != null) raf.close(); } catch (IOException e) {}
    }
  }
  
  /** Reads one sample, so the decode loop works over a local file or a remote one. */
  private interface SampleReader
  {
    public void readAt(long offset,byte[] target) throws IOException;
  }

  /**
   * Walks a text track's sample table and renders WEBVTT. tx3g samples are a two-byte
   * big-endian length followed by UTF-8 text.
   */
  private String buildVtt(TrackBox textTrack,SampleReader reader) throws IOException
  {
    StringBuilder vtt = new StringBuilder();
    vtt.append("WEBVTT\n\n");

    MediaBox media = textTrack.getMediaBox();
    MediaHeaderBox mdhd = media.getMediaHeaderBox();
    long timescale = mdhd.getTimescale();

    SampleTableBox stbl = media.getMediaInformationBox().getSampleTableBox();

    List<TimeToSampleBox.Entry> timeEntries = stbl.getTimeToSampleBox().getEntries();
    List<SampleToChunkBox.Entry> chunkEntries = stbl.getSampleToChunkBox().getEntries();
    long[] chunkOffsets = stbl.getChunkOffsetBox().getChunkOffsets();
    long[] sampleSizes = stbl.getSampleSizeBox().getSampleSizes();
    long defaultSampleSize = stbl.getSampleSizeBox().getSampleSize();

    // Map sample index to duration
    long[] durations = new long[(int)stbl.getSampleSizeBox().getSampleCount()];
    int dIdx = 0;
    for (TimeToSampleBox.Entry entry : timeEntries) {
        for (int k = 0; k < entry.getCount(); k++) {
            if (dIdx < durations.length) durations[dIdx++] = entry.getDelta();
        }
    }

    int chunkEntryIdx = 0;
    int sampleIdx = 0;
    long currentTime = 0;

    for (int i = 0; i < chunkOffsets.length; i++) // for each chunk
    {
        long chunkOffset = chunkOffsets[i];

        // Determine samples per chunk
        // entries: [1, countA], [10, countB] -> chunks 1-9 have countA, 10+ have countB
        while (chunkEntryIdx < chunkEntries.size() - 1 && chunkEntries.get(chunkEntryIdx + 1).getFirstChunk() <= (i + 1)) {
            chunkEntryIdx++;
        }
        long samplesInThisChunk = chunkEntries.get(chunkEntryIdx).getSamplesPerChunk();

        for (int j = 0; j < samplesInThisChunk; j++)
        {
            if (sampleIdx >= durations.length) break;

            long duration = durations[sampleIdx];
            long size = (defaultSampleSize > 0) ? defaultSampleSize : sampleSizes[sampleIdx];

            // Read Sample
            // For tx3g: 2 bytes length (BE), then text.
            if (size > 2)
            {
                byte[] buffer = new byte[(int)size];
                reader.readAt(chunkOffset,buffer);

                int textLen = ((buffer[0] & 0xFF) << 8) | (buffer[1] & 0xFF);
                if (textLen > 0 && textLen <= size - 2)
                {
                    String text = new String(buffer, 2, textLen, "UTF-8");
                    if (!text.trim().isEmpty())
                    {
                        vtt.append(formatTime(currentTime, timescale));
                        vtt.append(" --> ");
                        vtt.append(formatTime(currentTime + duration, timescale));
                        vtt.append("\n");
                        vtt.append(text);
                        vtt.append("\n\n");
                    }
                }
            }

            chunkOffset += size;
            currentTime += duration;
            sampleIdx++;
        }
    }
    return vtt.toString();
  }

  /**
   * Subtitles for a cloud file. Enumerating tracks only needs the moov box and is cheap,
   * but extracting cues is not: subtitle samples are interleaved across the whole file,
   * so it costs roughly one HTTP range request per cue. The finished WEBVTT is therefore
   * cached on disk and the extraction only ever happens once per file and track.
   */
  private void processCloud(boolean listOnly)
  {
    CloudProvider provider=path.getProvider();
    if (provider==null || !provider.isValid())return;

    String cacheId=null;
    if (!listOnly)
    {
      cacheId=path.getCloudLabel()+"|"+path.getCloudPath()+"|"+this.track;
      String cached=CloudCache.read(CACHE_KIND,cacheId);
      if (cached!=null)
      {
        vttContent=cached;
        return;
      }
    }

    RemoteSource source=null;
    IsoFile isoFile=null;
    try
    {
      String itemId=path.getCloudId();
      if (itemId==null)return;
      CloudItem item=provider.getItem(itemId);
      if (item==null || item.getSize()<=0)return;

      // Small blocks: sample reads are scattered, so large ones would pull the
      // whole file across the network.
      source=new RemoteSource(provider,itemId,item.getSize(),RemoteSource.BLOCK_SCATTERED);
      isoFile=source.openIso();

      List<MovieBox> movieBoxes=isoFile.getBoxes(MovieBox.class);
      if (movieBoxes.isEmpty())return;
      List<TrackBox> textTracks=new ArrayList<>();
      for (TrackBox track:movieBoxes.get(0).getBoxes(TrackBox.class))
      {
        String handler=track.getMediaBox().getHandlerBox().getHandlerType();
        if ("sbtl".equals(handler) || "text".equals(handler))textTracks.add(track);
      }

      for (int t=0;t<textTracks.size();t++)
      {
        TrackBox entry=textTracks.get(t);
        JSONObject info=new JSONObject();
        info.put("index",t);
        info.put("label",trackLabel(entry,t));
        info.put("lang",languageTag(trackLanguage(entry)));
        trackList.put(info);
      }
      if (listOnly)return;

      if (this.track>=textTracks.size())return;
      final RemoteSource remote=source;
      vttContent=buildVtt(textTracks.get(this.track),new SampleReader()
      {
        public void readAt(long offset,byte[] target) throws IOException
        {
          int read=remote.readAt(offset,target,0,target.length);
          if (read<target.length)throw new IOException("Short subtitle sample read at "+offset);
        }
      });
      if (vttContent.length()>0)CloudCache.write(CACHE_KIND,cacheId,vttContent);
    } catch (Exception e)
    {
      System.out.println("ERRCLOUDSUBTITLE:"+e.getMessage());
    } finally
    {
      if (isoFile!=null)try
      {
        isoFile.close();
      } catch (IOException e)
      {
      }
      if (source!=null)source.close();
    }
  }

  private String trackLanguage(TrackBox track)
  {
    try
    {
      String lang=track.getMediaBox().getMediaHeaderBox().getLanguage();
      if (lang==null) return "";
      return lang.trim().toLowerCase();
    } catch (Exception e)
    {
      return "";
    }
  }

  private String languageTag(String code)
  {
    if (code==null || code.length()==0 || "und".equals(code)) return "";
    return code;
  }

  private String languageName(String code)
  {
    if (code==null || code.length()==0 || "und".equals(code)) return "";
    String name=languageNames.get(code);
    if (name!=null) return name;
    name=new Locale(code).getDisplayLanguage(Locale.ENGLISH);
    if (name!=null && !name.equalsIgnoreCase(code)) return name;
    return "";
  }

  private String trackName(TrackBox track)
  {
    try
    {
      List<UserDataBox> udtas=track.getBoxes(UserDataBox.class);
      if (!udtas.isEmpty())
      {
        List<TitleBox> titles=udtas.get(0).getBoxes(TitleBox.class);
        if (!titles.isEmpty())
        {
          String title=titles.get(0).getTitle();
          if (title!=null && title.trim().length()>0) return title.trim();
        }
      }
      String handlerName=track.getMediaBox().getHandlerBox().getName();
      if (handlerName==null) return "";
      handlerName=handlerName.trim();
      // Handler names are usually boilerplate ("SubtitleHandler", "Apple Text Media Handler"),
      // which tells the viewer nothing the language does not already say.
      String check=handlerName.toLowerCase();
      if (check.length()==0 || check.contains("handler") || "subtitle".equals(check) || "text".equals(check)) return "";
      return handlerName;
    } catch (Exception e)
    {
      return "";
    }
  }

  private String trackLabel(TrackBox track,int index)
  {
    String title=trackName(track);
    String lang=languageName(trackLanguage(track));
    // Lead with the language so the picker can be scanned by language, and append the
    // track's own title when it adds something (e.g. "English - Forced", "English - SDH").
    if (lang.length()>0 && title.length()>0)
    {
      // Avoid "English - English ..." when the title already starts with the language.
      if (title.toLowerCase().startsWith(lang.toLowerCase()))return title;
      return lang+" - "+title;
    }
    if (lang.length()>0)return lang;
    if (title.length()>0)return title;
    return "Subtitle "+(index+1);
  }

  private String formatTime(long time, long timescale)
  {
      double seconds = (double)time / timescale;
      long h = (long)(seconds / 3600);
      long m = (long)((seconds % 3600) / 60);
      double s = seconds % 60;
      // Locale.ROOT is required: WEBVTT timestamps must use a dot as the decimal
      // separator, but the default locale may format it as a comma (e.g. en_ID).
      return String.format(Locale.ROOT, "%02d:%02d:%06.3f", h, m, s);
  }
}
