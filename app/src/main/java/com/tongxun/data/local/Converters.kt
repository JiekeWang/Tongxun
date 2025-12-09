package com.tongxun.data.local

import androidx.room.TypeConverter
import com.tongxun.data.model.MessageType
import com.tongxun.data.local.entity.MessageStatus
import com.tongxun.data.local.entity.ConversationType

class Converters {
    
    @TypeConverter
    fun fromMessageType(value: MessageType): String {
        return value.name
    }
    
    @TypeConverter
    fun toMessageType(value: String): MessageType {
        return MessageType.valueOf(value)
    }
    
    @TypeConverter
    fun fromMessageStatus(value: MessageStatus): String {
        return value.name
    }
    
    @TypeConverter
    fun toMessageStatus(value: String): MessageStatus {
        return MessageStatus.valueOf(value)
    }
    
    @TypeConverter
    fun fromConversationType(value: ConversationType): String {
        return value.name
    }
    
    @TypeConverter
    fun toConversationType(value: String): ConversationType {
        return ConversationType.valueOf(value)
    }
}

