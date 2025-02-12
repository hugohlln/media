/*
 * Copyright 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package androidx.media3.exoplayer.upstream;

import static androidx.media3.common.util.Assertions.checkArgument;
import static androidx.media3.common.util.Assertions.checkNotNull;
import static java.lang.annotation.ElementType.TYPE_USE;

import androidx.annotation.Nullable;
import androidx.annotation.StringDef;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.util.UnstableApi;
import com.google.common.collect.ImmutableMap;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.UUID;

/** Represents a configuration for the Common Media Client Data (CMCD) logging. */
@UnstableApi
public final class CmcdConfiguration {

  /**
   * Header keys SHOULD be allocated to one of the four defined header names based upon their
   * expected level of variability:
   *
   * <ul>
   *   <li>CMCD-Object: keys whose values vary with the object being requested.
   *   <li>CMCD-Request: keys whose values vary with each request.
   *   <li>CMCD-Session: keys whose values are expected to be invariant over the life of the
   *       session.
   *   <li>CMCD-Status: keys whose values do not vary with every request or object.
   * </ul>
   */
  @Retention(RetentionPolicy.SOURCE)
  @StringDef({KEY_CMCD_OBJECT, KEY_CMCD_REQUEST, KEY_CMCD_SESSION, KEY_CMCD_STATUS})
  @Documented
  @Target(TYPE_USE)
  public @interface HeaderKey {}

  /** Indicates that the annotated element represents a CMCD key. */
  @Retention(RetentionPolicy.SOURCE)
  @StringDef({
    KEY_BITRATE,
    KEY_BUFFER_LENGTH,
    KEY_CONTENT_ID,
    KEY_SESSION_ID,
    KEY_MAXIMUM_REQUESTED_BITRATE
  })
  @Documented
  @Target(TYPE_USE)
  public @interface CmcdKey {}

  /** Maximum length for ID fields. */
  public static final int MAX_ID_LENGTH = 64;

  public static final String KEY_CMCD_OBJECT = "CMCD-Object";
  public static final String KEY_CMCD_REQUEST = "CMCD-Request";
  public static final String KEY_CMCD_SESSION = "CMCD-Session";
  public static final String KEY_CMCD_STATUS = "CMCD-Status";
  public static final String KEY_BITRATE = "br";
  public static final String KEY_BUFFER_LENGTH = "bl";
  public static final String KEY_CONTENT_ID = "cid";
  public static final String KEY_SESSION_ID = "sid";
  public static final String KEY_MAXIMUM_REQUESTED_BITRATE = "rtp";

  /**
   * Factory for {@link CmcdConfiguration} instances.
   *
   * <p>Implementations must not make assumptions about which thread called their methods; and must
   * be thread-safe.
   */
  public interface Factory {
    /**
     * Creates a {@link CmcdConfiguration} based on the provided {@link MediaItem}.
     *
     * @param mediaItem The {@link MediaItem} from which to create the CMCD configuration.
     * @return A {@link CmcdConfiguration} instance.
     */
    CmcdConfiguration createCmcdConfiguration(MediaItem mediaItem);

    /**
     * The default factory implementation.
     *
     * <p>It creates a {@link CmcdConfiguration} by generating a random session ID and using the
     * content ID from {@link MediaItem#mediaId} (or {@link MediaItem#DEFAULT_MEDIA_ID} if the media
     * item does not have a {@link MediaItem#mediaId} defined).
     *
     * <p>It also utilises a default {@link RequestConfig} implementation that enables all available
     * keys, provides empty custom data, and sets the maximum requested bitrate to {@link
     * C#RATE_UNSET_INT}.
     */
    CmcdConfiguration.Factory DEFAULT =
        mediaItem ->
            new CmcdConfiguration(
                /* sessionId= */ UUID.randomUUID().toString(),
                /* contentId= */ mediaItem.mediaId != null
                    ? mediaItem.mediaId
                    : MediaItem.DEFAULT_MEDIA_ID,
                new RequestConfig() {});
  }

