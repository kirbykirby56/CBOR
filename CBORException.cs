/*
Written in 2013 by Peter O.
Any copyright is dedicated to the Public Domain.
http://creativecommons.org/publicdomain/zero/1.0/
If you like this, you should donate to Peter O.
at: http://upokecenter.com/d/
 */
using System;
using System.Runtime.Serialization;
namespace PeterO {
    /// <summary> Exception thrown for errors involving CBOR data. </summary>
    [Serializable]
    public class CBORException : Exception, ISerializable {
    /// <summary> </summary>
    public CBORException() {
    }
    /// <summary> </summary>
    /// <param name='message'> A string object.</param>
    public CBORException(string message)
      : base(message) {
    }
    /// <summary> </summary>
    /// <param name='message'> A string object.</param>
    /// <param name='innerException'> A Exception object.</param>
    public CBORException(string message, Exception innerException)
      : base(message, innerException) {
    }
    /// <summary> </summary>
    /// <param name='info'> A SerializationInfo object.</param>
    /// <param name='context'> A StreamingContext object.</param>
    protected CBORException(SerializationInfo info, StreamingContext context)
      : base(info, context) {
    }
  }
}