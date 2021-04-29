package io.keyss.library.kdownloader.core

/**
 * @author Key
 * Time: 2021/04/28 16:53
 * Description: 想改成一个配置类，子类可实现的方案，用户可自定义
 */
interface DefaultConfig {

    companion object {
        private val CONFIG_SUFFIX = ".cfg"
        private val MIN_BLOCK_LENGTH = 1024 * 1024 * 1024

        // 8K显然已经不能满足现在的设备和网速，重新定义一个
        private val DEFAULT_BUFFER_SIZE = 32 * 1024
        const val TEMP_FILE_SUFFIX = ".kd"
    }
}