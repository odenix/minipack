/*
 * Copyright 2024 the minipack project authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.translatenix.minipack;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.nio.charset.StandardCharsets;
import org.jspecify.annotations.Nullable;

/**
 * Reads messages encoded in the <a href="https://msgpack.org/">MessagePack</a> binary serialization
 * format.
 *
 * <p>To create a new {@code MessageReader}, use a {@linkplain #builder() builder}. To read a
 * message, call one of the {@code readXYZ()} methods. To peek at the next message type, call {@link
 * #nextType()}. If an error occurs when reading a value, a {@link ReaderException} is thrown.
 */
public final class MessageReader implements Closeable {
  private static final int MIN_BUFFER_CAPACITY = 9;
  private static final int DEFAULT_BUFFER_CAPACITY = 1 << 13;
  private static final int DEFAULT_MAX_ALLOCATOR_CAPACITY = 1 << 20;

  private final MessageSource source;
  private final ByteBuffer buffer;
  private final BufferAllocator allocator;

  /** A builder of {@link MessageReader}. */
  public static final class Builder {
    private @Nullable MessageSource source;
    private @Nullable ByteBuffer buffer;
    private @Nullable BufferAllocator allocator;
    private int maxAllocatorCapacity = DEFAULT_MAX_ALLOCATOR_CAPACITY;

    /** Sets the underlying source to read from. */
    public Builder source(MessageSource source) {
      this.source = source;
      return this;
    }

    /** Shorthand for {@code source(MessageSource.of(stream))}. */
    public Builder source(InputStream stream) {
      return source(MessageSource.of(stream));
    }

    /** Shorthand for {@code source(MessageSource.of(channel))}. */
    public Builder source(ReadableByteChannel channel) {
      return source(MessageSource.of(channel));
    }

    /**
     * Sets the buffer to use for reading from the underlying message {@linkplain MessageSource
     * source}. The buffer's {@linkplain ByteBuffer#capacity() capacity} determines the maximum
     * number of bytes that will be read at once from the source.
     *
     * <p>If not set, defaults to {@code ByteBuffer.allocate(8192)}.
     */
    public Builder buffer(ByteBuffer buffer) {
      this.buffer = buffer;
      return this;
    }

    /**
     * Sets the allocator to use for allocating additional {@linkplain ByteBuffer byte buffers}.
     *
     * <p>Currently, an additional byte buffer is only allocated if {@link #readString()} is called
     * and at least one of the following conditions holds:
     *
     * <ul>
     *   <li>The string is too large to fit into the regular {@linkplain #buffer(ByteBuffer) buffer}
     *       or a previously allocated additional buffer.
     *   <li>The regular {@linkplain #buffer(ByteBuffer) buffer} is not backed by an accessible
     *       {@linkplain ByteBuffer#array() array}.
     * </ul>
     */
    public Builder allocator(BufferAllocator allocator) {
      this.allocator = allocator;
      return this;
    }

    /**
     * Shorthand for {@code allocator(BufferAllocator.withCapacity(buffer.capacity() * 2,
     * maxCapacity))}, where {@code buffer} is the meassage reader's regular {@linkplain
     * #buffer(ByteBuffer) buffer}.
     *
     * @see #allocator(BufferAllocator)
     */
    public Builder allocatorCapacity(int maxCapacity) {
      maxAllocatorCapacity = maxCapacity;
      return this;
    }

    /** Creates a new {@code MessageReader} from this builder's current state. */
    public MessageReader build() {
      return new MessageReader(this);
    }
  }

  /** Creates a new {@code MessageWriter} builder. */
  public static Builder builder() {
    return new Builder();
  }

  private MessageReader(Builder builder) {
    if (builder.source == null) {
      throw Exceptions.sourceRequired();
    }
    this.source = builder.source;
    this.buffer =
        builder.buffer != null
            ? builder.buffer.position(0).limit(0)
            : ByteBuffer.allocate(DEFAULT_BUFFER_CAPACITY).limit(0);
    assert this.buffer.remaining() == 0;
    if (buffer.capacity() < MIN_BUFFER_CAPACITY) {
      throw Exceptions.bufferTooSmall(buffer.capacity(), MIN_BUFFER_CAPACITY);
    }
    this.allocator =
        builder.allocator != null
            ? builder.allocator
            : BufferAllocator.withCapacity(
                Math.min(builder.maxAllocatorCapacity, buffer.capacity() * 2),
                builder.maxAllocatorCapacity);
  }

