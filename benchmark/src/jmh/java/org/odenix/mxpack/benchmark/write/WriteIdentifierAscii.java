/*
 * Copyright 2024 the MxPack project authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.odenix.mxpack.benchmark.write;

import java.io.IOException;
import net.jqwik.api.Arbitraries;

@SuppressWarnings("unused")
public class WriteIdentifierAscii extends WriteValues {
  String[] values;

  @Override
  void generate256Values() {
    values =
        Arbitraries.strings()
            .ofMinLength(2)
            .ofMaxLength(20)
            .ascii()
            .array(String[].class)
            .ofSize(256)
            .sample();
  }

  @Override
  void writeValue(int index) throws IOException {
    writer.writeIdentifier(values[index]);
  }

  @Override
  void writeValueMp(int index) throws IOException {
    packer.packString(values[index]);
  }
}
