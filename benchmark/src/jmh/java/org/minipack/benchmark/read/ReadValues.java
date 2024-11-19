/*
 * Copyright 2024 the MiniPack contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.minipack.benchmark.read;

import java.io.IOException;
import java.nio.ByteBuffer;
import org.minipack.java.*;
import org.minipack.java.MessageWriter;
import org.msgpack.core.MessagePack;
import org.msgpack.core.MessageUnpacker;
import org.msgpack.core.buffer.ArrayBufferInput;
import org.msgpack.core.buffer.MessageBuffer;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

@State(Scope.Thread)
public abstract class ReadValues {
  BufferAllocator allocator;
  BufferAllocator.PooledByteBuffer pooledBuffer;
  ByteBuffer buffer;
  MessageReader reader;

  MessageBuffer messageBuffer;
  ArrayBufferInput bufferInput;
  MessageUnpacker unpacker;

  abstract void write256Values(MessageWriter writer) throws IOException;

  @CompilerControl(CompilerControl.Mode.INLINE)
  abstract void readValue(Blackhole hole) throws IOException;

  @CompilerControl(CompilerControl.Mode.INLINE)
  abstract void readValueMp(Blackhole hole) throws IOException;

  @Setup
  public void setUp() throws IOException {
    allocator = BufferAllocator.ofUnpooled();
    pooledBuffer = allocator.getByteBuffer(1024 * 16);
    buffer = pooledBuffer.value();
    var sink = MessageSink.ofDiscarding(options -> {}, pooledBuffer);
    var writer = MessageWriter.of(sink);
    write256Values(writer);
    buffer.flip();
    var source = MessageSource.of(pooledBuffer, options -> options.allocator(allocator));
    reader = MessageReader.of(source);

    messageBuffer = MessageBuffer.wrap(buffer.array());
    bufferInput = new ArrayBufferInput(messageBuffer);
    unpacker = MessagePack.newDefaultUnpacker(bufferInput);
  }

  @TearDown
  public void tearDown() {
    pooledBuffer.close();
    allocator.close();
  }

  @Benchmark
  @OperationsPerInvocation(256)
  public void run(Blackhole hole) throws IOException {
    buffer.clear();
    for (var i = 0; i < 256; i++) {
      readValue(hole);
    }
  }

  @Benchmark
  @OperationsPerInvocation(256)
  public void runMp(Blackhole hole) throws IOException {
    bufferInput.reset(messageBuffer);
    unpacker.reset(bufferInput);
    for (var i = 0; i < 256; i++) {
      readValueMp(hole);
    }
  }
}