  /** Returns the type of the next value to be read. */
  public ValueType nextType() {
    ensureRemaining(1);
    // don't change position
    return ValueFormat.toType(buffer.get(buffer.position()));
  }

  /** Reads a nil (null) value. */
  public void readNil() {
    var format = getByte();
    if (format != ValueFormat.NIL) {
      throw Exceptions.wrongJavaType(format, JavaType.VOID);
    }
  }

  /** Reads a boolean value. */
  public boolean readBoolean() {
    var format = getByte();
    return switch (format) {
      case ValueFormat.TRUE -> true;
      case ValueFormat.FALSE -> false;
      default -> throw Exceptions.wrongJavaType(format, JavaType.BOOLEAN);
    };
  }

  /** Reads an integer value that fits into a Java byte. */
  public byte readByte() {
    var format = getByte();
    return switch (format) {
      case ValueFormat.INT8 -> getByte();
      case ValueFormat.INT16 -> {
        var value = getShort();
        if (value >= Byte.MIN_VALUE && value <= Byte.MAX_VALUE) yield (byte) value;
        throw Exceptions.integerOverflow(value, format, JavaType.BYTE);
      }
      case ValueFormat.INT32 -> {
        var value = getInt();
        if (value >= Byte.MIN_VALUE && value <= Byte.MAX_VALUE) yield (byte) value;
        throw Exceptions.integerOverflow(value, format, JavaType.BYTE);
      }
      case ValueFormat.INT64 -> {
        var value = getLong();
        if (value >= Byte.MIN_VALUE && value <= Byte.MAX_VALUE) yield (byte) value;
        throw Exceptions.integerOverflow(value, format, JavaType.BYTE);
      }
      case ValueFormat.UINT8 -> {
        var value = getByte();
        if (value >= 0) yield value;
        throw Exceptions.integerOverflow(value, format, JavaType.BYTE);
      }
      case ValueFormat.UINT16 -> {
        var value = getShort();
        if (value >= 0 && value <= Byte.MAX_VALUE) yield (byte) value;
        throw Exceptions.integerOverflow(value, format, JavaType.BYTE);
      }
      case ValueFormat.UINT32 -> {
        var value = getInt();
        if (value >= 0 && value <= Byte.MAX_VALUE) yield (byte) value;
        throw Exceptions.integerOverflow(value, format, JavaType.BYTE);
      }
      case ValueFormat.UINT64 -> {
        var value = getLong();
        if (value >= 0 && value <= Byte.MAX_VALUE) yield (byte) value;
        throw Exceptions.integerOverflow(value, format, JavaType.BYTE);
      }
      default -> {
        if (ValueFormat.isFixInt(format)) yield format;
        throw Exceptions.wrongJavaType(format, JavaType.BYTE);
      }
    };
  }

  /** Reads an integer value that fits into a Java short. */
  public short readShort() {
    var format = getByte();
    return switch (format) {
      case ValueFormat.INT8 -> getByte();
      case ValueFormat.INT16 -> getShort();
      case ValueFormat.INT32 -> {
        var value = getInt();
        if (value >= Short.MIN_VALUE && value <= Short.MAX_VALUE) yield (short) value;
        throw Exceptions.integerOverflow(value, format, JavaType.SHORT);
      }
      case ValueFormat.INT64 -> {
        var value = getLong();
        if (value >= Short.MIN_VALUE && value <= Short.MAX_VALUE) yield (short) value;
        throw Exceptions.integerOverflow(value, format, JavaType.SHORT);
      }
      case ValueFormat.UINT8 -> getUByte();
      case ValueFormat.UINT16 -> {
        var value = getShort();
        if (value >= 0) yield value;
        throw Exceptions.integerOverflow(value, format, JavaType.SHORT);
      }
      case ValueFormat.UINT32 -> {
        var value = getInt();
        if (value >= 0 && value <= Short.MAX_VALUE) yield (short) value;
        throw Exceptions.integerOverflow(value, format, JavaType.SHORT);
      }
      case ValueFormat.UINT64 -> {
        var value = getLong();
        if (value >= 0 && value <= Short.MAX_VALUE) yield (short) value;
        throw Exceptions.integerOverflow(value, format, JavaType.SHORT);
      }
      default -> {
        if (ValueFormat.isFixInt(format)) yield format;
        throw Exceptions.wrongJavaType(format, JavaType.SHORT);
      }
    };
  }

