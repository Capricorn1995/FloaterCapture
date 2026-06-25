package com.floatercapture.data.model

data class AppRule(
    val packageName: String = "",
    val appName: String = "",
    val imageViewClasses: List<String> = emptyList(),
    val videoViewClasses: List<String> = emptyList(),
    val specialRules: String? = null
)
