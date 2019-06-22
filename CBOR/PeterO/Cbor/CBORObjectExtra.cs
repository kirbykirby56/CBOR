/*
Written by Peter O. in 2013.
Any copyright is dedicated to the Public Domain.
http://creativecommons.org/publicdomain/zero/1.0/
If you like this, you should donate to Peter O.
at: http://peteroupc.github.io/
 */
using System;
using System.IO;
using PeterO.Numbers;

namespace PeterO.Cbor {
  // Contains extra methods placed separately
  // because they are not CLS-compliant or they
  // are specific to the .NET framework.
  public sealed partial class CBORObject {
    ///
  /// <summary>Converts this object to a 16-bit unsigned integer. The return value will
  /// be truncated as necessary.
  /// </summary><returns>A 16-bit unsigned integer.
  /// </returns><exception cref='T:System.OverflowException'>This object's value is outside the range of a 16-bit unsigned integer.
  /// </exception>
  ///
    [CLSCompliant(false)]
    public ushort AsUInt16() {
      int v = this.AsInt32();
      if (v > UInt16.MaxValue || v < 0) {
        throw new OverflowException("This object's value is out of range");
      }
      return (ushort)v;
    }

    ///
  /// <summary>Converts this object to a 32-bit unsigned integer. The return value will
  /// be truncated as necessary.
  /// </summary><returns>A 32-bit unsigned integer.
  /// </returns><exception cref='T:System.OverflowException'>This object's value is outside the range of a 32-bit unsigned integer.
  /// </exception>
  ///
    [CLSCompliant(false)]
    public uint AsUInt32() {
      ulong v = this.AsUInt64();
      if (v > UInt32.MaxValue) {
        throw new OverflowException("This object's value is out of range");
      }
      return (uint)v;
    }

    ///
  /// <summary>Converts this object to an 8-bit signed integer.
  /// </summary><returns>An 8-bit signed integer.
  /// </returns>
  ///
    [CLSCompliant(false)]
    public sbyte AsSByte() {
      int v = this.AsInt32();
      if (v > SByte.MaxValue || v < SByte.MinValue) {
        throw new OverflowException("This object's value is out of range");
      }
      return (sbyte)v;
    }

    ///
  /// <summary>Writes a CBOR major type number and an integer 0 or greater associated
  /// with it to a data stream, where that integer is passed to this method as a
  /// 32-bit unsigned integer. This is a low-level method that is useful for
  /// implementing custom CBOR encoding methodologies. This method encodes the
  /// given major type and value in the shortest form allowed for the major
  /// type.
  /// </summary><param name='outputStream'>A writable data stream.
  /// </param><param name='majorType'>The CBOR major type to write. This is a number from 0 through 7 as
  /// follows. 0: integer 0 or greater; 1: negative integer; 2: byte string; 3:
  /// UTF-8 text string; 4: array; 5: map; 6: tag; 7: simple value. See RFC 7049
  /// for details on these major types.
  /// </param><param name='value'>An integer 0 or greater associated with the major type, as follows. 0:
  /// integer 0 or greater; 1: the negative integer's absolute value is 1 plus
  /// this number; 2: length in bytes of the byte string; 3: length in bytes of
  /// the UTF-8 text string; 4: number of items in the array; 5: number of
  /// key-value pairs in the map; 6: tag number; 7: simple value number, which
  /// must be in the interval [0, 23] or [32, 255].
  /// </param><returns>The number of bytes ordered to be written to the data stream.
  /// </returns><exception cref='T:System.ArgumentNullException'>The parameter
  /// <paramref name='outputStream'/>
  /// is null.
  /// </exception>
  ///
    [CLSCompliant(false)]
    public static int WriteValue(
  Stream outputStream,
  int majorType,
  uint value) {
      if (outputStream == null) {
        throw new ArgumentNullException(nameof(outputStream));
      }
      return WriteValue(outputStream, majorType, (long)value);
    }