  /**
   * Represents configuration which can vary on each request.
   *
   * <p>Implementations must not make assumptions about which thread called their methods; and must
   * be thread-safe.
   */
  public interface RequestConfig {
    /**
     * Checks whether the specified key is allowed in CMCD logging. By default, all keys are
     * allowed.
     *
     * @param key The key to check.
     * @return Whether the key is allowed.
     */
    default boolean isKeyAllowed(@CmcdKey String key) {
      return true;
    }

    /**
     * Retrieves the custom data associated with CMCD logging.
     *
     * <p>By default, no custom data is provided.
     *
     * <p>The key should belong to the {@link HeaderKey}. The value should consist of key-value
     * pairs separated by commas. If the value contains one of the keys defined in the {@link
     * CmcdKey} list, then this key should not be {@linkplain #isKeyAllowed(String) allowed},
     * otherwise the key could be included twice in the produced log.
     *
     * <p>Example:
     *
     * <ul>
     *   <li>CMCD-Request:customField1=25400
     *   <li>CMCD-Object:customField2=3200,customField3=4004,customField4=v,customField5=6000
     *   <li>CMCD-Status:customField6,customField7=15000
     *   <li>CMCD-Session:customField8="6e2fb550-c457-11e9-bb97-0800200c9a66"
     * </ul>
     *
     * @return An {@link ImmutableMap} containing the custom data.
     */
    default ImmutableMap<@HeaderKey String, String> getCustomData() {
      return ImmutableMap.of();
    }

    /**
     * Returns the maximum throughput requested in kbps, or {@link C#RATE_UNSET_INT} if the maximum
     * throughput is unknown in which case the maximum throughput will not be logged upstream.
     *
     * @param throughputKbps The throughput in kbps of the audio or video object being requested.
     * @return The maximum throughput requested in kbps.
     */
    default int getRequestedMaximumThroughputKbps(int throughputKbps) {
      return C.RATE_UNSET_INT;
    }
  }

  /**
   * A GUID identifying the current playback session, or {@code null} if unset.
   *
   * <p>A playback session typically ties together segments belonging to a single media asset.
   * Maximum length is 64 characters.
   */
  @Nullable public final String sessionId;
  /**
   * A GUID identifying the current content, or {@code null} if unset.
   *
   * <p>This value is consistent across multiple different sessions and devices and is defined and
   * updated at the discretion of the service provider. Maximum length is 64 characters.
   */
  @Nullable public final String contentId;
  /** Dynamic request specific configuration. */
  public final RequestConfig requestConfig;

  /** Creates an instance. */
  public CmcdConfiguration(
      @Nullable String sessionId, @Nullable String contentId, RequestConfig requestConfig) {
    checkArgument(sessionId == null || sessionId.length() <= MAX_ID_LENGTH);
    checkArgument(contentId == null || contentId.length() <= MAX_ID_LENGTH);
    checkNotNull(requestConfig);
    this.sessionId = sessionId;
    this.contentId = contentId;
    this.requestConfig = requestConfig;
  }

  /**
   * Whether logging bitrate is allowed based on the {@linkplain RequestConfig request
   * configuration}.
   */
  public boolean isBitrateLoggingAllowed() {
    return requestConfig.isKeyAllowed(KEY_BITRATE);
  }

  /**
   * Whether logging buffer length is allowed based on the {@linkplain RequestConfig request
   * configuration}.
   */
  public boolean isBufferLengthLoggingAllowed() {
    return requestConfig.isKeyAllowed(KEY_BUFFER_LENGTH);
  }

  /**
   * Whether logging content ID is allowed based on the {@linkplain RequestConfig request
   * configuration}.
   */
  public boolean isContentIdLoggingAllowed() {
    return requestConfig.isKeyAllowed(KEY_CONTENT_ID);
  }

  /**
   * Whether logging session ID is allowed based on the {@linkplain RequestConfig request
   * configuration}.
   */
  public boolean isSessionIdLoggingAllowed() {
    return requestConfig.isKeyAllowed(KEY_SESSION_ID);
  }

  /**
   * Whether logging maximum requested throughput is allowed based on the {@linkplain RequestConfig
   * request configuration}.
   */
  public boolean isMaximumRequestThroughputLoggingAllowed() {
    return requestConfig.isKeyAllowed(KEY_MAXIMUM_REQUESTED_BITRATE);
  }
}
