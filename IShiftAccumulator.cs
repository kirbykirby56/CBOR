using System;
using System.Numerics;

namespace PeterO {
  interface IShiftAccumulator {
    BigInteger ShiftedInt { get; }
    long DigitLength { get; }
    int OlderDiscardedDigits { get; }
    int LastDiscardedDigit { get; }
    long ShiftedIntSmall { get; }
    FastInteger DiscardedDigitCount { get; }
    void ShiftRight(FastInteger bits);
    void ShiftRight(long bits);
    void ShiftToDigits(long bits);
  }
}