    ///
  /// <summary>Writes a CBOR major type number and an integer 0 or greater associated
  /// with it to a data stream, where that integer is passed to this method as a
  /// 64-bit unsigned integer. This is a low-level method that is useful for
  /// implementing custom CBOR encoding methodologies. This method encodes the
  /// given major type and value in the shortest form allowed for the major
  /// type.
  /// </summary><param name='outputStream'>A writable data stream.
  /// </param><param name='majorType'>The CBOR major type to write. This is a number from 0 through 7 as
  /// follows. 0: integer 0 or greater; 1: negative integer; 2: byte string; 3:
  /// UTF-8 text string; 4: array; 5: map; 6: tag; 7: simple value. See RFC 7049
  /// for details on these major types.
  /// </param><param name='value'>An integer 0 or greater associated with the major type, as follows. 0:
  /// integer 0 or greater; 1: the negative integer's absolute value is 1 plus
  /// this number; 2: length in bytes of the byte string; 3: length in bytes of
  /// the UTF-8 text string; 4: number of items in the array; 5: number of
  /// key-value pairs in the map; 6: tag number; 7: simple value number, which
  /// must be in the interval [0, 23] or [32, 255].
  /// </param><returns>The number of bytes ordered to be written to the data stream.
  /// </returns><exception cref='T:System.ArgumentException'>The parameter
  /// <paramref name='majorType'/>
  /// is 7 and value is greater than 255.
  /// </exception><exception cref='T:System.ArgumentNullException'>The parameter
  /// <paramref name='outputStream'/>
  /// is null.
  /// </exception>
  ///
    [CLSCompliant(false)]
    public static int WriteValue(
  Stream outputStream,
  int majorType,
  ulong value) {
      if (outputStream == null) {
        throw new ArgumentNullException(nameof(outputStream));
      }
      if (value <= Int64.MaxValue) {
        return WriteValue(outputStream, majorType, (long)value);
      } else {
        if (majorType < 0) {
          throw new ArgumentException("majorType (" + majorType +
            ") is less than 0");
        }
        if (majorType > 7) {
          throw new ArgumentException("majorType (" + majorType +
            ") is more than 7");
        }
        if (majorType == 7) {
          throw new ArgumentException("majorType is 7 and value is greater than 255");
        }
        byte[] bytes = { (byte)(27 | (majorType << 5)), (byte)((value >>
          56) & 0xff),
        (byte)((value >> 48) & 0xff), (byte)((value >> 40) & 0xff),
        (byte)((value >> 32) & 0xff), (byte)((value >> 24) & 0xff),
        (byte)((value >> 16) & 0xff), (byte)((value >> 8) & 0xff),
        (byte)(value & 0xff), };
        outputStream.Write(bytes, 0, bytes.Length);
        return bytes.Length;
      }
    }

    private static EInteger DecimalToEInteger(decimal dec) {
      return ((EDecimal)dec).ToEInteger();
    }

    private static decimal ExtendedRationalToDecimal(ERational
      extendedNumber) {
      return (decimal)extendedNumber;
    }

    private static decimal ExtendedDecimalToDecimal(EDecimal
      extendedNumber) {
      return (decimal)extendedNumber;
    }

    ///
  /// <summary>Converts this object to a .NET decimal.
  /// </summary><returns>The closest big integer to this object.
  /// </returns><exception cref='T:System.InvalidOperationException'>This object's type is not a number type.
  /// </exception><exception cref='T:System.OverflowException'>This object's value exceeds the range of a .NET decimal.
  /// </exception>
  ///
    [CLSCompliant(false)]
    public decimal AsDecimal() {
      return (this.ItemType == CBORObjectTypeInteger) ?
        ((decimal)(long)this.ThisItem) : ((this.ItemType ==
        CBORObjectTypeExtendedRational) ?
        ExtendedRationalToDecimal((ERational)this.ThisItem) :
        ExtendedDecimalToDecimal(this.AsEDecimal()));
    }

    ///
  /// <summary>Converts this object to a 64-bit unsigned integer. Non-integer values are
  /// truncated to an integer.
  /// </summary><returns>The closest big integer to this object.
  /// </returns><exception cref='T:System.InvalidOperationException'>This object's type is not a number type.
  /// </exception><exception cref='T:System.OverflowException'>This object's value exceeds the range of a 64-bit unsigned integer.
  /// </exception>
  ///
    [CLSCompliant(false)]
    public ulong AsUInt64() {
      ICBORNumber cn = NumberInterfaces[this.ItemType];
      if (cn == null) {
        throw new InvalidOperationException("Not a number type");
      }
      EInteger bigint = cn.AsEInteger(this.ThisItem);
      if (bigint.Sign < 0 || !bigint.CanFitInInt64()) {
        throw new OverflowException("This object's value is out of range");
      }
      return (ulong)bigint;
    }

