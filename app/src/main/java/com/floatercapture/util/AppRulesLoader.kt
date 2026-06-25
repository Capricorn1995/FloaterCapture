package com.floatercapture.util

data class AppRule(
    val packageName: String,
    val appName: String,
    val imageViewClasses: List<String> = emptyList(),
    val videoViewClasses: List<String> = emptyList(),
    val webViewClasses: List<String> = emptyList(),
    val description: String = ""
)

object AppRulesLoader {

    private val rules: Map<String, AppRule> = mapOf(
        "com.tencent.mm" to AppRule(
            packageName = "com.tencent.mm",
            appName = "微信",
            imageViewClasses = listOf(
                "com.tencent.mm.ui.widget.MMNeat7ImageView",
                "com.tencent.mm.ui.widget.imageview.WeImageView",
                "com.tencent.mm.plugin.gif.MMAnimateView",
                "com.tencent.mm.plugin.sns.ui.SnsImageView",
                "com.tencent.mm.plugin.sns.ui.AsyncMaskImageView",
                "android.widget.ImageView"
            ),
            description = "微信朋友圈、聊天图片适配规则"
        ),
        "com.sina.weibo" to AppRule(
            packageName = "com.sina.weibo",
            appName = "微博",
            imageViewClasses = listOf(
                "com.sina.weibo.image.viewer.WeiboImageView",
                "com.sina.weibo.image.viewer.PhotoView",
                "android.widget.ImageView"
            ),
            description = "微博图片浏览适配规则"
        ),
        "com.ss.android.ugc.aweme" to AppRule(
            packageName = "com.ss.android.ugc.aweme",
            appName = "抖音",
            videoViewClasses = listOf(
                "com.bytedance.ies.ugc.aweme.mediaplayer.PlayerView",
                "android.widget.VideoView",
                "android.view.TextureView",
                "android.view.SurfaceView"
            ),
            description = "抖音视频播放适配规则"
        ),
        "com.xingin.xhs" to AppRule(
            packageName = "com.xingin.xhs",
            appName = "小红书",
            imageViewClasses = listOf(
                "com.xingin.xhs.widget.imageview.PinchImageView",
                "com.xingin.xhs.widget.imageview.StretchImageView",
                "android.widget.ImageView"
            ),
            description = "小红书笔记图片适配规则"
        ),
        "com.zhihu.android" to AppRule(
            packageName = "com.zhihu.android",
            appName = "知乎",
            imageViewClasses = listOf(
                "com.zhihu.android.app.ui.widget.ZHImageView",
                "com.zhihu.android.app.ui.widget.AutoScaleImageView",
                "android.widget.ImageView"
            ),
            description = "知乎回答图片适配规则"
        ),
        "com.tencent.mobileqq" to AppRule(
            packageName = "com.tencent.mobileqq",
            appName = "QQ",
            imageViewClasses = listOf(
                "com.tencent.mobileqq.widget.QQPhotoView",
                "com.tencent.mobileqq.pic.PicImageView",
                "com.tencent.image.URLImageView",
                "android.widget.ImageView"
            ),
            description = "QQ聊天、空间图片适配规则"
        ),
        "com.android.chrome" to AppRule(
            packageName = "com.android.chrome",
            appName = "Chrome浏览器",
            webViewClasses = listOf(
                "android.webkit.WebView",
                "org.chromium.content.browser.ContentViewCore",
                "org.chromium.android_webview.AwContents"
            ),
            description = "Chrome浏览器WebView处理规则"
        ),
        "com.chrome.beta" to AppRule(
            packageName = "com.chrome.beta",
            appName = "Chrome Beta",
            webViewClasses = listOf(
                "android.webkit.WebView",
                "org.chromium.content.browser.ContentViewCore",
                "org.chromium.android_webview.AwContents"
            ),
            description = "Chrome Beta WebView处理规则"
        ),
        "com.chrome.dev" to AppRule(
            packageName = "com.chrome.dev",
            appName = "Chrome Dev",
            webViewClasses = listOf(
                "android.webkit.WebView",
                "org.chromium.content.browser.ContentViewCore",
                "org.chromium.android_webview.AwContents"
            ),
            description = "Chrome Dev WebView处理规则"
        )
    )

    fun getRule(packageName: String): AppRule? {
        return rules[packageName]
    }

    fun getAllRules(): List<AppRule> {
        return rules.values.toList()
    }
}