  /** Reads an integer value that fits into a Java int. */
  public int readInt() {
    var format = getByte();
    return switch (format) {
      case ValueFormat.INT8 -> getByte();
      case ValueFormat.INT16 -> getShort();
      case ValueFormat.INT32 -> getInt();
      case ValueFormat.INT64 -> {
        var value = getLong();
        if (value >= Integer.MIN_VALUE && value <= Integer.MAX_VALUE) yield (int) value;
        throw Exceptions.integerOverflow(value, format, JavaType.INT);
      }
      case ValueFormat.UINT8 -> getUByte();
      case ValueFormat.UINT16 -> getUShort();
      case ValueFormat.UINT32 -> {
        var value = getInt();
        if (value >= 0) yield value;
        throw Exceptions.integerOverflow(value, format, JavaType.INT);
      }
      case ValueFormat.UINT64 -> {
        var value = getLong();
        if (value >= 0 && value <= Integer.MAX_VALUE) yield (int) value;
        throw Exceptions.integerOverflow(value, format, JavaType.INT);
      }
      default -> {
        if (ValueFormat.isFixInt(format)) yield format;
        throw Exceptions.wrongJavaType(format, JavaType.INT);
      }
    };
  }

  /** Reads an integer value that fits into a Java long. */
  public long readLong() {
    var format = getByte();
    return switch (format) {
      case ValueFormat.INT8 -> getByte();
      case ValueFormat.INT16 -> getShort();
      case ValueFormat.INT32 -> getInt();
      case ValueFormat.INT64 -> getLong();
      case ValueFormat.UINT8 -> getUByte();
      case ValueFormat.UINT16 -> getUShort();
      case ValueFormat.UINT32 -> getUInt();
      case ValueFormat.UINT64 -> {
        var value = getLong();
        if (value >= 0) yield value;
        throw Exceptions.integerOverflow(value, ValueFormat.UINT64, JavaType.LONG);
      }
      default -> {
        if (ValueFormat.isFixInt(format)) yield format;
        throw Exceptions.wrongJavaType(format, JavaType.INT);
      }
    };
  }

  /** Reads a floating point value that fits into a Java float. */
  public float readFloat() {
    var format = getByte();
    if (format == ValueFormat.FLOAT32) return getFloat();
    throw Exceptions.wrongJavaType(format, JavaType.FLOAT);
  }

  /** Reads a floating point value that fits into a Java double. */
  public double readDouble() {
    var format = getByte();
    if (format == ValueFormat.FLOAT64) return getDouble();
    throw Exceptions.wrongJavaType(format, JavaType.DOUBLE);
  }

  /**
   * Reads a string value.
   *
   * <p>The maximum UTF-8 string length is determined by the {@link BufferAllocator} that this
   * reader was built with. The default maximum UTF-8 string length is 1 MiB (1024 * 1024 bytes).
   *
   * <p>For a lower-level way to read strings, see {@link #readRawStringHeader()}.
   */
  public String readString() {
    var format = getByte();
    return switch (format) {
      case ValueFormat.STR8 -> readString(getLength8());
      case ValueFormat.STR16 -> readString(getLength16());
      case ValueFormat.STR32 -> readString(getLength32(ValueType.STRING));
      default -> {
        if (ValueFormat.isFixStr(format)) {
          yield readString(ValueFormat.getFixStrLength(format));
        } else {
          throw Exceptions.wrongJavaType(format, JavaType.STRING);
        }
      }
    };
  }