    ///
  /// <summary>Writes an 8-bit signed integer in CBOR format to a data stream.
  /// </summary><param name='value'>The parameter
  /// <paramref name='value'/>
  /// is an 8-bit signed integer.
  /// </param><param name='stream'>A writable data stream.
  /// </param>
  ///
    [CLSCompliant(false)]
    public static void Write(sbyte value, Stream stream) {
      Write((long)value, stream);
    }

    ///
  /// <summary>Writes a 64-bit unsigned integer in CBOR format to a data stream.
  /// </summary><param name='value'>A 64-bit unsigned integer.
  /// </param><param name='stream'>A writable data stream.
  /// </param><exception cref='T:System.ArgumentNullException'>The parameter
  /// <paramref name='stream'/>
  /// is null.
  /// </exception>
  ///
    [CLSCompliant(false)]
    public static void Write(ulong value, Stream stream) {
      if (stream == null) {
        throw new ArgumentNullException(nameof(stream));
      }
      if (value <= Int64.MaxValue) {
        Write((long)value, stream);
      } else {
        stream.WriteByte((byte)27);
        stream.WriteByte((byte)((value >> 56) & 0xff));
        stream.WriteByte((byte)((value >> 48) & 0xff));
        stream.WriteByte((byte)((value >> 40) & 0xff));
        stream.WriteByte((byte)((value >> 32) & 0xff));
        stream.WriteByte((byte)((value >> 24) & 0xff));
        stream.WriteByte((byte)((value >> 16) & 0xff));
        stream.WriteByte((byte)((value >> 8) & 0xff));
        stream.WriteByte((byte)(value & 0xff));
      }
    }

    ///
  /// <summary>Converts a .NET decimal to a CBOR object.
  /// </summary><param name='value'>The parameter
  /// <paramref name='value'/>
  /// is a Decimal object.
  /// </param><returns>A CBORObject object with the same value as the .NET decimal.
  /// </returns>
  ///
    public static CBORObject FromObject(decimal value) {
      return FromObject((EDecimal)value);
    }

    ///
  /// <summary>Writes a 32-bit unsigned integer in CBOR format to a data stream.
  /// </summary><param name='value'>A 32-bit unsigned integer.
  /// </param><param name='stream'>A writable data stream.
  /// </param>
  ///
    [CLSCompliant(false)]
    public static void Write(uint value, Stream stream) {
      Write((ulong)value, stream);
    }

    ///
  /// <summary>Writes a 16-bit unsigned integer in CBOR format to a data stream.
  /// </summary><param name='value'>A 16-bit unsigned integer.
  /// </param><param name='stream'>A writable data stream.
  /// </param>
  ///
    [CLSCompliant(false)]
    public static void Write(ushort value, Stream stream) {
      Write((ulong)value, stream);
    }

    ///
  /// <summary>Converts a signed 8-bit integer to a CBOR object.
  /// </summary><param name='value'>The parameter
  /// <paramref name='value'/>
  /// is an 8-bit signed integer.
  /// </param><returns>A CBORObject object.
  /// </returns>
  ///
    [CLSCompliant(false)]
    public static CBORObject FromObject(sbyte value) {
      return FromObject((long)value);
    }

    private static EInteger UInt64ToEInteger(ulong value) {
      var data = new byte[9];
      ulong uvalue = value;
      data[0] = (byte)(uvalue & 0xff);
      data[1] = (byte)((uvalue >> 8) & 0xff);
      data[2] = (byte)((uvalue >> 16) & 0xff);
      data[3] = (byte)((uvalue >> 24) & 0xff);
      data[4] = (byte)((uvalue >> 32) & 0xff);
      data[5] = (byte)((uvalue >> 40) & 0xff);
      data[6] = (byte)((uvalue >> 48) & 0xff);
      data[7] = (byte)((uvalue >> 56) & 0xff);
      data[8] = (byte)0;
      return EInteger.FromBytes(data, true);
    }

    ///
  /// <summary>Converts a 64-bit unsigned integer to a CBOR object.
  /// </summary><param name='value'>A 64-bit unsigned integer.
  /// </param><returns>A CBORObject object.
  /// </returns>
  ///
    [CLSCompliant(false)]
    public static CBORObject FromObject(ulong value) {
      return CBORObject.FromObject(UInt64ToEInteger(value));
    }

    ///
  /// <summary>Converts a 32-bit unsigned integer to a CBOR object.
  /// </summary><param name='value'>A 32-bit unsigned integer.
  /// </param><returns>A CBORObject object.
  /// </returns>
  ///
    [CLSCompliant(false)]
    public static CBORObject FromObject(uint value) {
      return FromObject((long)(Int64)value);
    }

