package kotlinx.asm

class ByteVector internal constructor(var data: ByteArray, length: Int) {
    var length: Int = length;

    constructor() : this(ByteArray(64), 0)

    constructor(initialCapacity: Int) : this(ByteArray(initialCapacity), 0)

    internal constructor(data: ByteArray) : this(data, 0)

    fun putByte(byteValue: Int): ByteVector {
        var currentLength = length
        if (currentLength + 1 > data.size) {
            enlarge(1)
        }
        data[currentLength++] = byteValue.toByte()
        length = currentLength
        return this
    }

    fun put11(byteValue1: Int, byteValue2: Int): ByteVector {
        var currentLength = length
        if (currentLength + 2 > data.size) {
            enlarge(2)
        }
        val currentData = data
        currentData[currentLength++] = byteValue1.toByte()
        currentData[currentLength++] = byteValue2.toByte()
        length = currentLength
        return this
    }

    fun putShort(shortValue: Int): ByteVector {
        var currentLength = length
        if (currentLength + 2 > data.size) {
            enlarge(2)
        }
        val currentData = data
        currentData[currentLength++] = (shortValue ushr 8).toByte()
        currentData[currentLength++] = shortValue.toByte()
        length = currentLength
        return this
    }

    fun put12(byteValue: Int, shortValue: Int): ByteVector {
        var currentLength = length
        if (currentLength + 3 > data.size) {
            enlarge(3)
        }
        val currentData = data
        currentData[currentLength++] = byteValue.toByte()
        currentData[currentLength++] = (shortValue ushr 8).toByte()
        currentData[currentLength++] = shortValue.toByte()
        length = currentLength
        return this
    }

    fun put112(byteValue1: Int, byteValue2: Int, shortValue: Int): ByteVector {
        var currentLength = length
        if (currentLength + 4 > data.size) {
            enlarge(4)
        }
        val currentData = data
        currentData[currentLength++] = byteValue1.toByte()
        currentData[currentLength++] = byteValue2.toByte()
        currentData[currentLength++] = (shortValue ushr 8).toByte()
        currentData[currentLength++] = shortValue.toByte()
        length = currentLength
        return this
    }

    fun putInt(intValue: Int): ByteVector {
        var currentLength = length
        if (currentLength + 4 > data.size) {
            enlarge(4)
        }
        val currentData = data
        currentData[currentLength++] = (intValue ushr 24).toByte()
        currentData[currentLength++] = (intValue ushr 16).toByte()
        currentData[currentLength++] = (intValue ushr 8).toByte()
        currentData[currentLength++] = intValue.toByte()
        length = currentLength
        return this
    }
    
    fun put122(byteValue: Int, shortValue1: Int, shortValue2: Int): ByteVector {
        var currentLength = length
        if (currentLength + 5 > data.size) {
            enlarge(5)
        }
        val currentData = data
        currentData[currentLength++] = byteValue.toByte()
        currentData[currentLength++] = (shortValue1 ushr 8).toByte()
        currentData[currentLength++] = shortValue1.toByte()
        currentData[currentLength++] = (shortValue2 ushr 8).toByte()
        currentData[currentLength++] = shortValue2.toByte()
        length = currentLength
        return this
    }
    
    fun putLong(longValue: Long): ByteVector {
        var currentLength = length
        if (currentLength + 8 > data.size) {
            enlarge(8)
        }
        val currentData = data
        var intValue = (longValue ushr 32).toInt()
        currentData[currentLength++] = (intValue ushr 24).toByte()
        currentData[currentLength++] = (intValue ushr 16).toByte()
        currentData[currentLength++] = (intValue ushr 8).toByte()
        currentData[currentLength++] = intValue.toByte()
        intValue = longValue.toInt()
        currentData[currentLength++] = (intValue ushr 24).toByte()
        currentData[currentLength++] = (intValue ushr 16).toByte()
        currentData[currentLength++] = (intValue ushr 8).toByte()
        currentData[currentLength++] = intValue.toByte()
        length = currentLength
        return this
    }
    
    fun putUTF8(stringValue: String): ByteVector {
        val charLength = stringValue.length
        require(charLength <= 65535) { "UTF8 string too large" }
        var currentLength = length
        if (currentLength + 2 + charLength > data.size) {
            enlarge(2 + charLength)
        }
        val currentData = data
        // Optimistic algorithm: instead of computing the byte length and then serializing the string
        // (which requires two loops), we assume the byte length is equal to char length (which is the
        // most frequent case), and we start serializing the string right away. During the
        // serialization, if we find that this assumption is wrong, we continue with the general method.
        currentData[currentLength++] = (charLength ushr 8).toByte()
        currentData[currentLength++] = charLength.toByte()
        for (i in 0..<charLength) {
            val charValue = stringValue[i]
            if (charValue in '\u0001'..'\u007F') {
                currentData[currentLength++] = charValue.code.toByte()
            } else {
                length = currentLength
                return encodeUtf8(stringValue, i, 65535)
            }
        }
        length = currentLength
        return this
    }

    fun encodeUtf8(stringValue: String, offset: Int, maxByteLength: Int): ByteVector {
        val charLength = stringValue.length
        var byteLength = offset
        for (i in offset..<charLength) {
            val charValue = stringValue[i]
            if (charValue.code in 0x0001..0x007F) {
                byteLength++
            } else if (charValue.code <= 0x07FF) {
                byteLength += 2
            } else {
                byteLength += 3
            }
        }
        require(byteLength <= maxByteLength) { "UTF8 string too large" }
        // Compute where 'byteLength' must be stored in 'data', and store it at this location.
        val byteLengthOffset = length - offset - 2
        if (byteLengthOffset >= 0) {
            data[byteLengthOffset] = (byteLength ushr 8).toByte()
            data[byteLengthOffset + 1] = byteLength.toByte()
        }
        if (length + byteLength - offset > data.size) {
            enlarge(byteLength - offset)
        }
        var currentLength = length
        for (i in offset..<charLength) {
            val charValue = stringValue[i]
            if (charValue.code in 0x0001..0x007F) {
                data[currentLength++] = charValue.code.toByte()
            } else if (charValue.code <= 0x07FF) {
                data[currentLength++] = (0xC0 or (charValue.code shr 6 and 0x1F)).toByte()
                data[currentLength++] = (0x80 or (charValue.code and 0x3F)).toByte()
            } else {
                data[currentLength++] = (0xE0 or (charValue.code shr 12 and 0xF)).toByte()
                data[currentLength++] = (0x80 or (charValue.code shr 6 and 0x3F)).toByte()
                data[currentLength++] = (0x80 or (charValue.code and 0x3F)).toByte()
            }
        }
        length = currentLength
        return this
    }

    fun putByteArray(
        byteArrayValue: ByteArray?, byteOffset: Int, byteLength: Int
    ): ByteVector {
        if (length + byteLength > data.size) {
            enlarge(byteLength)
        }
        byteArrayValue?.copyInto(data, length, byteOffset, byteOffset + byteLength)
        length += byteLength
        return this
    }

    private fun enlarge(size: Int) {
        require(length > data.size) {
            "Internal error"
        }
        val doubleCapacity = 2 * data.size
        val minimalCapacity = length + size
        val newData = ByteArray(if (doubleCapacity > minimalCapacity) doubleCapacity else minimalCapacity)
        data.copyInto(newData)
        data = newData
    }
}