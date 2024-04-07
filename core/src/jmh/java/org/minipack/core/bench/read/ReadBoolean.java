/*
 * Copyright 2024 the minipack project authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.minipack.core.bench.read;

import java.io.IOException;
import java.util.Random;
import org.minipack.core.MessageWriter;
import org.openjdk.jmh.infra.Blackhole;

public class ReadBoolean extends ReadValues {
  @Override
  void writeValues(MessageWriter writer) throws IOException {
    var random = new Random();
    for (int i = 0; i < 256; i++) {
      writer.write(random.nextBoolean());
    }
  }

  @Override
  void readValue(Blackhole hole) throws IOException {
    hole.consume(reader.readBoolean());
  }

  @Override
  void readValueMp(Blackhole hole) throws IOException {
    hole.consume(unpacker.unpackBoolean());
  }
}