    ///
  /// <summary>Converts a 16-bit unsigned integer to a CBOR object.
  /// </summary><param name='value'>A 16-bit unsigned integer.
  /// </param><returns>A CBORObject object.
  /// </returns>
  ///
    [CLSCompliant(false)]
    public static CBORObject FromObject(ushort value) {
      return FromObject((long)(Int64)value);
    }

    ///
  /// <summary>Generates a CBOR object from an arbitrary object and gives the resulting
  /// object a tag.
  /// </summary><param name='o'>The parameter
  /// <paramref name='o'/>
  /// is an arbitrary object.
  /// </param><param name='tag'>A 64-bit integer that specifies a tag number. The tag number 55799 can be
  /// used to mark a "self-described CBOR" object. This document does not
  /// attempt to list all CBOR tags and their meanings. An up-to-date list can
  /// be found at the CBOR Tags registry maintained by the Internet Assigned
  /// Numbers Authority (
  /// <i>
  /// iana.org/assignments/cbor-tags
  /// </i>
  /// ).
  /// </param><returns>A CBOR object where the object
  /// <paramref name='o'/>
  /// is converted to a CBOR object and given the tag
  /// <paramref name='tag'/>
  /// .
  /// </returns>
  ///
    [CLSCompliant(false)]
    public static CBORObject FromObjectAndTag(Object o, ulong tag) {
      return FromObjectAndTag(o, UInt64ToEInteger(tag));
    }

    ///
  /// <summary><para>Converts this CBOR object to an object of an arbitrary type. See
  /// <see cref='M:PeterO.Cbor.CBORObject.ToObject(System.Type)'/>
  /// for further information.
  /// </para>
  /// </summary><typeparam name='T'>The type, class, or interface that this method's return value will belong
  /// to.
  /// <b>Note:</b> For security reasons, an application should not base this parameter on
  /// user input or other externally supplied data. Whenever possible, this
  /// parameter should be either a type specially handled by this method (such
  /// as
  /// <c>int</c> or
  /// <c>String</c> ) or a plain-old-data type (POCO or POJO type) within the control of the
  /// application. If the plain-old-data type references other data types, those
  /// types should likewise meet either criterion above.
  /// </typeparam><returns>The converted object.
  /// </returns><exception cref='T:System.NotSupportedException'>The given type "T", or this object's CBOR type, is not supported.
  /// </exception>
  ///
    public T ToObject<T>() {
      return (T)this.ToObject(typeof(T));
    }

    ///
  /// <summary><para>Converts this CBOR object to an object of an arbitrary type. See
  /// <see cref='M:PeterO.Cbor.CBORObject.ToObject(System.Type)'/>
  /// for further information.
  /// </para>
  /// </summary><param name='mapper'>This parameter controls which data types are eligible for Plain-Old-Data
  /// deserialization and includes custom converters from CBOR objects to
  /// certain data types.
  /// </param><typeparam name='T'>The type, class, or interface that this method's return value will belong
  /// to.
  /// <b>Note:</b> For security reasons, an application should not base this parameter on
  /// user input or other externally supplied data. Whenever possible, this
  /// parameter should be either a type specially handled by this method (such
  /// as
  /// <c>int</c> or
  /// <c>String</c> ) or a plain-old-data type (POCO or POJO type) within the control of the
  /// application. If the plain-old-data type references other data types, those
  /// types should likewise meet either criterion above.
  /// </typeparam><returns>The converted object.
  /// </returns><exception cref='T:System.NotSupportedException'>The given type "T", or this object's CBOR type, is not supported.
  /// </exception>
  ///
    public T ToObject<T>(CBORTypeMapper mapper) {
      return (T)this.ToObject(typeof(T), mapper);
    }