  /**
   * Starts reading an array value.
   *
   * <p>A call to this method MUST be followed by {@code n} calls that read the array's elements,
   * where {@code n} is the number of array elements returned by this method.
   */
  public int readArrayHeader() {
    var format = getByte();
    return switch (format) {
      case ValueFormat.ARRAY16 -> getLength16();
      case ValueFormat.ARRAY32 -> getLength32(ValueType.ARRAY);
      default -> {
        if (ValueFormat.isFixArray(format)) {
          yield ValueFormat.getFixArrayLength(format);
        }
        throw Exceptions.wrongJavaType(format, JavaType.MAP);
      }
    };
  }

  /**
   * Starts reading a map value.
   *
   * <p>A call to this method MUST be followed by {@code n*2} calls that alternately read the map's
   * keys and values, where {@code n} is the number of map entries returned by this method.
   */
  public int readMapHeader() {
    var format = getByte();
    return switch (format) {
      case ValueFormat.MAP16 -> getLength16();
      case ValueFormat.MAP32 -> getLength32(ValueType.MAP);
      default -> {
        if (ValueFormat.isFixMap(format)) {
          yield ValueFormat.getFixMapLength(format);
        }
        throw Exceptions.wrongJavaType(format, JavaType.MAP);
      }
    };
  }

  /**
   * Starts reading a binary value.
   *
   * <p>A call to this method MUST be followed by one or more calls to {@link #readPayload} that
   * read <i>exactly</i> the number of bytes returned by this method.
   */
  public int readBinaryHeader() {
    var format = getByte();
    return switch (format) {
      case ValueFormat.BIN8 -> getLength8();
      case ValueFormat.BIN16 -> getLength16();
      case ValueFormat.BIN32 -> getLength32(ValueType.BINARY);
      default -> throw Exceptions.wrongJavaType(format, JavaType.BINARY_HEADER);
    };
  }

  /**
   * Starts reading a string value.
   *
   * <p>A call to this method MUST be followed by one or more calls to {@link #readPayload} that
   * read <i>exactly</i> the number of bytes returned by this method.
   *
   * <p>This method is a low-level alternative to {@link #readString()}. It can be useful in the
   * following cases:
   *
   * <ul>
   *   <li>There is no need to convert a MessagePack string's UTF-8 payload to {@code
   *       java.lang.String}.
   *   <li>Full control over conversion from UTF-8 to {@code java.lang.String} is required.
   * </ul>
   */
  public int readRawStringHeader() {
    var format = getByte();
    return switch (format) {
      case ValueFormat.STR8 -> getLength8();
      case ValueFormat.STR16 -> getLength16();
      case ValueFormat.STR32 -> getLength32(ValueType.STRING);
      default -> {
        if (ValueFormat.isFixStr(format)) {
          yield ValueFormat.getFixStrLength(format);
        } else {
          throw Exceptions.wrongJavaType(format, JavaType.STRING);
        }
      }
    };
  }

  public ExtensionHeader readExtensionHeader() {
    var format = getByte();
    return switch (format) {
      case ValueFormat.FIXEXT1 -> new ExtensionHeader(1, getByte());
      case ValueFormat.FIXEXT2 -> new ExtensionHeader(2, getByte());
      case ValueFormat.FIXEXT4 -> new ExtensionHeader(4, getByte());
      case ValueFormat.FIXEXT8 -> new ExtensionHeader(8, getByte());
      case ValueFormat.FIXEXT16 -> new ExtensionHeader(16, getByte());
      case ValueFormat.EXT8 -> new ExtensionHeader(getLength8(), getByte());
      case ValueFormat.EXT16 -> new ExtensionHeader(getLength16(), getByte());
      case ValueFormat.EXT32 -> new ExtensionHeader(getLength32(ValueType.EXTENSION), getByte());
      default -> throw Exceptions.wrongJavaType(format, JavaType.EXTENSION_HEADER);
    };
  }

