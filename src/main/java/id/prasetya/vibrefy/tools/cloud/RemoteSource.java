package id.prasetya.vibrefy.tools.cloud;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;
import java.util.LinkedHashMap;
import java.util.Map;

import com.googlecode.mp4parser.DataSource;

import id.prasetya.vibrefy.data.CloudLink;

/**
 * Presents a remote file as a seekable DataSource so isoparser can read MP4 boxes over
 * HTTP range requests without downloading the whole file.
 *
 * The block cache is not an optimisation. Measured against the real workload, isoparser
 * issues dozens to hundreds of tiny scattered reads while walking box headers and sample
 * tables; without buffering, each one would be its own HTTP round trip. Reading in
 * blocks collapses the moov traffic into a couple of fetches. It never maps the mdat
 * box, so a large file costs only a few kilobytes of transfer.
 */
public class RemoteSource implements DataSource
{
  /** Good for walking box headers, which sit contiguously inside moov. */
  public static final int BLOCK_SEQUENTIAL=262144; // 256 KB
  /**
   * For subtitle sample reads. Those samples are only tens of bytes each but are
   * interleaved with audio and video across the whole file, so one sample lands in one
   * block and the request count converges on the cue count whatever the block size.
   * Bytes transferred, however, scale directly with it. Measured over 300 cues in an
   * 11 MB file: 256 KB blocks moved 102% of the file, 32 KB moved 88%, 16 KB moved 44%,
   * 8 KB moved 22% - all at the same ~300 requests. So keep this small.
   */
  public static final int BLOCK_SCATTERED=8192; // 8 KB

  private static final int MAXBYTES=8*1024*1024; // ~8 MB of cache per open file

  private CloudProvider provider=null;
  private String itemId=null;
  private long total=0;
  private long pos=0;
  private CloudLink link=null;
  private int blockSize=BLOCK_SEQUENTIAL;
  private int maxBlocks=32;
  private int requestCount=0;

  private final Map<Long,byte[]> blocks=new LinkedHashMap<Long,byte[]>(16,0.75f,true)
  {
    private static final long serialVersionUID=1L;
    protected boolean removeEldestEntry(Map.Entry<Long,byte[]> eldest)
    {
      return size()>maxBlocks;
    }
  };

  public RemoteSource(CloudProvider provider,String itemId,long size)
  {
    this(provider,itemId,size,BLOCK_SEQUENTIAL);
  }

  public RemoteSource(CloudProvider provider,String itemId,long size,int blockSize)
  {
    this.provider=provider;
    this.itemId=itemId;
    this.total=size;
    this.blockSize=blockSize>0?blockSize:BLOCK_SEQUENTIAL;
    this.maxBlocks=Math.max(8,MAXBYTES/this.blockSize);
  }

  /** Number of HTTP requests issued so far, for diagnostics. */
  public int getRequestCount(){return requestCount;}

  /**
   * Opens this remote file for MP4 box parsing.
   *
   * Deliberately here rather than at the call sites: passing a RemoteSource where a
   * DataSource is expected makes the verifier resolve the isoparser types when the
   * calling class is loaded, and jspCompile builds its classpath without the ISO-MP4
   * Parser library, so JSP validation of any bean doing that would fail. Keeping the
   * conversion inside this package leaves the beans free of it.
   */
  public com.coremedia.iso.IsoFile openIso() throws IOException
  {
    return new com.coremedia.iso.IsoFile(this);
  }

  public long size(){return total;}
  public long position(){return pos;}
  public void position(long newPosition){pos=newPosition;}

  private CloudLink activeLink() throws IOException
  {
    if (link==null || link.isExpired())link=provider.getDownloadLink(itemId);
    return link;
  }

  /** Fetches one aligned block, re-acquiring the link once if it has gone stale. */
  private byte[] fetchBlock(long blockIndex) throws IOException
  {
    byte[] cached=blocks.get(blockIndex);
    if (cached!=null)return cached;

    long offset=blockIndex*blockSize;
    if (offset>=total)return new byte[0];
    int length=(int)Math.min(blockSize,total-offset);

    CloudLink current=activeLink();
    requestCount++;
    byte[] data=HttpTool.getRange(current.getURL(),current.getHeaders(),offset,length);
    if (data==null)
    {
      int status=HttpTool.getLastStatus();
      // 401/403 mean the credential expired, 404/410 that the temporary URL did.
      if (status==401 || status==403 || status==404 || status==410)
      {
        link=provider.getDownloadLink(itemId);
        data=HttpTool.getRange(link.getURL(),link.getHeaders(),offset,length);
      }
      if (data==null)throw new IOException("Remote read failed at "+offset+" ("+status+")");
    }
    blocks.put(blockIndex,data);
    return data;
  }

  /** Reads up to length bytes at an absolute offset, spanning blocks as needed. */
  public int readAt(long offset,byte[] target,int targetOffset,int length) throws IOException
  {
    if (offset>=total)return -1;
    int done=0;
    while (done<length)
    {
      long absolute=offset+done;
      if (absolute>=total)break;
      long blockIndex=absolute/blockSize;
      int within=(int)(absolute-(blockIndex*blockSize));
      byte[] block=fetchBlock(blockIndex);
      if (block.length<=within)break;
      int take=Math.min(length-done,block.length-within);
      System.arraycopy(block,within,target,targetOffset+done,take);
      done+=take;
    }
    return done==0?-1:done;
  }

  public int read(ByteBuffer buffer) throws IOException
  {
    int want=buffer.remaining();
    if (want==0)return 0;
    byte[] temp=new byte[want];
    int read=readAt(pos,temp,0,want);
    if (read<=0)return -1;
    buffer.put(temp,0,read);
    pos+=read;
    return read;
  }

  public ByteBuffer map(long startPosition,long size) throws IOException
  {
    // isoparser only maps small boxes; it skips mdat with position() instead. Guard
    // anyway so a malformed file cannot ask for an enormous allocation.
    if (size>MAXBYTES)throw new IOException("Refusing to map "+size+" bytes remotely");
    byte[] target=new byte[(int)size];
    int read=readAt(startPosition,target,0,(int)size);
    if (read<size)throw new IOException("Short remote map at "+startPosition);
    return ByteBuffer.wrap(target);
  }

  public long transferTo(long position,long count,WritableByteChannel sink) throws IOException
  {
    long done=0;
    byte[] buffer=new byte[blockSize];
    while (done<count)
    {
      int take=(int)Math.min(buffer.length,count-done);
      int read=readAt(position+done,buffer,0,take);
      if (read<=0)break;
      sink.write(ByteBuffer.wrap(buffer,0,read));
      done+=read;
    }
    return done;
  }

  public void close()
  {
    blocks.clear();
  }
}