    ///
  /// <summary><para>Converts this CBOR object to an object of an arbitrary type. See
  /// <see cref='M:PeterO.Cbor.CBORObject.ToObject(System.Type)'/>
  /// for further information.
  /// </para>
  /// </summary><param name='options'>Specifies options for controlling deserialization of CBOR objects.
  /// </param><typeparam name='T'>The type, class, or interface that this method's return value will belong
  /// to.
  /// <b>Note:</b> For security reasons, an application should not base this parameter on
  /// user input or other externally supplied data. Whenever possible, this
  /// parameter should be either a type specially handled by this method (such
  /// as
  /// <c>int</c> or
  /// <c>String</c> ) or a plain-old-data type (POCO or POJO type) within the control of the
  /// application. If the plain-old-data type references other data types, those
  /// types should likewise meet either criterion above.
  /// </typeparam><returns>The converted object.
  /// </returns><exception cref='T:System.NotSupportedException'>The given type "T", or this object's CBOR type, is not supported.
  /// </exception>
  ///
    public T ToObject<T>(PODOptions options) {
      return (T)this.ToObject(typeof(T), options);
    }

    ///
  /// <summary><para>Converts this CBOR object to an object of an arbitrary type. See
  /// <see cref='M:PeterO.Cbor.CBORObject.ToObject(System.Type)'/>
  /// for further information.
  /// </para>
  /// </summary><param name='mapper'>This parameter controls which data types are eligible for Plain-Old-Data
  /// deserialization and includes custom converters from CBOR objects to
  /// certain data types.
  /// </param><param name='options'>Specifies options for controlling deserialization of CBOR objects.
  /// </param><typeparam name='T'>The type, class, or interface that this method's return value will belong
  /// to.
  /// <b>Note:</b> For security reasons, an application should not base this parameter on
  /// user input or other externally supplied data. Whenever possible, this
  /// parameter should be either a type specially handled by this method (such
  /// as
  /// <c>int</c> or
  /// <c>String</c> ) or a plain-old-data type (POCO or POJO type) within the control of the
  /// application. If the plain-old-data type references other data types, those
  /// types should likewise meet either criterion above.
  /// </typeparam><returns>The converted object.
  /// </returns><exception cref='T:System.NotSupportedException'>The given type "T", or this object's CBOR type, is not supported.
  /// </exception>
  ///
    public T ToObject<T>(CBORTypeMapper mapper, PODOptions options) {
      return (T)this.ToObject(typeof(T), mapper, options);
    }

    ///
  /// <summary>Adds two CBOR objects and returns their result.
  /// </summary><param name='a'>The parameter
  /// <paramref name='a'/>
  /// is a CBOR object.
  /// </param><param name='b'>The parameter
  /// <paramref name='b'/>
  /// is a CBOR object.
  /// </param><returns>The sum of the two objects.
  /// </returns>
  ///
    public static CBORObject operator +(CBORObject a, CBORObject b) {
      return Addition(a, b);
    }

    ///
  /// <summary>Subtracts a CBORObject object from a CBORObject object.
  /// </summary><param name='a'>The parameter
  /// <paramref name='a'/>
  /// is a CBOR object.
  /// </param><param name='b'>The parameter
  /// <paramref name='b'/>
  /// is a CBOR object.
  /// </param><returns>The difference of the two objects.
  /// </returns>
  ///
    public static CBORObject operator -(CBORObject a, CBORObject b) {
      return Subtract(a, b);
    }

    ///
  /// <summary>Multiplies a CBORObject object by the value of a CBORObject object.
  /// </summary><param name='a'>The parameter
  /// <paramref name='a'/>
  /// is a CBOR object.
  /// </param><param name='b'>The parameter
  /// <paramref name='b'/>
  /// is a CBOR object.
  /// </param><returns>The product of the two numbers.
  /// </returns>
  ///
    public static CBORObject operator *(CBORObject a, CBORObject b) {
      return Multiply(a, b);
    }

    ///
  /// <summary>Divides a CBORObject object by the value of a CBORObject object.
  /// </summary><param name='a'>The parameter
  /// <paramref name='a'/>
  /// is a CBOR object.
  /// </param><param name='b'>The parameter
  /// <paramref name='b'/>
  /// is a CBOR object.
  /// </param><returns>The quotient of the two objects.
  /// </returns>
  ///
    public static CBORObject operator /(CBORObject a, CBORObject b) {
      return Divide(a, b);
    }

    ///
  /// <summary>Finds the remainder that results when a CBORObject object is divided by
  /// the value of a CBORObject object.
  /// </summary><param name='a'>The parameter
  /// <paramref name='a'/>
  /// is a CBOR object.
  /// </param><param name='b'>The parameter
  /// <paramref name='b'/>
  /// is a CBOR object.
  /// </param><returns>The remainder of the two numbers.
  /// </returns>
  ///
    public static CBORObject operator %(CBORObject a, CBORObject b) {
      return Remainder(a, b);
    }
  }
}
