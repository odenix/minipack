/*
 * Copyright 2024 the MiniPack contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.minipack.core.internal;

import java.nio.ByteBuffer;
import org.minipack.core.MessageSource;

/// A source provider that reads nothing.
public final class EmptySourceProvider implements MessageSource.Provider {
  @Override
  public int read(ByteBuffer buffer, int minLengthHint) {
    return -1;
  }

  @Override
  public void close() {} // nothing to do
}
