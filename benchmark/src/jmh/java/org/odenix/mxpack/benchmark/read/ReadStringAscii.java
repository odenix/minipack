/*
 * Copyright 2024 the MxPack project authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.odenix.mxpack.benchmark.read;

import java.io.IOException;
import net.jqwik.api.Arbitraries;
import org.odenix.mxpack.core.MessageWriter;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.infra.Blackhole;

@SuppressWarnings("unused")
public class ReadStringAscii extends ReadValue {
  @Param({"10", "100", "1000"})
  int length;

  @Override
  void writeValue(MessageWriter writer) throws IOException {
    var value = Arbitraries.strings().ofLength(length).ascii().sample();
    writer.write(value);
  }

  @Override
  void readValue(Blackhole hole) throws IOException {
    hole.consume(reader.readString());
  }

  @Override
  void readValueMp(Blackhole hole) throws IOException {
    hole.consume(unpacker.unpackString());
  }
}
