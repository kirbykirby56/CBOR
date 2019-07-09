/*
Any copyright is dedicated to the Public Domain.
http://creativecommons.org/publicdomain/zero/1.0/
If you like this, you should donate to Peter O.
at: http://peteroupc.github.io/
 */
using System;

namespace PeterO.Cbor {
    /// <include file='../../docs.xml'
    /// path='docs/doc[@name="T:PeterO.Cbor.CBORException"]/*'/>
  public class CBORException : Exception {
    /// <summary>Initializes a new instance of the <see cref='CBORException'/> class.</summary>
    public CBORException() {
    }

    /// <summary>Initializes a new instance of the <see cref='CBORException'/> class.</summary>
    /// <param name='message'>The parameter <paramref name='message'/> is a
    /// text string.</param>
    public CBORException(string message) : base(message) {
    }

    /// <summary>Initializes a new instance of the <see cref='CBORException'/> class. Uses the given message and inner
    /// exception.</summary>
    /// <param name='message'>The parameter <paramref name='message'/> is a
    /// text string.</param>
    /// <param name='innerException'>The parameter <paramref name='innerException'/> is an Exception object.</param>
    public CBORException(string message, Exception innerException)
      : base(message, innerException) {
    }
  }
}
