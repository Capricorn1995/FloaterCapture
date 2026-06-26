package com.floatercapture.util

import com.floatercapture.data.model.AppRule

object AppRulesLoader {

    private val rules: Map<String, AppRule> = mapOf(
        // ========================
        // 国内社交/通讯
        // ========================
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

        // ========================
        // 国内短视频/直播
        // ========================
        "com.smile.gifmaker" to AppRule(
            packageName = "com.smile.gifmaker",
            appName = "快手",
            videoViewClasses = listOf(
                "com.kuaishou.gifshow.media.player.PlayerView",
                "android.widget.VideoView",
                "android.view.TextureView",
                "android.view.SurfaceView"
            ),
            imageViewClasses = listOf("android.widget.ImageView"),
            description = "快手短视频适配规则"
        ),
        "tv.danmaku.bili" to AppRule(
            packageName = "tv.danmaku.bili",
            appName = "哔哩哔哩",
            videoViewClasses = listOf(
                "tv.danmaku.ijk.media.player.IjkVideoView",
                "com.bilibili.lib.ui.widget.BiliVideoView",
                "android.widget.VideoView",
                "android.view.TextureView"
            ),
            imageViewClasses = listOf("android.widget.ImageView"),
            description = "B站视频和图片适配规则"
        ),

        // ========================
        // 国内电商
        // ========================
        "com.taobao.taobao" to AppRule(
            packageName = "com.taobao.taobao",
            appName = "淘宝",
            imageViewClasses = listOf(
                "com.taobao.taobao.widget.TBImageView",
                "com.taobao.taobao.image.TBImage",
                "android.widget.ImageView"
            ),
            description = "淘宝商品图片适配规则"
        ),
        "com.jingdong.app.mall" to AppRule(
            packageName = "com.jingdong.app.mall",
            appName = "京东",
            imageViewClasses = listOf(
                "com.jd.lib.productdetail.ProductImageView",
                "android.widget.ImageView"
            ),
            description = "京东商品图片适配规则"
        ),
        "com.xunmeng.pinduoduo" to AppRule(
            packageName = "com.xunmeng.pinduoduo",
            appName = "拼多多",
            imageViewClasses = listOf(
                "com.xunmeng.pinduoduo.widget.PDDImageView",
                "android.widget.ImageView"
            ),
            description = "拼多多商品图片适配规则"
        ),

        // ========================
        // 国内资讯/社区
        // ========================
        "com.ss.android.article.news" to AppRule(
            packageName = "com.ss.android.article.news",
            appName = "今日头条",
            imageViewClasses = listOf(
                "com.bytedance.article.common.image.TTImageView",
                "android.widget.ImageView"
            ),
            videoViewClasses = listOf(
                "com.bytedance.ies.ugc.aweme.mediaplayer.PlayerView",
                "android.widget.VideoView"
            ),
            description = "今日头条图文视频适配规则"
        ),
        "com.douban.frodo" to AppRule(
            packageName = "com.douban.frodo",
            appName = "豆瓣",
            imageViewClasses = listOf("android.widget.ImageView"),
            description = "豆瓣社区图片适配规则"
        ),
        "com.coolapk.market" to AppRule(
            packageName = "com.coolapk.market",
            appName = "酷安",
            imageViewClasses = listOf(
                "com.coolapk.market.widget.CoolImageView",
                "android.widget.ImageView"
            ),
            description = "酷安社区图片适配规则"
        ),
        "com.xiaoying.card" to AppRule(
            packageName = "com.xiaoying.card",
            appName = "最右",
            imageViewClasses = listOf("android.widget.ImageView"),
            videoViewClasses = listOf("android.widget.VideoView", "android.view.TextureView"),
            description = "最右社区图片视频适配规则"
        ),

        // ========================
        // 国内音乐/工具
        // ========================
        "com.netease.cloudmusic" to AppRule(
            packageName = "com.netease.cloudmusic",
            appName = "网易云音乐",
            imageViewClasses = listOf(
                "com.netease.cloudmusic.widget.AlbumImageView",
                "android.widget.ImageView"
            ),
            description = "网易云音乐专辑图片适配规则"
        ),
        "com.meitu.meiyancamera" to AppRule(
            packageName = "com.meitu.meiyancamera",
            appName = "美图秀秀",
            imageViewClasses = listOf(
                "com.meitu.meiyancamera.widget.MTImageView",
                "android.widget.ImageView"
            ),
            description = "美图秀秀图片适配规则"
        ),

        // ========================
        // 国际社交/通讯
        // ========================
        "org.telegram.messenger" to AppRule(
            packageName = "org.telegram.messenger",
            appName = "Telegram",
            imageViewClasses = listOf(
                "org.telegram.ui.Components.BackupImageView",
                "org.telegram.ui.Components.AnimatedFileDrawable",
                "android.widget.ImageView"
            ),
            videoViewClasses = listOf(
                "org.telegram.ui.Components.VideoPlayer",
                "android.widget.VideoView",
                "android.view.TextureView"
            ),
            description = "Telegram聊天图片视频适配规则"
        ),
        "com.twitter.android" to AppRule(
            packageName = "com.twitter.android",
            appName = "Twitter/X",
            imageViewClasses = listOf(
                "com.twitter.ui.widget.TweetImageView",
                "android.widget.ImageView"
            ),
            videoViewClasses = listOf(
                "com.twitter.ui.widget.VideoView",
                "android.widget.VideoView"
            ),
            description = "Twitter/X推文图片视频适配规则"
        ),
        "com.instagram.android" to AppRule(
            packageName = "com.instagram.android",
            appName = "Instagram",
            imageViewClasses = listOf(
                "com.instagram.common.ui.widget.imageview.IgImageView",
                "com.instagram.feed.widget.IgImageView",
                "android.widget.ImageView"
            ),
            videoViewClasses = listOf(
                "com.instagram.feed.widget.VideoView",
                "android.widget.VideoView"
            ),
            description = "Instagram图片视频适配规则"
        ),
        "com.zhiliaoapp.musically" to AppRule(
            packageName = "com.zhiliaoapp.musically",
            appName = "TikTok",
            videoViewClasses = listOf(
                "com.bytedance.ies.ugc.aweme.mediaplayer.PlayerView",
                "android.widget.VideoView",
                "android.view.TextureView",
                "android.view.SurfaceView"
            ),
            imageViewClasses = listOf("android.widget.ImageView"),
            description = "TikTok短视频适配规则"
        ),
        "com.google.android.youtube" to AppRule(
            packageName = "com.google.android.youtube",
            appName = "YouTube",
            videoViewClasses = listOf(
                "com.google.android.youtube.player.YouTubePlayerView",
                "android.widget.VideoView",
                "android.view.TextureView",
                "android.view.SurfaceView"
            ),
            imageViewClasses = listOf("android.widget.ImageView"),
            description = "YouTube视频适配规则"
        ),
        "com.facebook.katana" to AppRule(
            packageName = "com.facebook.katana",
            appName = "Facebook",
            imageViewClasses = listOf(
                "com.facebook.drawee.view.SimpleDraweeView",
                "com.facebook.fresco.view.FrescoImageView",
                "android.widget.ImageView"
            ),
            videoViewClasses = listOf("android.widget.VideoView"),
            description = "Facebook图片视频适配规则"
        ),
        "com.whatsapp" to AppRule(
            packageName = "com.whatsapp",
            appName = "WhatsApp",
            imageViewClasses = listOf(
                "com.whatsapp.mediaview.MediaView",
                "android.widget.ImageView"
            ),
            description = "WhatsApp聊天图片适配规则"
        ),
        "com.snapchat.android" to AppRule(
            packageName = "com.snapchat.android",
            appName = "Snapchat",
            imageViewClasses = listOf("android.widget.ImageView"),
            videoViewClasses = listOf("android.widget.VideoView", "android.view.TextureView"),
            description = "Snapchat图片视频适配规则"
        ),
        "com.reddit.frontpage" to AppRule(
            packageName = "com.reddit.frontpage",
            appName = "Reddit",
            imageViewClasses = listOf(
                "com.reddit.frontpage.ui.widget.RedditImageView",
                "android.widget.ImageView"
            ),
            videoViewClasses = listOf("android.widget.VideoView"),
            description = "Reddit图片视频适配规则"
        ),
        "com.pinterest" to AppRule(
            packageName = "com.pinterest",
            appName = "Pinterest",
            imageViewClasses = listOf(
                "com.pinterest.ui.widget.PinImageView",
                "android.widget.ImageView"
            ),
            description = "Pinterest图片适配规则"
        ),
        "com.spotify.music" to AppRule(
            packageName = "com.spotify.music",
            appName = "Spotify",
            imageViewClasses = listOf(
                "com.spotify.music.widget.AlbumImageView",
                "android.widget.ImageView"
            ),
            description = "Spotify专辑图片适配规则"
        ),

        // ========================
        // 浏览器
        // ========================
        "com.android.chrome" to AppRule(
            packageName = "com.android.chrome",
            appName = "Chrome浏览器",
            webViewClasses = listOf(
                "android.webkit.WebView",
                "org.chromium.content.browser.ContentViewCore",
                "org.chromium.android_webview.AwContents"
            ),
            imageViewClasses = listOf("android.widget.ImageView"),
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
            imageViewClasses = listOf("android.widget.ImageView"),
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
            imageViewClasses = listOf("android.widget.ImageView"),
            description = "Chrome Dev WebView处理规则"
        ),
        "org.mozilla.firefox" to AppRule(
            packageName = "org.mozilla.firefox",
            appName = "Firefox",
            webViewClasses = listOf(
                "android.webkit.WebView",
                "org.mozilla.gecko.GeckoView"
            ),
            imageViewClasses = listOf("android.widget.ImageView"),
            description = "Firefox浏览器适配规则"
        ),
        "com.microsoft.emmx" to AppRule(
            packageName = "com.microsoft.emmx",
            appName = "Microsoft Edge",
            webViewClasses = listOf("android.webkit.WebView"),
            imageViewClasses = listOf("android.widget.ImageView"),
            description = "Edge浏览器适配规则"
        ),
        "com.sec.android.app.sbrowser" to AppRule(
            packageName = "com.sec.android.app.sbrowser",
            appName = "Samsung Internet",
            webViewClasses = listOf("android.webkit.WebView"),
            imageViewClasses = listOf("android.widget.ImageView"),
            description = "三星浏览器适配规则"
        ),
        "com.UCMobile" to AppRule(
            packageName = "com.UCMobile",
            appName = "UC浏览器",
            webViewClasses = listOf("android.webkit.WebView", "com.uc.webview.export.WebView"),
            imageViewClasses = listOf("android.widget.ImageView"),
            description = "UC浏览器适配规则"
        ),
    )

    fun getRule(packageName: String): AppRule? {
        return rules[packageName]
    }

    fun getAllRules(): List<AppRule> {
        return rules.values.toList()
    }
}
