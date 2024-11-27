/*
 * Copyright 2024 the MiniPack contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.odenix.minipack.benchmark.read;

import java.io.IOException;
import net.jqwik.api.Arbitraries;
import io.github.odenix.minipack.core.MessageWriter;
import org.openjdk.jmh.infra.Blackhole;

@SuppressWarnings("unused")
public class ReadIdentifier extends ReadValues {
  @Override
  void write256Values(MessageWriter writer) throws IOException {
    var values = Arbitraries.strings().ofMinLength(2).ofMaxLength(20).list().ofSize(256).sample();
    for (var v : values) writer.write(v);
  }

  @Override
  void readValue(Blackhole hole) throws IOException {
    hole.consume(reader.readIdentifier());
  }

  @Override
  void readValueMp(Blackhole hole) throws IOException {
    hole.consume(unpacker.unpackString());
  }
}