  /**
   * Reads {@linkplain ByteBuffer#remaining() remaining} bytes into the given buffer, starting at
   * the buffer's current {@linkplain ByteBuffer#position() position}.
   *
   * <p>This method is used together with {@link #readBinaryHeader()} or {@link
   * #readRawStringHeader()}.
   */
  public int readPayload(ByteBuffer buffer) {
    return readFromSource(buffer, 1);
  }

  public int readPayload(ByteBuffer buffer, int minBytes) {
    return readFromSource(buffer, minBytes);
  }

  /** Closes the underlying message {@linkplain MessageSource source}. */
  @Override
  public void close() {
    try {
      source.close();
    } catch (IOException e) {
      throw Exceptions.ioErrorClosingSource(e);
    }
  }

  private byte getByte() {
    ensureRemaining(1);
    return buffer.get();
  }

  private short getUByte() {
    ensureRemaining(1);
    return (short) (buffer.get() & 0xff);
  }

  private short getShort() {
    ensureRemaining(2);
    return buffer.getShort();
  }

  private int getUShort() {
    ensureRemaining(2);
    return buffer.getShort() & 0xffff;
  }

  private int getInt() {
    ensureRemaining(4);
    return buffer.getInt();
  }

  private long getUInt() {
    ensureRemaining(4);
    return buffer.getInt() & 0xffffffffL;
  }

  private long getLong() {
    ensureRemaining(8);
    return buffer.getLong();
  }

  private float getFloat() {
    ensureRemaining(4);
    return buffer.getFloat();
  }

  private double getDouble() {
    ensureRemaining(8);
    return buffer.getDouble();
  }

  private int getLength8() {
    ensureRemaining(1);
    return buffer.get() & 0xff;
  }

  private int getLength16() {
    ensureRemaining(2);
    return buffer.getShort() & 0xffff;
  }

  private int getLength32(ValueType valueType) {
    var length = getInt();
    if (length < 0) {
      throw Exceptions.lengthTooLarge(length, valueType);
    }
    return length;
  }

  private int readFromSource(ByteBuffer buffer, int minBytes) {
    assert minBytes > 0;
    assert minBytes <= buffer.remaining();
    var totalBytesRead = 0;
    try {
      while (totalBytesRead < minBytes) {
        var bytesRead = source.read(buffer, minBytes);
        if (bytesRead == -1) {
          throw Exceptions.prematureEndOfInput(minBytes, totalBytesRead);
        }
        totalBytesRead += bytesRead;
      }
    } catch (IOException e) {
      throw Exceptions.ioErrorReadingFromSource(e);
    }
    return totalBytesRead;
  }

  // non-private for testing
  byte nextFormat() {
    ensureRemaining(1);
    // don't change position
    return buffer.get(buffer.position());
  }

  private String readString(int length) {
    assert length >= 0;
    if (length <= buffer.capacity() && buffer.hasArray()) {
      ensureRemaining(length, buffer);
      var result = convertToString(buffer, length);
      buffer.position(buffer.position() + length);
      return result;
    }
    var tempBuffer = allocator.getArrayBackedBuffer(length).position(0).limit(length);
    var transferLength = Math.min(length, buffer.remaining());
    tempBuffer.put(0, buffer, buffer.position(), transferLength);
    if (transferLength < length) {
      tempBuffer.position(transferLength);
      readFromSource(tempBuffer, tempBuffer.remaining());
      tempBuffer.position(0);
    }
    buffer.position(buffer.position() + transferLength);
    return convertToString(tempBuffer, length);
  }

  private String convertToString(ByteBuffer buffer, int length) {
    assert buffer.hasArray();
    return new String(
        buffer.array(), buffer.arrayOffset() + buffer.position(), length, StandardCharsets.UTF_8);
  }

  private void ensureRemaining(int length) {
    ensureRemaining(length, buffer);
  }

  private void ensureRemaining(int length, ByteBuffer buffer) {
    int minBytes = length - buffer.remaining();
    if (minBytes > 0) {
      buffer.compact();
      readFromSource(buffer, minBytes);
      buffer.flip();
      assert buffer.remaining() >= length;
    }
  }
}
