/*
 * Copyright 2024 the MxPack project authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.odenix.mxpack.core;

import java.io.IOException;
import org.odenix.mxpack.core.internal.CharsetStringEncoder;

/// Encodes values of type [T] and writes them to a [MessageSink].
///
/// @param <T> the type of values to encode
@FunctionalInterface
public interface MessageEncoder<T> {
  /// Returns a new message encoder that encodes strings.
  ///
  /// @return a new message encoder that encodes strings
  static MessageEncoder<CharSequence> ofString() {
    return new CharsetStringEncoder();
  }

  /// Encodes a value and writes it to a message sink.
  ///
  /// @param value the value to encode
  /// @param sink  the message sink to write to
  /// @throws IOException if an I/O error occurs
  void encode(T value, MessageSink sink) throws IOException;
}
