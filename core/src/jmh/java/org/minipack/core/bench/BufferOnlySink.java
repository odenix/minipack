/*
 * Copyright 2024 the minipack project authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.minipack.core.bench;

import java.nio.ByteBuffer;
import org.minipack.core.BufferAllocator;
import org.minipack.core.MessageSink;

public class BufferOnlySink extends MessageSink {
  public BufferOnlySink(ByteBuffer buffer, BufferAllocator allocator) {
    super(allocator);
  }

  @Override
  protected void doWrite(ByteBuffer buffer) {}

  @Override
  protected void doWrite(ByteBuffer... buffers) {}

  @Override
  protected void doFlush() {}

  @Override
  protected void doClose() {}
}
