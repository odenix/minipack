/*
 * Copyright 2024 the MiniPack contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.minipack.java.internal;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import org.minipack.java.MessageSource;

/// A source provider that reads from an [InputStream].
public final class StreamSourceProvider implements MessageSource.Provider {
  private final InputStream stream;

  public StreamSourceProvider(InputStream stream) {
    this.stream = stream;
  }

  @Override
  public int read(ByteBuffer buffer, int minBytesHint) throws IOException {
    if (!buffer.hasArray()) {
      throw Exceptions.arrayBackedBufferRequired();
    }
    var maxBytesToRead =
        stream.available() >= minBytesHint
            ? Math.min(stream.available(), buffer.remaining())
            : buffer.remaining();
    var bytesRead =
        stream.read(buffer.array(), buffer.arrayOffset() + buffer.position(), maxBytesToRead);
    if (bytesRead > 0) {
      buffer.position(buffer.position() + bytesRead);
    }
    return bytesRead;
  }

  @Override
  public void close() throws IOException {
    stream.close();
  }

  @Override
  public void skip(int length, ByteBuffer buffer) throws IOException {
    if (length == 0) return;
    var remaining = buffer.remaining();
    if (length <= remaining) {
      buffer.position(buffer.position() + length);
      return;
    }
    buffer.position(buffer.limit());
    stream.skipNBytes(length - remaining);
  }
}