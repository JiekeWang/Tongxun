package com.tongxun.utils

import com.tongxun.data.remote.NetworkModule

object ImageUrlUtils {
    /**
     * 获取完整的图片URL
     * @param imageUrl 图片URL（可能是相对路径或绝对路径）
     * @return 完整的图片URL
     */
    fun getFullImageUrl(imageUrl: String?): String? {
        if (imageUrl.isNullOrBlank()) {
            return null
        }
        
        // 如果已经是完整的URL（http://或https://开头），直接返回
        if (imageUrl.startsWith("http://") || imageUrl.startsWith("https://")) {
            return imageUrl
        }
        
        // 如果是相对路径，拼接BASE_URL（去掉/api/，因为图片是静态资源）
        val baseUrl = NetworkModule.BASE_URL.removeSuffix("/api/").removeSuffix("/")
        return if (imageUrl.startsWith("/")) {
            "$baseUrl$imageUrl"
        } else {
            "$baseUrl/$imageUrl"
        }
    }
}

