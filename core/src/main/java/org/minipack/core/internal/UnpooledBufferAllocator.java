/*
 * Copyright 2024 the minipack project authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.minipack.core.internal;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;

public final class UnpooledBufferAllocator extends AbstractBufferAllocator {
  public UnpooledBufferAllocator(AbstractBufferAllocator.Builder builder) {
    super(builder);
  }

  @Override
  public ByteBuffer byteBuffer(long minCapacity) {
    var capacity = checkCapacity(minCapacity);
    return preferDirect ? ByteBuffer.allocateDirect(capacity) : ByteBuffer.allocate(capacity);
  }

  @Override
  public CharBuffer charBuffer(long minCapacity) {
    var capacity = checkCharCapacity(minCapacity);
    return CharBuffer.allocate(capacity);
  }

  @Override
  public char[] charArray(long minLength) {
    var capacity = checkCharCapacity(minLength);
    return new char[capacity];
  }

  @Override
  public void release(ByteBuffer buffer) {} // nothing to do

  @Override
  public void release(CharBuffer buffer) {} // nothing to do

  @Override
  public void release(char[] buffer) {} // nothing to do

  @Override
  public void close() {} // nothing to do
}
