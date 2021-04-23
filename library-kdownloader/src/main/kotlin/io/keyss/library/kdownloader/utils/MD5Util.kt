package io.keyss.library.kdownloader.utils

import java.security.GeneralSecurityException
import java.security.MessageDigest

/**
 * @author Key
 * Time: 2021/04/23 15:58
 * Description:
 */
object MD5Util {
    @Throws(GeneralSecurityException::class)
    fun stringToMD5(string: String): String {
        return bytesToMD5(string.encodeToByteArray())
    }

    @Throws(GeneralSecurityException::class)
    fun bytesToMD5(bytes: ByteArray): String {
        val md5 = MessageDigest.getInstance("MD5")
        val digest: ByteArray = md5.digest(bytes)
        val sb: StringBuffer = StringBuffer()
        for (byte in digest) {
            //获取低八位有效值
            val i: Int = byte.toInt() and 0xff
            if (i < 16) {
                //如果是一位的话，补0
                sb.append("0")
            }
            //将整数转化为16进制
            sb.append(Integer.toHexString(i))
        }
        return sb.toString()
    }
}