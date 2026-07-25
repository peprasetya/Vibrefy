package id.prasetya.vibrefy.tools.cloud;

import java.io.IOException;

import id.prasetya.vibrefy.data.CloudItem;
import id.prasetya.vibrefy.data.CloudLink;

/**
 * Read-only view of one linked cloud account. Vibrefy never writes to the provider.
 */
public interface CloudProvider
{
  /** Single-char provider code, matching the convention used by the login config. */
  public char getKind();

  /** The mount name, which is also the URL segment after the '~'. */
  public String getLabel();

  /** Provider-specific id of the folder this mount is rooted at. */
  public String getRootId() throws IOException;

  public CloudItem[] listChildren(String folderId) throws IOException;

  /**
   * Resolves one named child. Separate from listChildren because Drive can answer it
   * with a single server-side query, which matters for sidecar thumbnail lookups.
   */
  public CloudItem findChild(String folderId,String name) throws IOException;

  public CloudItem getItem(String itemId) throws IOException;

  /** A URL the server can issue byte-range requests against. */
  public CloudLink getDownloadLink(String itemId) throws IOException;

  /** Account label shown in the UI, may be empty for key-based providers. */
  public String getAccountName();

  public boolean isValid();

  /** Human-readable reason the mount is not usable, or null when it is fine. */
  public String getError();
}
