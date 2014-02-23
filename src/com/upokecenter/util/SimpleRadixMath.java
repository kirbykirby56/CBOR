package com.upokecenter.util;
/*
Written in 2013 by Peter O.
Any copyright is dedicated to the Public Domain.
http://creativecommons.org/publicdomain/zero/1.0/
If you like this, you should donate to Peter O.
at: http://peteroupc.github.io/CBOR/
 */

    /**
     * Implements the simplified arithmetic in Appendix A of the General
     * Decimal Arithmetic Specification. Unfortunately, it doesn't pass
     * all the test cases, since some aspects of the spec are left open. For
     * example: in which cases is the Clamped flag set? The test cases set
     * the Clamped flag in only a handful of test cases, all within the <code>exp</code>
     * operation.
     * @param <T> Data type for a numeric value in a particular radix.
     */
  final class SimpleRadixMath<T> implements IRadixMath<T> {
    private IRadixMath<T> wrapper;

    public SimpleRadixMath (IRadixMath<T> wrapper) {
      this.wrapper = wrapper;
    }

    private static PrecisionContext GetContextWithFlags(PrecisionContext ctx) {
      if (ctx == null) {
        return ctx;
      }
      return ctx.WithBlankFlags();
    }

    private T SignalInvalid(PrecisionContext ctx) {
      if (this.GetHelper().GetArithmeticSupport() == BigNumberFlags.FiniteOnly) {
        throw new ArithmeticException("Invalid operation");
      }
      if (ctx != null && ctx.getHasFlags()) {
        ctx.setFlags(ctx.getFlags()|(PrecisionContext.FlagInvalid));
      }
      return this.GetHelper().CreateNewWithFlags(BigInteger.ZERO, BigInteger.ZERO, BigNumberFlags.FlagQuietNaN);
    }

    private T PostProcess(T thisValue, PrecisionContext ctxDest, PrecisionContext ctxSrc) {
      return this.PostProcessEx(thisValue, ctxDest, ctxSrc, false, false);
    }

    private T PostProcessAfterDivision(T thisValue, PrecisionContext ctxDest, PrecisionContext ctxSrc) {
      return this.PostProcessEx(thisValue, ctxDest, ctxSrc, true, false);
    }

    private T PostProcessAfterQuantize(T thisValue, PrecisionContext ctxDest, PrecisionContext ctxSrc) {
      return this.PostProcessEx(thisValue, ctxDest, ctxSrc, false, true);
    }

    private T PostProcessEx(T thisValue, PrecisionContext ctxDest, PrecisionContext ctxSrc, boolean afterDivision, boolean afterQuantize) {
      int thisFlags = this.GetHelper().GetFlags(thisValue);
      if (ctxDest != null && ctxSrc != null) {
        if (ctxDest.getHasFlags()) {
          // if ((ctxSrc.getFlags() & PrecisionContext.FlagUnderflow) != 0) {
          ctxSrc.setFlags(ctxSrc.getFlags()&~(PrecisionContext.FlagClamped));
          // }
          ctxDest.setFlags(ctxDest.getFlags()|(ctxSrc.getFlags()));
          if ((ctxSrc.getFlags() & PrecisionContext.FlagSubnormal) != 0) {
            // Treat subnormal numbers as underflows
            int newflags = PrecisionContext.FlagUnderflow | PrecisionContext.FlagInexact |
              PrecisionContext.FlagRounded;
            ctxDest.setFlags(ctxDest.getFlags()|(newflags));
          }
        }
      }
      if ((thisFlags & BigNumberFlags.FlagSpecial) != 0) {
        if (ctxDest.getFlags() == 0) {
          return this.SignalInvalid(ctxDest);
        }
        return thisValue;
      }
      BigInteger mant = (this.GetHelper().GetMantissa(thisValue)).abs();
      if (mant.signum()==0) {
        if (afterQuantize) {
          return this.GetHelper().CreateNewWithFlags(mant, this.GetHelper().GetExponent(thisValue), 0);
        }
        return this.GetHelper().ValueOf(0);
      }
      if (afterQuantize) {
        return thisValue;
      }
      BigInteger exp = this.GetHelper().GetExponent(thisValue);
      if (exp.signum() > 0) {
        FastInteger fastExp = FastInteger.FromBig(exp);
        if (ctxDest == null || !ctxDest.getHasMaxPrecision()) {
          mant = this.GetHelper().MultiplyByRadixPower(mant, fastExp);
          return this.GetHelper().CreateNewWithFlags(mant, BigInteger.ZERO, thisFlags);
        }
        if (!ctxDest.ExponentWithinRange(exp)) {
          return thisValue;
        }
        FastInteger prec = FastInteger.FromBig(ctxDest.getPrecision());
        FastInteger digits = this.GetHelper().CreateShiftAccumulator(mant).GetDigitLength();
        prec.Subtract(digits);
        if (prec.signum() > 0 && prec.compareTo(fastExp) >= 0) {
          mant = this.GetHelper().MultiplyByRadixPower(mant, fastExp);
          return this.GetHelper().CreateNewWithFlags(mant, BigInteger.ZERO, thisFlags);
        }
        if (afterDivision) {
          mant = DecimalUtility.ReduceTrailingZeros(mant, fastExp, this.GetHelper().GetRadix(), null, null, null);
          thisValue = this.GetHelper().CreateNewWithFlags(mant, fastExp.AsBigInteger(), thisFlags);
        }
      } else if (afterDivision && exp.signum() < 0) {
        FastInteger fastExp = FastInteger.FromBig(exp);
        mant = DecimalUtility.ReduceTrailingZeros(mant, fastExp, this.GetHelper().GetRadix(), null, null, new FastInteger(0));
        thisValue = this.GetHelper().CreateNewWithFlags(mant, fastExp.AsBigInteger(), thisFlags);
      }
      return thisValue;
    }

    private T ReturnQuietNaN(T thisValue, PrecisionContext ctx) {
      BigInteger mant = (this.GetHelper().GetMantissa(thisValue)).abs();
      boolean mantChanged = false;
      if (mant.signum()!=0 && ctx != null && ctx.getHasMaxPrecision()) {
        BigInteger limit = this.GetHelper().MultiplyByRadixPower(BigInteger.ONE, FastInteger.FromBig(ctx.getPrecision()));
        if (mant.compareTo(limit) >= 0) {
          mant=mant.remainder(limit);
          mantChanged = true;
        }
      }
      int flags = this.GetHelper().GetFlags(thisValue);
      if (!mantChanged && (flags & BigNumberFlags.FlagQuietNaN) != 0) {
        return thisValue;
      }
      flags &= BigNumberFlags.FlagNegative;
      flags |= BigNumberFlags.FlagQuietNaN;
      return this.GetHelper().CreateNewWithFlags(mant, BigInteger.ZERO, flags);
    }

    private T HandleNotANumber(T thisValue, T other, PrecisionContext ctx) {
      int thisFlags = this.GetHelper().GetFlags(thisValue);
      int otherFlags = this.GetHelper().GetFlags(other);
      // Check this value then the other value for signaling NaN
      if ((thisFlags & BigNumberFlags.FlagSignalingNaN) != 0) {
        return this.SignalingNaNInvalid(thisValue, ctx);
      }
      if ((otherFlags & BigNumberFlags.FlagSignalingNaN) != 0) {
        return this.SignalingNaNInvalid(other, ctx);
      }
      // Check this value then the other value for quiet NaN
      if ((thisFlags & BigNumberFlags.FlagQuietNaN) != 0) {
        return this.ReturnQuietNaN(thisValue, ctx);
      }
      if ((otherFlags & BigNumberFlags.FlagQuietNaN) != 0) {
        return this.ReturnQuietNaN(other, ctx);
      }
      return null;
    }

    private T SignalingNaNInvalid(T value, PrecisionContext ctx) {
      if (ctx != null && ctx.getHasFlags()) {
        ctx.setFlags(ctx.getFlags()|(PrecisionContext.FlagInvalid));
      }
      return this.ReturnQuietNaN(value, ctx);
    }

    private T CheckNotANumber1(T val, PrecisionContext ctx) {
      return this.HandleNotANumber(val, val, ctx);
    }

    private T CheckNotANumber2(T val, T val2, PrecisionContext ctx) {
      return this.HandleNotANumber(val, val2, ctx);
    }

    private T RoundBeforeOp(T val, PrecisionContext ctx) {
      if (ctx == null || !ctx.getHasMaxPrecision()) {
        return val;
      }
      int thisFlags = this.GetHelper().GetFlags(val);
      if ((thisFlags & BigNumberFlags.FlagSpecial) != 0) {
        return val;
      }
      FastInteger fastPrecision = FastInteger.FromBig(ctx.getPrecision());
      BigInteger mant = (this.GetHelper().GetMantissa(val)).abs();
      FastInteger digits = this.GetHelper().CreateShiftAccumulator(mant).GetDigitLength();
      if (digits.compareTo(fastPrecision) <= 0) {
        // Rounding is only to be done if the digit count is
        // too big (distinguishing this case is material
        // if the value also has an exponent that's out of range)
        return val;
      }
      PrecisionContext ctx2 = ctx.WithBlankFlags().WithTraps(0);
      val = this.wrapper.RoundToPrecision(val, ctx2);
      // the only time rounding can signal an invalid
      // operation is if an operand is signaling NaN, but
      // this was already checked beforehand

      if ((ctx2.getFlags() & PrecisionContext.FlagInexact) != 0) {
        if (ctx.getHasFlags()) {
          int newflags = PrecisionContext.FlagLostDigits | PrecisionContext.FlagInexact |
            PrecisionContext.FlagRounded;
          ctx.setFlags(ctx.getFlags()|(newflags));
        }
      }
      if ((ctx2.getFlags() & PrecisionContext.FlagRounded) != 0) {
        if (ctx.getHasFlags()) {
          ctx.setFlags(ctx.getFlags()|(PrecisionContext.FlagRounded));
        }
      }
      if ((ctx2.getFlags() & PrecisionContext.FlagSubnormal) != 0) {
      // System.out.println("Subnormal");
      }
      if ((ctx2.getFlags() & PrecisionContext.FlagUnderflow) != 0) {
      // System.out.println("Underflow");
      }
      if ((ctx2.getFlags() & PrecisionContext.FlagOverflow) != 0) {
        boolean neg = (thisFlags & BigNumberFlags.FlagNegative) != 0;
        ctx.setFlags(ctx.getFlags()|(PrecisionContext.FlagLostDigits));
        return this.SignalOverflow2(ctx, neg);
      }
      return val;
    }

    /**
     * Not documented yet.
     * @param thisValue A T object. (2).
     * @param divisor A T object. (3).
     * @param ctx A PrecisionContext object.
     * @return A T object.
     */
    public T DivideToIntegerNaturalScale(T thisValue, T divisor, PrecisionContext ctx) {
      T ret = this.CheckNotANumber2(thisValue, divisor, ctx);
      if ((Object)ret != (Object)null) {
        return ret;
      }
      PrecisionContext ctx2 = GetContextWithFlags(ctx);
      thisValue = this.RoundBeforeOp(thisValue, ctx2);
      divisor = this.RoundBeforeOp(divisor, ctx2);
      thisValue = this.wrapper.DivideToIntegerNaturalScale(thisValue, divisor, ctx2);
      return this.PostProcessAfterDivision(thisValue, ctx, ctx2);
    }

    /**
     * Not documented yet.
     * @param thisValue A T object. (2).
     * @param divisor A T object. (3).
     * @param ctx A PrecisionContext object.
     * @return A T object.
     */
    public T DivideToIntegerZeroScale(T thisValue, T divisor, PrecisionContext ctx) {
      T ret = this.CheckNotANumber2(thisValue, divisor, ctx);
      if ((Object)ret != (Object)null) {
        return ret;
      }
      PrecisionContext ctx2 = GetContextWithFlags(ctx);
      thisValue = this.RoundBeforeOp(thisValue, ctx2);
      divisor = this.RoundBeforeOp(divisor, ctx2);
      thisValue = this.wrapper.DivideToIntegerZeroScale(thisValue, divisor, ctx2);
      return this.PostProcessAfterDivision(thisValue, ctx, ctx2);
    }

    /**
     * Not documented yet.
     * @param value A T object. (2).
     * @param ctx A PrecisionContext object.
     * @return A T object.
     */
    public T Abs(T value, PrecisionContext ctx) {
      T ret = this.CheckNotANumber1(value, ctx);
      if ((Object)ret != (Object)null) {
        return ret;
      }
      PrecisionContext ctx2 = GetContextWithFlags(ctx);
      value = this.RoundBeforeOp(value, ctx2);
      value = this.wrapper.Abs(value, ctx2);
      return this.PostProcess(value, ctx, ctx2);
    }

    /**
     * Not documented yet.
     * @param value A T object. (2).
     * @param ctx A PrecisionContext object.
     * @return A T object.
     */
    public T Negate(T value, PrecisionContext ctx) {
      T ret = this.CheckNotANumber1(value, ctx);
      if ((Object)ret != (Object)null) {
        return ret;
      }
      PrecisionContext ctx2 = GetContextWithFlags(ctx);
      value = this.RoundBeforeOp(value, ctx2);
      value = this.wrapper.Negate(value, ctx2);
      return this.PostProcess(value, ctx, ctx2);
    }

    /**
     * Finds the remainder that results when dividing two T objects.
     * @param thisValue A T object.
     * @param divisor A T object. (2).
     * @param ctx A PrecisionContext object.
     * @return The remainder of the two objects.
     */
    public T Remainder(T thisValue, T divisor, PrecisionContext ctx) {
      T ret = this.CheckNotANumber2(thisValue, divisor, ctx);
      if ((Object)ret != (Object)null) {
        return ret;
      }
      PrecisionContext ctx2 = GetContextWithFlags(ctx);
      thisValue = this.RoundBeforeOp(thisValue, ctx2);
      divisor = this.RoundBeforeOp(divisor, ctx2);
      thisValue = this.wrapper.Remainder(thisValue, divisor, ctx2);
      return this.PostProcess(thisValue, ctx, ctx2);
    }

    /**
     * Not documented yet.
     * @param thisValue A T object. (2).
     * @param divisor A T object. (3).
     * @param ctx A PrecisionContext object.
     * @return A T object.
     */
    public T RemainderNear(T thisValue, T divisor, PrecisionContext ctx) {
      T ret = this.CheckNotANumber2(thisValue, divisor, ctx);
      if ((Object)ret != (Object)null) {
        return ret;
      }
      PrecisionContext ctx2 = GetContextWithFlags(ctx);
      thisValue = this.RoundBeforeOp(thisValue, ctx2);
      divisor = this.RoundBeforeOp(divisor, ctx2);
      thisValue = this.wrapper.RemainderNear(thisValue, divisor, ctx2);
      return this.PostProcess(thisValue, ctx, ctx2);
    }

    /**
     * Not documented yet.
     * @param ctx A PrecisionContext object.
     * @return A T object.
     */
    public T Pi(PrecisionContext ctx) {
      return this.wrapper.Pi(ctx);
    }

    /*
    private T PowerIntegral(
      T thisValue,
      BigInteger powIntBig,
      PrecisionContext ctx) {
      int sign = powIntBig.signum();
      T one = this.GetHelper().ValueOf(1);

      if (sign == 0) {
        // however 0 to the power of 0 is undefined
        return this.wrapper.RoundToPrecision(one, ctx);
      } else if (powIntBig.equals(BigInteger.ONE)) {
        return this.wrapper.RoundToPrecision(thisValue, ctx);
      }
      boolean retvalNeg = (this.GetHelper().GetFlags(thisValue) &
                        BigNumberFlags.FlagNegative) != 0 && powIntBig.testBit(0);
      FastInteger error = this.GetHelper().CreateShiftAccumulator(
        (powIntBig).abs()).GetDigitLength();
      error.AddInt(6);
      BigInteger bigError = error.AsBigInteger();
      PrecisionContext ctxdiv = ctx.WithBigPrecision(ctx.getPrecision().add(bigError))
        .WithRounding(this.GetHelper().GetRadix() == 2 ?
                      Rounding.HalfEven : Rounding.ZeroFiveUp).WithBlankFlags();
      boolean negativeSign = sign < 0;
      if (negativeSign) {
        powIntBig=powIntBig.negate();
      }
      // System.out.println("pow=" + powIntBig + " negsign=" + negativeSign);
      T r = one;
      boolean first = true;
      int b = powIntBig.bitLength();
      boolean inexact = false;
      // System.out.println("starting pow prec=" + ctxdiv.getPrecision());
      for (int i = b - 1; i >= 0; --i) {
        boolean bit = powIntBig.testBit(i);
        if (bit) {
          ctxdiv.setFlags(0);
          if (first) {
            r = thisValue;
            first = false;
          } else {
            ctxdiv.setFlags(0);
            r = this.wrapper.Multiply(r, thisValue, ctxdiv);
            // System.out.println("mult " + r);
            if ((ctxdiv.getFlags() & PrecisionContext.FlagInexact) != 0) {
              inexact = true;
            }
            if ((ctxdiv.getFlags() & PrecisionContext.FlagOverflow) != 0) {
              // Avoid multiplying too huge numbers with
              // limited exponent range
              return this.SignalOverflow2(ctx, retvalNeg);
            }
          }
        }
        if (i > 0 && !first) {
          ctxdiv.setFlags(0);
          r = this.wrapper.Multiply(r, r, ctxdiv);
          // System.out.println("sqr " + r);
          if ((ctxdiv.getFlags() & PrecisionContext.FlagInexact) != 0) {
            inexact = true;
          }
          if ((ctxdiv.getFlags() & PrecisionContext.FlagOverflow) != 0) {
            // Avoid multiplying too huge numbers with
            // limited exponent range
            return this.SignalOverflow2(ctx, retvalNeg);
          }
        }
      }
      if (negativeSign) {
        // Use the reciprocal for negative powers
        ctxdiv.setFlags(0);
        r = this.wrapper.Divide(one, r, ctx);
        // System.out.println("Flags=" + ctxdiv.getFlags());
        if ((ctxdiv.getFlags() & PrecisionContext.FlagOverflow) != 0) {
          return this.SignalOverflow2(ctx, retvalNeg);
        }
        // System.out.println("Exp=" + this.GetHelper().GetExponent(r) + " Prec=" + ctx.getPrecision() + " Digits=" + (this.GetHelper().CreateShiftAccumulator(
        // (powIntBig).abs()).GetDigitLength()));
        if (ctx != null && ctx.getHasFlags()) {
          if (inexact) {
            ctx.setFlags(ctx.getFlags()|(PrecisionContext.FlagRounded));
          }
        }
        return r;
      } else {
        return this.wrapper.RoundToPrecision(r, ctx);
      }
    }
     */
    private T SignalOverflow2(PrecisionContext pc, boolean neg) {
      if (pc != null) {
        Rounding roundingOnOverflow = pc.getRounding();
        if (pc.getHasFlags()) {
          pc.setFlags(pc.getFlags()|(PrecisionContext.FlagOverflow | PrecisionContext.FlagInexact | PrecisionContext.FlagRounded));
        }
        if (pc.getHasMaxPrecision() && pc.getHasExponentRange() &&
            (roundingOnOverflow == Rounding.Down || roundingOnOverflow == Rounding.ZeroFiveUp ||
             (roundingOnOverflow == Rounding.Ceiling && neg) || (roundingOnOverflow == Rounding.Floor && !neg))) {
          // Set to the highest possible value for
          // the given precision
          BigInteger overflowMant = BigInteger.ZERO;
          FastInteger fastPrecision = FastInteger.FromBig(pc.getPrecision());
          overflowMant = this.GetHelper().MultiplyByRadixPower(BigInteger.ONE, fastPrecision);
          overflowMant=overflowMant.subtract(BigInteger.ONE);
          FastInteger clamp = FastInteger.FromBig(pc.getEMax()).Increment().Subtract(fastPrecision);
          return this.GetHelper().CreateNewWithFlags(overflowMant, clamp.AsBigInteger(), neg ? BigNumberFlags.FlagNegative : 0);
        }
      }
      return this.GetHelper().GetArithmeticSupport() == BigNumberFlags.FiniteOnly ?
        null : this.GetHelper().CreateNewWithFlags(BigInteger.ZERO, BigInteger.ZERO, (neg ? BigNumberFlags.FlagNegative : 0) | BigNumberFlags.FlagInfinity);
    }
    /*

    private T NegateRaw(T val) {
      if (val == null) {
        return val;
      }
      int sign = this.GetHelper().GetFlags(val) & BigNumberFlags.FlagNegative;
      return this.GetHelper().CreateNewWithFlags(
        this.GetHelper().GetMantissa(val),
        this.GetHelper().GetExponent(val),
        sign == 0 ? BigNumberFlags.FlagNegative : 0);
    }
     */

    /**
     * Not documented yet.
     * @param thisValue A T object. (2).
     * @param pow A T object. (3).
     * @param ctx A PrecisionContext object.
     * @return A T object.
     */
    public T Power(T thisValue, T pow, PrecisionContext ctx) {
      T ret = this.CheckNotANumber2(thisValue, pow, ctx);
      if ((Object)ret != (Object)null) {
        return ret;
      }
      PrecisionContext ctx2 = GetContextWithFlags(ctx);
      // System.out.println("op was " + thisValue + ", "+pow);
      thisValue = this.RoundBeforeOp(thisValue, ctx2);
      pow = this.RoundBeforeOp(pow, ctx2);
      // System.out.println("op now " + thisValue + ", "+pow);
      int powSign = this.GetHelper().GetSign(pow);
      if (powSign == 0 && this.GetHelper().GetSign(thisValue) == 0) {
        thisValue = this.GetHelper().ValueOf(1);
      } else {
        // System.out.println("was " + thisValue);
        // BigInteger powExponent = this.GetHelper().GetExponent(pow);
        // BigInteger powInteger = (this.GetHelper().GetMantissa(pow)).abs();
        {
          thisValue = this.wrapper.Power(thisValue, pow, ctx2);
        }
      }
      // System.out.println("was " + thisValue);
      thisValue = this.PostProcessAfterDivision(thisValue, ctx, ctx2);
      // System.out.println("result was " + thisValue);
      // System.out.println("now " + thisValue);
      return thisValue;
    }

    /**
     * Not documented yet.
     * @param thisValue A T object. (2).
     * @param ctx A PrecisionContext object.
     * @return A T object.
     */
    public T Log10(T thisValue, PrecisionContext ctx) {
      T ret = this.CheckNotANumber1(thisValue, ctx);
      if ((Object)ret != (Object)null) {
        return ret;
      }
      PrecisionContext ctx2 = GetContextWithFlags(ctx);
      thisValue = this.RoundBeforeOp(thisValue, ctx2);
      thisValue = this.wrapper.Log10(thisValue, ctx2);
      return this.PostProcess(thisValue, ctx, ctx2);
    }

    /**
     * Not documented yet.
     * @param thisValue A T object. (2).
     * @param ctx A PrecisionContext object.
     * @return A T object.
     */
    public T Ln(T thisValue, PrecisionContext ctx) {
      T ret = this.CheckNotANumber1(thisValue, ctx);
      if ((Object)ret != (Object)null) {
        return ret;
      }
      PrecisionContext ctx2 = GetContextWithFlags(ctx);
      // System.out.println("was: " + thisValue);
      thisValue = this.RoundBeforeOp(thisValue, ctx2);
      // System.out.println("now: " + thisValue);
      thisValue = this.wrapper.Ln(thisValue, ctx2);
      // System.out.println("result: " + thisValue);
      return this.PostProcess(thisValue, ctx, ctx2);
    }

    /**
     * Not documented yet.
     * @return An IRadixMathHelper(T) object.
     */
    public IRadixMathHelper<T> GetHelper() {
      return this.wrapper.GetHelper();
    }

    /**
     * Not documented yet.
     * @param thisValue A T object. (2).
     * @param ctx A PrecisionContext object.
     * @return A T object.
     */
    public T Exp(T thisValue, PrecisionContext ctx) {
      T ret = this.CheckNotANumber1(thisValue, ctx);
      if ((Object)ret != (Object)null) {
        return ret;
      }
      PrecisionContext ctx2 = GetContextWithFlags(ctx);
      thisValue = this.RoundBeforeOp(thisValue, ctx2);
      thisValue = this.wrapper.Exp(thisValue, ctx2);
      return this.PostProcess(thisValue, ctx, ctx2);
    }

    /**
     * Not documented yet.
     * @param thisValue A T object. (2).
     * @param ctx A PrecisionContext object.
     * @return A T object.
     */
    public T SquareRoot(T thisValue, PrecisionContext ctx) {
      T ret = this.CheckNotANumber1(thisValue, ctx);
      if ((Object)ret != (Object)null) {
        return ret;
      }
      PrecisionContext ctx2 = GetContextWithFlags(ctx);
      // System.out.println("op was " + thisValue);
      thisValue = this.RoundBeforeOp(thisValue, ctx2);
      // System.out.println("op now " + thisValue);
      thisValue = this.wrapper.SquareRoot(thisValue, ctx2);
      // System.out.println("result was " + thisValue);
      return this.PostProcess(thisValue, ctx, ctx2);
    }

    /**
     * Not documented yet.
     * @param thisValue A T object. (2).
     * @param ctx A PrecisionContext object.
     * @return A T object.
     */
    public T NextMinus(T thisValue, PrecisionContext ctx) {
      T ret = this.CheckNotANumber1(thisValue, ctx);
      if ((Object)ret != (Object)null) {
        return ret;
      }
      PrecisionContext ctx2 = GetContextWithFlags(ctx);
      thisValue = this.RoundBeforeOp(thisValue, ctx2);
      thisValue = this.wrapper.NextMinus(thisValue, ctx2);
      return this.PostProcess(thisValue, ctx, ctx2);
    }

    /**
     * Not documented yet.
     * @param thisValue A T object. (2).
     * @param otherValue A T object. (3).
     * @param ctx A PrecisionContext object.
     * @return A T object.
     */
    public T NextToward(T thisValue, T otherValue, PrecisionContext ctx) {
      T ret = this.CheckNotANumber2(thisValue, otherValue, ctx);
      if ((Object)ret != (Object)null) {
        return ret;
      }
      PrecisionContext ctx2 = GetContextWithFlags(ctx);
      thisValue = this.RoundBeforeOp(thisValue, ctx2);
      otherValue = this.RoundBeforeOp(otherValue, ctx2);
      thisValue = this.wrapper.NextToward(thisValue, otherValue, ctx2);
      return this.PostProcess(thisValue, ctx, ctx2);
    }

    /**
     * Not documented yet.
     * @param thisValue A T object. (2).
     * @param ctx A PrecisionContext object.
     * @return A T object.
     */
    public T NextPlus(T thisValue, PrecisionContext ctx) {
      T ret = this.CheckNotANumber1(thisValue, ctx);
      if ((Object)ret != (Object)null) {
        return ret;
      }
      PrecisionContext ctx2 = GetContextWithFlags(ctx);
      thisValue = this.RoundBeforeOp(thisValue, ctx2);
      thisValue = this.wrapper.NextPlus(thisValue, ctx2);
      return this.PostProcess(thisValue, ctx, ctx2);
    }

    /**
     * Not documented yet.
     * @param thisValue A T object. (2).
     * @param divisor A T object. (3).
     * @param desiredExponent A BigInteger object.
     * @param ctx A PrecisionContext object.
     * @return A T object.
     */
    public T DivideToExponent(T thisValue, T divisor, BigInteger desiredExponent, PrecisionContext ctx) {
      T ret = this.CheckNotANumber2(thisValue, divisor, ctx);
      if ((Object)ret != (Object)null) {
        return ret;
      }
      PrecisionContext ctx2 = GetContextWithFlags(ctx);
      thisValue = this.RoundBeforeOp(thisValue, ctx2);
      divisor = this.RoundBeforeOp(divisor, ctx2);
      thisValue = this.wrapper.DivideToExponent(thisValue, divisor, desiredExponent, ctx2);
      return this.PostProcessAfterDivision(thisValue, ctx, ctx2);
    }

    /**
     * Divides two T objects.
     * @param thisValue A T object.
     * @param divisor A T object. (2).
     * @param ctx A PrecisionContext object.
     * @return The quotient of the two objects.
     */
    public T Divide(T thisValue, T divisor, PrecisionContext ctx) {
      T ret = this.CheckNotANumber2(thisValue, divisor, ctx);
      if ((Object)ret != (Object)null) {
        return ret;
      }
      PrecisionContext ctx2 = GetContextWithFlags(ctx);
      thisValue = this.RoundBeforeOp(thisValue, ctx2);
      divisor = this.RoundBeforeOp(divisor, ctx2);
      thisValue = this.wrapper.Divide(thisValue, divisor, ctx2);
      return this.PostProcessAfterDivision(thisValue, ctx, ctx2);
    }

    /**
     * Not documented yet.
     * @param a A T object. (2).
     * @param b A T object. (3).
     * @param ctx A PrecisionContext object.
     * @return A T object.
     */
    public T MinMagnitude(T a, T b, PrecisionContext ctx) {
      T ret = this.CheckNotANumber2(a, b, ctx);
      if ((Object)ret != (Object)null) {
        return ret;
      }
      PrecisionContext ctx2 = GetContextWithFlags(ctx);
      a = this.RoundBeforeOp(a, ctx2);
      b = this.RoundBeforeOp(b, ctx2);
      a = this.wrapper.MinMagnitude(a, b, ctx2);
      return this.PostProcess(a, ctx, ctx2);
    }

    /**
     * Not documented yet.
     * @param a A T object. (2).
     * @param b A T object. (3).
     * @param ctx A PrecisionContext object.
     * @return A T object.
     */
    public T MaxMagnitude(T a, T b, PrecisionContext ctx) {
      T ret = this.CheckNotANumber2(a, b, ctx);
      if ((Object)ret != (Object)null) {
        return ret;
      }
      PrecisionContext ctx2 = GetContextWithFlags(ctx);
      a = this.RoundBeforeOp(a, ctx2);
      b = this.RoundBeforeOp(b, ctx2);
      a = this.wrapper.MaxMagnitude(a, b, ctx2);
      return this.PostProcess(a, ctx, ctx2);
    }

    /**
     * Not documented yet.
     * @param a A T object. (2).
     * @param b A T object. (3).
     * @param ctx A PrecisionContext object.
     * @return A T object.
     */
    public T Max(T a, T b, PrecisionContext ctx) {
      T ret = this.CheckNotANumber2(a, b, ctx);
      if ((Object)ret != (Object)null) {
        return ret;
      }
      PrecisionContext ctx2 = GetContextWithFlags(ctx);
      a = this.RoundBeforeOp(a, ctx2);
      b = this.RoundBeforeOp(b, ctx2);
      // choose the left operand if both are equal
      a = (this.compareTo(a, b) >= 0) ? a : b;
      return this.PostProcess(a, ctx, ctx2);
    }

    /**
     * Not documented yet.
     * @param a A T object. (2).
     * @param b A T object. (3).
     * @param ctx A PrecisionContext object.
     * @return A T object.
     */
    public T Min(T a, T b, PrecisionContext ctx) {
      T ret = this.CheckNotANumber2(a, b, ctx);
      if ((Object)ret != (Object)null) {
        return ret;
      }
      PrecisionContext ctx2 = GetContextWithFlags(ctx);
      a = this.RoundBeforeOp(a, ctx2);
      b = this.RoundBeforeOp(b, ctx2);
      // choose the left operand if both are equal
      a = (this.compareTo(a, b) <= 0) ? a : b;
      return this.PostProcess(a, ctx, ctx2);
    }

    /**
     * Multiplies two T objects.
     * @param thisValue A T object.
     * @param other A T object. (2).
     * @param ctx A PrecisionContext object.
     * @return The product of the two objects.
     */
    public T Multiply(T thisValue, T other, PrecisionContext ctx) {
      T ret = this.CheckNotANumber2(thisValue, other, ctx);
      if ((Object)ret != (Object)null) {
        return ret;
      }
      PrecisionContext ctx2 = GetContextWithFlags(ctx);
      thisValue = this.RoundBeforeOp(thisValue, ctx2);
      other = this.RoundBeforeOp(other, ctx2);
      thisValue = this.wrapper.Multiply(thisValue, other, ctx2);
      return this.PostProcess(thisValue, ctx, ctx2);
    }

    /**
     * Not documented yet.
     * @param thisValue A T object. (2).
     * @param multiplicand A T object. (3).
     * @param augend A T object. (4).
     * @param ctx A PrecisionContext object.
     * @return A T object.
     */
    public T MultiplyAndAdd(T thisValue, T multiplicand, T augend, PrecisionContext ctx) {
      return this.SignalInvalid(ctx);
    }

    /**
     * Not documented yet.
     * @param thisValue A T object. (2).
     * @param ctx A PrecisionContext object.
     * @return A T object.
     */
    public T RoundToBinaryPrecision(T thisValue, PrecisionContext ctx) {
      T ret = this.CheckNotANumber1(thisValue, ctx);
      if ((Object)ret != (Object)null) {
        return ret;
      }
      PrecisionContext ctx2 = GetContextWithFlags(ctx);
      thisValue = this.RoundBeforeOp(thisValue, ctx2);
      thisValue = this.wrapper.RoundToBinaryPrecision(thisValue, ctx2);
      return this.PostProcess(thisValue, ctx, ctx2);
    }

    /**
     * Not documented yet.
     * @param thisValue A T object. (2).
     * @param ctx A PrecisionContext object.
     * @return A T object.
     */
    public T Plus(T thisValue, PrecisionContext ctx) {
      T ret = this.CheckNotANumber1(thisValue, ctx);
      if ((Object)ret != (Object)null) {
        return ret;
      }
      PrecisionContext ctx2 = GetContextWithFlags(ctx);
      thisValue = this.RoundBeforeOp(thisValue, ctx2);
      thisValue = this.wrapper.Plus(thisValue, ctx2);
      return this.PostProcess(thisValue, ctx, ctx2);
    }

    /**
     * Not documented yet.
     * @param thisValue A T object. (2).
     * @param ctx A PrecisionContext object.
     * @return A T object.
     */
    public T RoundToPrecision(T thisValue, PrecisionContext ctx) {
      T ret = this.CheckNotANumber1(thisValue, ctx);
      if ((Object)ret != (Object)null) {
        return ret;
      }
      PrecisionContext ctx2 = GetContextWithFlags(ctx);
      thisValue = this.RoundBeforeOp(thisValue, ctx2);
      thisValue = this.wrapper.RoundToPrecision(thisValue, ctx2);
      return this.PostProcess(thisValue, ctx, ctx2);
    }

    /**
     * Not documented yet.
     * @param thisValue A T object. (2).
     * @param otherValue A T object. (3).
     * @param ctx A PrecisionContext object.
     * @return A T object.
     */
    public T Quantize(T thisValue, T otherValue, PrecisionContext ctx) {
      T ret = this.CheckNotANumber1(thisValue, ctx);
      if ((Object)ret != (Object)null) {
        return ret;
      }
      PrecisionContext ctx2 = GetContextWithFlags(ctx);
      // System.out.println("was: "+thisValue+", "+otherValue);
      thisValue = this.RoundBeforeOp(thisValue, ctx2);
      BigInteger oldExponent = this.GetHelper().GetExponent(otherValue);
      // System.out.println("now: "+thisValue+", "+otherValue);
      otherValue = this.RoundBeforeOp(otherValue, ctx2);
      if (!oldExponent.equals(this.GetHelper().GetExponent(otherValue))) {
        // OtherValue's exponent changed in rounding
        return this.SignalInvalid(ctx);
      }
      thisValue = this.wrapper.Quantize(thisValue, otherValue, ctx2);
      // System.out.println("result: "+thisValue);
      return this.PostProcessAfterQuantize(thisValue, ctx, ctx2);
    }

    /**
     * Not documented yet.
     * @param thisValue A T object. (2).
     * @param expOther A BigInteger object.
     * @param ctx A PrecisionContext object.
     * @return A T object.
     */
    public T RoundToExponentExact(T thisValue, BigInteger expOther, PrecisionContext ctx) {
      T ret = this.CheckNotANumber1(thisValue, ctx);
      if ((Object)ret != (Object)null) {
        return ret;
      }
      PrecisionContext ctx2 = GetContextWithFlags(ctx);
      thisValue = this.RoundBeforeOp(thisValue, ctx2);
      thisValue = this.wrapper.RoundToExponentExact(thisValue, expOther, ctx);
      return this.PostProcessAfterQuantize(thisValue, ctx, ctx2);
    }

    /**
     * Not documented yet.
     * @param thisValue A T object. (2).
     * @param expOther A BigInteger object.
     * @param ctx A PrecisionContext object.
     * @return A T object.
     */
    public T RoundToExponentSimple(T thisValue, BigInteger expOther, PrecisionContext ctx) {
      T ret = this.CheckNotANumber1(thisValue, ctx);
      if ((Object)ret != (Object)null) {
        return ret;
      }
      PrecisionContext ctx2 = GetContextWithFlags(ctx);
      thisValue = this.RoundBeforeOp(thisValue, ctx2);
      thisValue = this.wrapper.RoundToExponentSimple(thisValue, expOther, ctx2);
      return this.PostProcessAfterQuantize(thisValue, ctx, ctx2);
    }

    /**
     * Not documented yet.
     * @param thisValue A T object. (2).
     * @param exponent A BigInteger object.
     * @param ctx A PrecisionContext object.
     * @return A T object.
     */
    public T RoundToExponentNoRoundedFlag(T thisValue, BigInteger exponent, PrecisionContext ctx) {
      T ret = this.CheckNotANumber1(thisValue, ctx);
      if ((Object)ret != (Object)null) {
        return ret;
      }
      PrecisionContext ctx2 = GetContextWithFlags(ctx);
      thisValue = this.RoundBeforeOp(thisValue, ctx2);
      thisValue = this.wrapper.RoundToExponentNoRoundedFlag(thisValue, exponent, ctx);
      return this.PostProcessAfterQuantize(thisValue, ctx, ctx2);
    }

    /**
     * Not documented yet.
     * @param thisValue A T object. (2).
     * @param ctx A PrecisionContext object.
     * @return A T object.
     */
    public T Reduce(T thisValue, PrecisionContext ctx) {
      T ret = this.CheckNotANumber1(thisValue, ctx);
      if ((Object)ret != (Object)null) {
        return ret;
      }
      PrecisionContext ctx2 = GetContextWithFlags(ctx);
      thisValue = this.RoundBeforeOp(thisValue, ctx2);
      thisValue = this.wrapper.Reduce(thisValue, ctx);
      return this.PostProcessAfterQuantize(thisValue, ctx, ctx2);
    }

    /**
     * Not documented yet.
     * @param thisValue A T object. (2).
     * @param other A T object. (3).
     * @param ctx A PrecisionContext object.
     * @return A T object.
     */
    public T Add(T thisValue, T other, PrecisionContext ctx) {
      T ret = this.CheckNotANumber2(thisValue, other, ctx);
      if ((Object)ret != (Object)null) {
        return ret;
      }
      PrecisionContext ctx2 = GetContextWithFlags(ctx);
      thisValue = this.RoundBeforeOp(thisValue, ctx2);
      other = this.RoundBeforeOp(other, ctx2);
      boolean zeroA = this.GetHelper().GetSign(thisValue) == 0;
      boolean zeroB = this.GetHelper().GetSign(other) == 0;
      if (zeroA) {
        thisValue = zeroB ? this.GetHelper().ValueOf(0) : other;
        thisValue = this.RoundToPrecision(thisValue, ctx2);
      } else if (!zeroB) {
        thisValue = this.wrapper.AddEx(thisValue, other, ctx2, true);
      } else {
        thisValue = this.RoundToPrecision(thisValue, ctx2);
      }
      return this.PostProcess(thisValue, ctx, ctx2);
    }

    /**
     * Not documented yet.
     * @param thisValue A T object. (2).
     * @param other A T object. (3).
     * @param ctx A PrecisionContext object.
     * @param roundToOperandPrecision A Boolean object.
     * @return A T object.
     */
    public T AddEx(T thisValue, T other, PrecisionContext ctx, boolean roundToOperandPrecision) {
      // NOTE: Ignores roundToOperandPrecision
      return this.Add(thisValue, other, ctx);
    }

    /**
     * Compares a T object with this instance.
     * @param thisValue A T object.
     * @param otherValue A T object. (2).
     * @param treatQuietNansAsSignaling A Boolean object.
     * @param ctx A PrecisionContext object.
     * @return Zero if the values are equal; a negative number if this instance
     * is less, or a positive number if this instance is greater.
     */
    public T CompareToWithContext(T thisValue, T otherValue, boolean treatQuietNansAsSignaling, PrecisionContext ctx) {
      T ret = this.CheckNotANumber2(thisValue, otherValue, ctx);
      if ((Object)ret != (Object)null) {
        return ret;
      }
      thisValue = this.RoundBeforeOp(thisValue, ctx);
      otherValue = this.RoundBeforeOp(otherValue, ctx);
      return this.wrapper.CompareToWithContext(
        thisValue,
        otherValue,
        treatQuietNansAsSignaling,
        ctx);
    }

    /**
     * Compares a T object with this instance.
     * @param thisValue A T object.
     * @param otherValue A T object. (2).
     * @return Zero if the values are equal; a negative number if this instance
     * is less, or a positive number if this instance is greater.
     */
    public int compareTo(T thisValue, T otherValue) {
      return this.wrapper.compareTo(thisValue, otherValue);
    }

    /**
     * Not documented yet.
     * @param thisValue A T object. (2).
     * @param ctx A PrecisionContext object.
     * @return A T object.
     */
    public T RoundToPrecisionRaw(T thisValue, PrecisionContext ctx) {
      // System.out.println("toprecraw " + thisValue);
      return this.wrapper.RoundToPrecisionRaw(thisValue, ctx);
    }
  }
