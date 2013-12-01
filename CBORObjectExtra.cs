/*
Written in 2013 by Peter O.
Any copyright is dedicated to the Public Domain.
http://creativecommons.org/publicdomain/zero/1.0/
If you like this, you should donate to Peter O.
at: http://upokecenter.com/d/
 */
using System;
using System.Collections.Generic;
using System.Globalization;
using System.IO;
using System.Numerics;
using System.Text;
namespace PeterO {
  // Contains extra methods placed separately
  // because they are not CLS-compliant or they
  // are specific to the .NET framework.
  public sealed partial class CBORObject {
    /// <summary> </summary>
    /// <returns></returns>
    /// <remarks/>
    [CLSCompliant(false)]
    public ushort AsUInt16() {
      int v = AsInt32();
      if (v > UInt16.MaxValue || v < 0)
        throw new OverflowException("This object's value is out of range");
      return (ushort)v;
    }
    /// <summary> </summary>
    /// <returns></returns>
    /// <remarks/>
    [CLSCompliant(false)]
    public uint AsUInt32() {
      ulong v = AsUInt64();
      if (v > UInt32.MaxValue)
        throw new OverflowException("This object's value is out of range");
      return (uint)v;
    }
    /// <summary> </summary>
    /// <returns></returns>
    /// <remarks/>
    [CLSCompliant(false)]
    public sbyte AsSByte() {
      int v = AsInt32();
      if (v > SByte.MaxValue || v < SByte.MinValue)
        throw new OverflowException("This object's value is out of range");
      return (sbyte)v;
    }
    private static decimal EncodeDecimal(BigInteger bigmant,
                                  int scale, bool neg) {
      if (scale < 0 || scale > 28)
        throw new ArgumentOutOfRangeException("scale");
      int a = unchecked((int)(long)(bigmant & 0xFFFFFFFFL));
      int b = unchecked((int)(long)((bigmant >> 32) & 0xFFFFFFFFL));
      int c = unchecked((int)(long)((bigmant >> 64) & 0xFFFFFFFFL));
      int d = (scale << 16);
      if (neg) d |= (1 << 31);
      return new Decimal(new int[] { a, b, c, d });
    }
    private static decimal DecimalFractionToDecimal(DecimalFraction decfrac) {
      FastInteger bigexp = new FastInteger(decfrac.Exponent);
      BigInteger bigmant = decfrac.Mantissa;
      BigInteger decmax = (BigInteger)Decimal.MaxValue; // equals (2^96-1)
      bool neg = (bigmant < 0);
      if (neg) bigmant = -bigmant;
      if (bigexp.Sign == 0) {
        if (bigmant > decmax)
          throw new OverflowException("This object's value is out of range");
        return (decimal)bigmant;
      } else if (bigexp.Sign > 0) {
        while (bigexp.Sign > 0) {
          bigmant *= 10;
          if (bigmant > decmax)
            throw new OverflowException("This object's value is out of range");
          bigexp.Add(-1);
        }
        return (decimal)bigmant;
      } else {
        int lastDigit = 0;
        while (bigexp.Sign < 0) {
          if (bigexp.CompareTo(-28) >= 0 && bigmant <= decmax) {
            if (lastDigit >= 5) {
              bigmant++; // round half-up
            }
            if (bigmant <= decmax) {
              bigexp.Negate();
              return EncodeDecimal(bigmant, bigexp.AsInt32(), neg);
            } else if (lastDigit >= 5) {
              bigmant--; // undo rounding
            }
          }
          lastDigit = (int)(bigmant % 10);
          bigmant /= 10;
          bigexp.Add(-1);
        }
        if (lastDigit >= 5) {
          bigmant++;
        }
        if (bigmant > decmax)
          throw new OverflowException("This object's value is out of range");
        return EncodeDecimal(bigmant, 0, neg);
      }
    }
    /// <summary> Converts this object to a .NET decimal. </summary>
    /// <returns> The closest big integer to this object.</returns>
    /// <exception cref='System.InvalidOperationException'> This object's
    /// type is not a number type. </exception>
    /// <exception cref='System.OverflowException'> This object's value
    /// exceeds the range of a .NET decimal.</exception>
    [CLSCompliant(false)]
    public decimal AsDecimal() {
      if (this.ItemType == CBORObjectType_Integer) {
        return (decimal)(long)this.ThisItem;
      } else if (this.ItemType == CBORObjectType_BigInteger) {
        if ((BigInteger)this.ThisItem > (BigInteger)Decimal.MaxValue ||
           (BigInteger)this.ThisItem < (BigInteger)Decimal.MinValue)
          throw new OverflowException("This object's value is out of range");
        return (decimal)(BigInteger)this.ThisItem;
      } else if (this.ItemType == CBORObjectType_Single) {
        if (Single.IsNaN((float)this.ThisItem) ||
           (float)this.ThisItem > (float)Decimal.MaxValue ||
           (float)this.ThisItem < (float)Decimal.MinValue)
          throw new OverflowException("This object's value is out of range");
        return (decimal)(float)this.ThisItem;
      } else if (this.ItemType == CBORObjectType_Double) {
        if (Double.IsNaN((double)this.ThisItem) ||
           (double)this.ThisItem > (double)Decimal.MaxValue ||
           (double)this.ThisItem < (double)Decimal.MinValue)
          throw new OverflowException("This object's value is out of range");
        return (decimal)(double)this.ThisItem;
      } else if (this.ItemType == CBORObjectType_DecimalFraction) {
        return DecimalFractionToDecimal((DecimalFraction)this.ThisItem);
      } else if (this.ItemType == CBORObjectType_BigFloat) {
        return DecimalFractionToDecimal(
          DecimalFraction.FromBigFloat((BigFloat)this.ThisItem));
      } else
        throw new InvalidOperationException("Not a number type");
    }
    /// <summary> Converts this object to a 64-bit unsigned integer. Floating
    /// point values are truncated to an integer. </summary>
    /// <returns> The closest big integer to this object.</returns>
    /// <exception cref='System.InvalidOperationException'> This object's
    /// type is not a number type. </exception>
    /// <exception cref='System.OverflowException'> This object's value
    /// exceeds the range of a 64-bit unsigned integer.</exception>
    [CLSCompliant(false)]
    public ulong AsUInt64() {
      if (this.ItemType == CBORObjectType_Integer) {
        if ((long)this.ThisItem < 0)
          throw new OverflowException("This object's value is out of range");
        return (ulong)(long)this.ThisItem;
      } else if (this.ItemType == CBORObjectType_BigInteger) {
        if ((BigInteger)this.ThisItem > UInt64.MaxValue || (BigInteger)this.ThisItem < 0)
          throw new OverflowException("This object's value is out of range");
        return (ulong)(BigInteger)this.ThisItem;
      } else if (this.ItemType == CBORObjectType_Single) {
        float fltItem = (float)this.ThisItem;
        if (Single.IsNaN(fltItem))
          throw new OverflowException("This object's value is out of range");
        fltItem = (fltItem < 0) ? (float)Math.Ceiling(fltItem) : (float)Math.Floor(fltItem);
        if (fltItem >= 0 && fltItem <= UInt64.MaxValue)
          return (ulong)fltItem;
        throw new OverflowException("This object's value is out of range");
      } else if (this.ItemType == CBORObjectType_Double) {
        double fltItem = (double)this.ThisItem;
        if (Double.IsNaN(fltItem))
          throw new OverflowException("This object's value is out of range");
        fltItem = (fltItem < 0) ? Math.Ceiling(fltItem) : Math.Floor(fltItem);
        if (fltItem >= 0 && fltItem <= UInt64.MaxValue)
          return (ulong)fltItem;
        throw new OverflowException("This object's value is out of range");
      } else if (this.ItemType == CBORObjectType_DecimalFraction) {
        BigInteger bi = ((DecimalFraction)this.ThisItem).ToBigInteger();
        if (bi > UInt64.MaxValue || bi < 0)
          throw new OverflowException("This object's value is out of range");
        return (ulong)bi;
      } else if (this.ItemType == CBORObjectType_BigFloat) {
        BigInteger bi = ((BigFloat)this.ThisItem).ToBigInteger();
        if (bi > UInt64.MaxValue || bi < 0)
          throw new OverflowException("This object's value is out of range");
        return (ulong)bi;
      } else
        throw new InvalidOperationException("Not a number type");
    }
    /// <summary> </summary>
    /// <param name='value'>A SByte object.</param>
    /// <returns></returns>
    /// <param name='stream'>A Stream object.</param>
    [CLSCompliant(false)]
    public static void Write(sbyte value, Stream stream) {
      Write((long)value, stream);
    }
    /// <summary> </summary>
    /// <param name='value'>A 64-bit unsigned integer.</param>
    /// <returns></returns>
    /// <param name='stream'>A Stream object.</param>
    [CLSCompliant(false)]
    public static void Write(ulong value, Stream stream) {
      if((stream)==null)throw new ArgumentNullException("stream");
      if (value <= Int64.MaxValue) {
        Write((long)value, stream);
      } else {
        stream.WriteByte((byte)(27));
        stream.WriteByte((byte)((value >> 56) & 0xFF));
        stream.WriteByte((byte)((value >> 48) & 0xFF));
        stream.WriteByte((byte)((value >> 40) & 0xFF));
        stream.WriteByte((byte)((value >> 32) & 0xFF));
        stream.WriteByte((byte)((value >> 24) & 0xFF));
        stream.WriteByte((byte)((value >> 16) & 0xFF));
        stream.WriteByte((byte)((value >> 8) & 0xFF));
        stream.WriteByte((byte)(value & 0xFF));
      }
    }
    /// <summary> </summary>
    /// <param name='value'>A Decimal object.</param>
    /// <returns></returns>
    public static CBORObject FromObject(decimal value) {
      if (Math.Round(value) == value) {
        // This is an integer
        if (value >= 0 && value <= UInt64.MaxValue) {
          return FromObject((ulong)value);
        } else if (value >= Int64.MinValue && value <= Int64.MaxValue) {
          return FromObject((long)value);
        } else {
          return FromObject((BigInteger)value);
        }
      } else {
        int[] v = Decimal.GetBits(value);
        uint low = unchecked((uint)v[0]);
        ulong mid = (ulong)unchecked((uint)v[1]);
        ulong high = (ulong)unchecked((uint)v[2]);
        bool negative = (v[3] >> 31) != 0;
        int scale = (v[3] >> 16) & 0xFF;
        high <<= 32;
        BigInteger mantissa = high | mid;
        mantissa <<= 32;
        mantissa |= low;
        if (negative) mantissa = -mantissa;
        return FromObjectAndTag(
          new CBORObject[]{
            FromObject(-scale),
            FromObject(mantissa)
          }, 4);
      }
    }
    /// <summary> </summary>
    /// <param name='value'>A 32-bit unsigned integer.</param>
    /// <returns></returns>
    /// <param name='stream'>A Stream object.</param>
    [CLSCompliant(false)]
    public static void Write(uint value, Stream stream) {
      Write((ulong)value, stream);
    }
    /// <summary> </summary>
    /// <param name='value'>A UInt16 object.</param>
    /// <returns></returns>
    /// <param name='stream'>A Stream object.</param>
    [CLSCompliant(false)]
    public static void Write(ushort value, Stream stream) {
      Write((ulong)value, stream);
    }
    /// <summary> </summary>
    /// <param name='value'>A SByte object.</param>
    /// <returns></returns>
    [CLSCompliant(false)]
    public static CBORObject FromObject(sbyte value) {
      return FromObject((long)value);
    }
    /// <summary> </summary>
    /// <param name='value'>A 64-bit unsigned integer.</param>
    /// <returns></returns>
    [CLSCompliant(false)]
    public static CBORObject FromObject(ulong value) {
      return FromObject((BigInteger)value);
    }
    /// <summary> </summary>
    /// <param name='value'>A 32-bit unsigned integer.</param>
    /// <returns></returns>
    [CLSCompliant(false)]
    public static CBORObject FromObject(uint value) {
      return FromObject((long)value);
    }
    /// <summary> </summary>
    /// <param name='value'>A UInt16 object.</param>
    /// <returns></returns>
    [CLSCompliant(false)]
    public static CBORObject FromObject(ushort value) {
      return FromObject((long)value);
    }
    /// <summary> </summary>
    /// <param name='o'>An arbitrary object.</param>
    /// <param name='tag'>A 64-bit unsigned integer.</param>
    /// <returns></returns>
    [CLSCompliant(false)]
    public static CBORObject FromObjectAndTag(Object o, ulong tag) {
      return FromObjectAndTag(o, (BigInteger)tag);
    }
    // .NET-specific
    private static string DateTimeToString(DateTime bi) {
      DateTime dt = bi.ToUniversalTime();
      int year = dt.Year;
      int month = dt.Month;
      int day = dt.Day;
      int hour = dt.Hour;
      int minute = dt.Minute;
      int second = dt.Second;
      int millisecond = dt.Millisecond;
      char[] charbuf = new char[millisecond > 0 ? 24 : 20];
      charbuf[0] = (char)('0' + ((year / 1000) % 10));
      charbuf[1] = (char)('0' + ((year / 100) % 10));
      charbuf[2] = (char)('0' + ((year / 10) % 10));
      charbuf[3] = (char)('0' + ((year) % 10));
      charbuf[4] = '-';
      charbuf[5] = (char)('0' + ((month / 10) % 10));
      charbuf[6] = (char)('0' + ((month) % 10));
      charbuf[7] = '-';
      charbuf[8] = (char)('0' + ((day / 10) % 10));
      charbuf[9] = (char)('0' + ((day) % 10));
      charbuf[10] = 'T';
      charbuf[11] = (char)('0' + ((hour / 10) % 10));
      charbuf[12] = (char)('0' + ((hour) % 10));
      charbuf[13] = ':';
      charbuf[14] = (char)('0' + ((minute / 10) % 10));
      charbuf[15] = (char)('0' + ((minute) % 10));
      charbuf[16] = ':';
      charbuf[17] = (char)('0' + ((second / 10) % 10));
      charbuf[18] = (char)('0' + ((second) % 10));
      if (millisecond > 0) {
        charbuf[19] = '.';
        charbuf[20] = (char)('0' + ((millisecond / 100) % 10));
        charbuf[21] = (char)('0' + ((millisecond / 10) % 10));
        charbuf[22] = (char)('0' + ((millisecond) % 10));
        charbuf[23] = 'Z';
      } else {
        charbuf[19] = 'Z';
      }
      return new String(charbuf);
    }
    /// <summary> </summary>
    /// <param name='value'> A DateTime object.</param>
    /// <returns></returns>
    public static CBORObject FromObject(DateTime value) {
      return new CBORObject(
        FromObject(DateTimeToString(value)), 0, 0);
    }
    /// <summary> Writes a date and time in CBOR format to a data stream. </summary>
    /// <param name='bi'> A DateTime object.</param>
    /// <returns></returns>
    /// <param name='stream'> A Stream object.</param>
    public static void Write(DateTime bi, Stream stream) {
      if ((stream) == null) throw new ArgumentNullException("stream");
      stream.WriteByte(0xC0);
      Write(DateTimeToString(bi), stream);
    }
  }
}