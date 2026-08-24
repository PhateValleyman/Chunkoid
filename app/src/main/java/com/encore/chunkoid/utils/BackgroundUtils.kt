package com.encore.chunkoid.utils

import android.app.Activity
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.palette.graphics.Palette
import androidx.preference.PreferenceManager
import com.encore.chunkoid.R
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.progressindicator.LinearProgressIndicator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.min

object BackgroundUtils {

    const val KEY_CUSTOM_BG_ENABLED = "custom_bg_enabled"
    const val KEY_EXTRACT_COLOR = "custom_bg_extract_color"
    const val KEY_BG_DIM = "custom_bg_dim"
    const val KEY_BG_BLUR = "custom_bg_blur"
    const val KEY_EXTRACTED_PRIMARY = "custom_bg_primary_color"
    const val KEY_EXTRACTED_ON_PRIMARY = "custom_bg_on_primary_color"
    const val KEY_EXTRACTED_CONTAINER = "custom_bg_container_color"
    const val KEY_EXTRACTED_ON_CONTAINER = "custom_bg_on_container_color"

    private const val WALLPAPER_FILENAME = "custom_wallpaper.png"
    private const val WALLPAPER_BLURRED_FILENAME = "custom_wallpaper_blurred.png"

    private var memoryCachedBitmap: Bitmap? = null

    fun getWallpaperFile(context: Context): File {
        return File(context.filesDir, WALLPAPER_FILENAME)
    }

    fun getBlurredWallpaperFile(context: Context): File {
        return File(context.filesDir, WALLPAPER_BLURRED_FILENAME)
    }

    fun isCustomBackgroundEnabled(context: Context): Boolean {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        return prefs.getBoolean(KEY_CUSTOM_BG_ENABLED, false) && getWallpaperFile(context).exists()
    }

    fun isColorExtractionEnabled(context: Context): Boolean {
        if (!isCustomBackgroundEnabled(context)) return false
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        return prefs.getBoolean(KEY_EXTRACT_COLOR, true)
    }

    fun getExtractedPrimaryColor(context: Context): Int? {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val color = prefs.getInt(KEY_EXTRACTED_PRIMARY, 0)
        return if (color != 0) color else null
    }

    fun getExtractedOnPrimaryColor(context: Context): Int {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val color = prefs.getInt(KEY_EXTRACTED_ON_PRIMARY, 0)
        if (color != 0) return color
        return if (isSystemInDarkTheme(context)) Color.parseColor("#0A1424") else Color.WHITE
    }

    fun getExtractedContainerColor(context: Context): Int? {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val color = prefs.getInt(KEY_EXTRACTED_CONTAINER, 0)
        return if (color != 0) color else null
    }

    fun getExtractedOnContainerColor(context: Context): Int? {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val color = prefs.getInt(KEY_EXTRACTED_ON_CONTAINER, 0)
        return if (color != 0) color else null
    }

    suspend fun saveCustomWallpaper(context: Context, imageUri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            val inputStream = context.contentResolver.openInputStream(imageUri) ?: return@withContext false
            val originalBitmap = BitmapFactory.decodeStream(inputStream)
            inputStream.close()

            if (originalBitmap == null) return@withContext false

            val maxDimension = 1920
            val width = originalBitmap.width
            val height = originalBitmap.height
            val scale = if (width > maxDimension || height > maxDimension) {
                maxDimension.toFloat() / max(width, height)
            } else {
                1.0f
            }

            val scaledBitmap = if (scale < 1.0f) {
                Bitmap.createScaledBitmap(originalBitmap, (width * scale).toInt(), (height * scale).toInt(), true)
            } else {
                originalBitmap
            }

            val wallpaperFile = getWallpaperFile(context)
            FileOutputStream(wallpaperFile).use { out ->
                scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }

            val palette = Palette.from(scaledBitmap).generate()
            val isDark = isSystemInDarkTheme(context)

            val rawSwatch = if (isDark) {
                palette.vibrantSwatch ?: palette.lightVibrantSwatch ?: palette.dominantSwatch ?: palette.mutedSwatch
            } else {
                palette.darkVibrantSwatch ?: palette.vibrantSwatch ?: palette.dominantSwatch ?: palette.mutedSwatch
            }

            val rawColor = rawSwatch?.rgb ?: ContextCompat.getColor(context, R.color.md_theme_primary)

            val hsl = FloatArray(3)
            ColorUtils.colorToHSL(rawColor, hsl)
            hsl[1] = hsl[1].coerceIn(0.40f, 0.70f)

            val primaryColor: Int
            val onPrimaryColor: Int
            val containerColor: Int
            val onContainerColor: Int

            if (isDark) {
                hsl[2] = 0.78f
                primaryColor = ColorUtils.HSLToColor(hsl)
                onPrimaryColor = Color.parseColor("#0A1424")

                hsl[2] = 0.26f
                hsl[1] = (hsl[1] * 0.75f).coerceIn(0.20f, 0.50f)
                containerColor = ColorUtils.HSLToColor(hsl)
                onContainerColor = primaryColor
            } else {
                hsl[2] = 0.38f
                primaryColor = ColorUtils.HSLToColor(hsl)
                onPrimaryColor = Color.WHITE

                hsl[2] = 0.90f
                hsl[1] = (hsl[1] * 0.60f).coerceIn(0.15f, 0.40f)
                containerColor = ColorUtils.HSLToColor(hsl)
                onContainerColor = primaryColor
            }

            val prefs = PreferenceManager.getDefaultSharedPreferences(context)
            prefs.edit()
                .putInt(KEY_EXTRACTED_PRIMARY, primaryColor)
                .putInt(KEY_EXTRACTED_ON_PRIMARY, onPrimaryColor)
                .putInt(KEY_EXTRACTED_CONTAINER, containerColor)
                .putInt(KEY_EXTRACTED_ON_CONTAINER, onContainerColor)
                .apply()

            val blurRadius = prefs.getInt(KEY_BG_BLUR, 15).coerceIn(5, 40)
            val downsampled = Bitmap.createScaledBitmap(
                scaledBitmap,
                (scaledBitmap.width / 4).coerceAtLeast(1),
                (scaledBitmap.height / 4).coerceAtLeast(1),
                true
            )
            val blurredBitmap = fastBlur(downsampled, blurRadius)

            val blurredFile = getBlurredWallpaperFile(context)
            FileOutputStream(blurredFile).use { out ->
                blurredBitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
            }

            memoryCachedBitmap = blurredBitmap

            return@withContext true
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext false
        }
    }

    fun clearCustomWallpaper(context: Context) {
        getWallpaperFile(context).delete()
        getBlurredWallpaperFile(context).delete()
        memoryCachedBitmap = null

        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        prefs.edit()
            .putBoolean(KEY_CUSTOM_BG_ENABLED, false)
            .remove(KEY_EXTRACTED_PRIMARY)
            .remove(KEY_EXTRACTED_ON_PRIMARY)
            .remove(KEY_EXTRACTED_CONTAINER)
            .remove(KEY_EXTRACTED_ON_CONTAINER)
            .apply()
    }

    fun getWallpaperBitmap(context: Context): Bitmap? {
        if (memoryCachedBitmap != null && !memoryCachedBitmap!!.isRecycled) {
            return memoryCachedBitmap
        }
        val file = getBlurredWallpaperFile(context)
        if (file.exists()) {
            memoryCachedBitmap = BitmapFactory.decodeFile(file.absolutePath)
            return memoryCachedBitmap
        }
        val rawFile = getWallpaperFile(context)
        if (rawFile.exists()) {
            memoryCachedBitmap = BitmapFactory.decodeFile(rawFile.absolutePath)
            return memoryCachedBitmap
        }
        return null
    }

    /**
     * Apply wallpaper layer and full-spectrum dynamic Monet harmonization across all controls.
     */
    fun applyCustomBackground(activity: Activity) {
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(activity.window, false)
        activity.window.statusBarColor = android.graphics.Color.TRANSPARENT
        activity.window.navigationBarColor = android.graphics.Color.TRANSPARENT
        
        val ivWallpaper = activity.findViewById<ImageView>(R.id.iv_custom_wallpaper)
        val vScrim = activity.findViewById<View>(R.id.v_custom_scrim)
        val isDark = isSystemInDarkTheme(activity)

        if (!isCustomBackgroundEnabled(activity)) {
            ivWallpaper?.visibility = View.GONE
            vScrim?.visibility = View.GONE
            return
        }

        val bitmap = getWallpaperBitmap(activity) ?: return
        val prefs = PreferenceManager.getDefaultSharedPreferences(activity)
        val dimPercent = prefs.getInt(KEY_BG_DIM, 25).coerceIn(0, 80)

        if (ivWallpaper != null && vScrim != null) {
            ivWallpaper.setImageBitmap(bitmap)
            ivWallpaper.visibility = View.VISIBLE

            val alpha = (dimPercent * 255 / 100).coerceIn(0, 255)
            val scrimColor = if (isDark) Color.argb(alpha, 10, 15, 24) else Color.argb(alpha, 0, 0, 0)
            vScrim.setBackgroundColor(scrimColor)
            vScrim.visibility = View.VISIBLE
        }

        val contentRoot = activity.findViewById<ViewGroup>(android.R.id.content) ?: return
        if (contentRoot.childCount == 0) return
        val activityRoot = contentRoot.getChildAt(0) as? ViewGroup ?: return

        applyUnifiedMonetTheme(activityRoot, isDark, activity)
    }

    fun applyUnifiedMonetTheme(viewGroup: ViewGroup, isDark: Boolean, context: Context) {
        val isColorExtraction = isColorExtractionEnabled(context)
        var primary = if (isColorExtraction) getExtractedPrimaryColor(context) else null
        if (primary == null) {
            val typedValue = android.util.TypedValue()
            context.theme.resolveAttribute(com.google.android.material.R.attr.colorPrimary, typedValue, true)
            primary = typedValue.data
        }
        val onPrimary = getExtractedOnPrimaryColor(context)
        val container = if (isColorExtraction) getExtractedContainerColor(context) else null
        val onContainer = if (isColorExtraction) (getExtractedOnContainerColor(context) ?: primary) else null

        val glassBg = if (isDark) {
            if (primary != null) {
                ColorUtils.blendARGB(Color.argb(95, 20, 28, 42), primary, 0.05f)
            } else {
                Color.argb(95, 20, 28, 42)
            }
        } else {
            Color.argb(215, 255, 255, 255)
        }

        val glassStroke = if (isDark) {
            Color.argb(55, 255, 255, 255)
        } else {
            Color.argb(90, 255, 255, 255)
        }

        val drawerBg = glassBg
        val bottomNavBg = glassBg

        val navItemIds = listOf(
            R.id.nav_item_settings, R.id.nav_item_terminal, R.id.nav_item_resource_converter,
            R.id.nav_item_decrypt, R.id.nav_item_map_downloader, R.id.nav_item_help, R.id.nav_item_about
        )

        for (i in 0 until viewGroup.childCount) {
            val child = viewGroup.getChildAt(i)

            if (child.id == R.id.nav_drawer) {
                child.setBackgroundColor(drawerBg)
                val header = (child as? ViewGroup)?.getChildAt(0)
                if (header is ViewGroup) {
                    header.setBackgroundColor(Color.TRANSPARENT)
                }
            }

            if (child.id in navItemIds && child is ViewGroup) {
                if (primary != null) {
                    for (j in 0 until child.childCount) {
                        val grandChild = child.getChildAt(j)
                        if (grandChild is ImageView) {
                            grandChild.setColorFilter(primary)
                        }
                    }
                }
            }

            if (child is BottomNavigationView) {
                child.setBackgroundColor(bottomNavBg)
                child.elevation = 0f
                if (container != null && primary != null) {
                    child.itemActiveIndicatorColor = ColorStateList.valueOf(container)
                    val unselectedColor = ContextCompat.getColor(context, R.color.md_theme_onSurfaceVariant)
                    val states = arrayOf(
                        intArrayOf(android.R.attr.state_checked),
                        intArrayOf(-android.R.attr.state_checked)
                    )
                    val colors = intArrayOf(primary, unselectedColor)
                    val navColorStateList = ColorStateList(states, colors)
                    child.itemIconTintList = navColorStateList
                    child.itemTextColor = navColorStateList
                }
            }

            when (child) {
                is MaterialCardView -> {
                    child.cardElevation = 0f
                    child.setCardBackgroundColor(glassBg)
                    child.strokeColor = glassStroke
                    child.strokeWidth = 1
                }
                is MaterialButton -> {
                    if (primary != null && child.id != R.id.btn_cancel) {
                        if (child.backgroundTintList != null && child.backgroundTintList != ColorStateList.valueOf(Color.TRANSPARENT)) {
                            child.backgroundTintList = ColorStateList.valueOf(primary)
                            child.setTextColor(onPrimary)
                            child.iconTint = ColorStateList.valueOf(onPrimary)
                        }
                    }
                }
                is ExtendedFloatingActionButton -> {
                    if (primary != null) {
                        child.backgroundTintList = ColorStateList.valueOf(primary)
                        child.setTextColor(onPrimary)
                        child.iconTint = ColorStateList.valueOf(onPrimary)
                    }
                }
                is ProgressBar -> {
                    if (primary != null) {
                        child.progressTintList = ColorStateList.valueOf(primary)
                    }
                }
                is LinearProgressIndicator -> {
                    if (primary != null) {
                        child.setIndicatorColor(primary)
                    }
                }
                is com.google.android.material.materialswitch.MaterialSwitch -> {
                    if (primary != null && container != null) {
                        child.thumbTintList = ColorStateList.valueOf(primary)
                        child.trackTintList = ColorStateList.valueOf(ColorUtils.blendARGB(container, Color.TRANSPARENT, 0.5f))
                    }
                }
                is com.google.android.material.slider.Slider -> {
                    if (primary != null && container != null) {
                        child.thumbTintList = ColorStateList.valueOf(primary)
                        child.trackActiveTintList = ColorStateList.valueOf(primary)
                        child.trackInactiveTintList = ColorStateList.valueOf(container)
                    }
                }
                is ProgressBar -> {
                    if (primary != null && container != null) {
                        child.progressTintList = ColorStateList.valueOf(primary)
                        child.progressBackgroundTintList = ColorStateList.valueOf(ColorUtils.blendARGB(container, Color.TRANSPARENT, 0.5f))
                        child.indeterminateTintList = ColorStateList.valueOf(primary)
                    }
                }
            }

            if (child.id == R.id.btn_hero_circle && container != null && primary != null) {
                val circleDrawable = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(container)
                }
                child.background = circleDrawable
            }
            if (child is ImageView) {
                val primaryIconIds = listOf(
                    R.id.iv_hero_plus,
                    R.id.theme_check_system,
                    R.id.theme_check_light,
                    R.id.theme_check_dark,
                    R.id.btn_send_command,
                    R.id.iv_empty_folder,
                    R.id.iv_main_earth,
                    R.id.iv_decrypt_key,
                    R.id.iv_pack_folder,
                    R.id.iv_terminal_icon,
                    R.id.iv_author,
                    R.id.iv_bilibili,
                    R.id.iv_github,
                    R.id.iv_app_logo,
                    R.id.iv_nav_logo,
                    R.id.iv_splash_logo
                )
                if (child.id in primaryIconIds && primary != null) {
                    child.setColorFilter(primary)
                }
            }

            val glassTintIds = listOf(
                R.id.theme_option_system,
                R.id.theme_option_light,
                R.id.theme_option_dark,
                R.id.tv_clear,
                R.id.tv_exit,
                R.id.tv_debug_jdk,
                R.id.tv_debug_chunker,
                R.id.tv_symbol_dash,
                R.id.tv_symbol_slash,
                R.id.layout_direction_switcher,
                R.id.fl_main_earth,
                R.id.fl_decrypt_key,
                R.id.fl_pack_folder,
                R.id.fl_empty_folder,
                R.id.fl_terminal_icon,
                R.id.fl_social_author,
                R.id.fl_social_bilibili,
                R.id.fl_social_github,
                R.id.tv_log_level,
                R.id.tv_app_version,
                R.id.fl_app_logo,
                R.id.fl_nav_logo,
                R.id.fl_splash_logo
            )
            if (child.id in glassTintIds) {
                child.backgroundTintList = ColorStateList.valueOf(glassBg)
            }

            if (child is TextView) {
                val primaryTextIds = listOf(
                    R.id.tv_settings_header_conversion,
                    R.id.tv_settings_header_general,
                    R.id.tv_memory_value,
                    R.id.tv_dim_value,
                    R.id.tv_terminal_output,
                    R.id.tv_terminal_prompt,
                    R.id.tv_app_version,
                    R.id.tv_author_name,
                    R.id.tv_website_link,
                    R.id.tv_docs_link,
                    R.id.tv_progress_text,
                    R.id.tv_extract_progress
                )
                if (child.id in primaryTextIds && primary != null) {
                    child.setTextColor(primary)
                }
            }

            if (child is ViewGroup) {
                applyUnifiedMonetTheme(child, isDark, context)
            }
        }
    }

    fun isSystemInDarkTheme(context: Context): Boolean {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val mode = prefs.getString("night_mode", "system")
        return when (mode) {
            "dark" -> true
            "light" -> false
            else -> {
                val nightModeFlags = context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
                nightModeFlags == android.content.res.Configuration.UI_MODE_NIGHT_YES
            }
        }
    }

    /**
     * Fast StackBlur algorithm in pure Kotlin for smooth background blurring without native dependencies.
     */
    fun fastBlur(sentBitmap: Bitmap, radius: Int): Bitmap {
        val bitmap = sentBitmap.copy(sentBitmap.config ?: Bitmap.Config.ARGB_8888, true)
        if (radius < 1) return bitmap

        val w = bitmap.width
        val h = bitmap.height
        val pix = IntArray(w * h)
        bitmap.getPixels(pix, 0, w, 0, 0, w, h)

        val wm = w - 1
        val hm = h - 1
        val wh = w * h
        val div = radius + radius + 1

        val r = IntArray(wh)
        val g = IntArray(wh)
        val b = IntArray(wh)
        var rsum: Int
        var gsum: Int
        var bsum: Int
        var x: Int
        var y: Int
        var p: Int
        var yp: Int
        var yi: Int
        var yw: Int
        val vmin = IntArray(max(w, h))

        var divsum = (div + 1) shr 1
        divsum *= divsum
        val dv = IntArray(256 * divsum)
        for (idx in 0 until 256 * divsum) {
            dv[idx] = idx / divsum
        }

        yw = 0
        yi = 0

        val stack = Array(div) { IntArray(3) }
        var stackpointer: Int
        var stackstart: Int
        var sir: IntArray
        var rbs: Int
        val r1 = radius + 1
        var routsum: Int
        var goutsum: Int
        var boutsum: Int
        var rinsum: Int
        var ginsum: Int
        var binsum: Int

        for (yIdx in 0 until h) {
            rinsum = 0
            ginsum = 0
            binsum = 0
            routsum = 0
            goutsum = 0
            boutsum = 0
            rsum = 0
            gsum = 0
            bsum = 0
            for (iIdx in -radius..radius) {
                p = pix[yi + min(wm, max(iIdx, 0))]
                sir = stack[iIdx + radius]
                sir[0] = (p and 0xff0000) shr 16
                sir[1] = (p and 0x00ff00) shr 8
                sir[2] = (p and 0x0000ff)
                rbs = r1 - Math.abs(iIdx)
                rsum += sir[0] * rbs
                gsum += sir[1] * rbs
                bsum += sir[2] * rbs
                if (iIdx > 0) {
                    rinsum += sir[0]
                    ginsum += sir[1]
                    binsum += sir[2]
                } else {
                    routsum += sir[0]
                    goutsum += sir[1]
                    boutsum += sir[2]
                }
            }
            stackpointer = radius

            for (xIdx in 0 until w) {
                r[yi] = dv[rsum]
                g[yi] = dv[gsum]
                b[yi] = dv[bsum]

                rsum -= routsum
                gsum -= goutsum
                bsum -= boutsum

                stackstart = stackpointer - radius + div
                sir = stack[stackstart % div]

                routsum -= sir[0]
                goutsum -= sir[1]
                boutsum -= sir[2]

                if (yIdx == 0) {
                    vmin[xIdx] = min(xIdx + radius + 1, wm)
                }
                p = pix[yw + vmin[xIdx]]

                sir[0] = (p and 0xff0000) shr 16
                sir[1] = (p and 0x00ff00) shr 8
                sir[2] = (p and 0x0000ff)

                rinsum += sir[0]
                ginsum += sir[1]
                binsum += sir[2]

                rsum += rinsum
                gsum += ginsum
                bsum += binsum

                stackpointer = (stackpointer + 1) % div
                sir = stack[stackpointer % div]

                routsum += sir[0]
                goutsum += sir[1]
                boutsum += sir[2]

                rinsum -= sir[0]
                ginsum -= sir[1]
                binsum -= sir[2]

                yi++
            }
            yw += w
        }

        for (xIdx in 0 until w) {
            rinsum = 0
            ginsum = 0
            binsum = 0
            routsum = 0
            goutsum = 0
            boutsum = 0
            rsum = 0
            gsum = 0
            bsum = 0
            yp = -radius * w
            for (iIdx in -radius..radius) {
                yi = max(0, yp) + xIdx
                sir = stack[iIdx + radius]
                sir[0] = r[yi]
                sir[1] = g[yi]
                sir[2] = b[yi]
                rbs = r1 - Math.abs(iIdx)
                rsum += r[yi] * rbs
                gsum += g[yi] * rbs
                bsum += b[yi] * rbs
                if (iIdx > 0) {
                    rinsum += sir[0]
                    ginsum += sir[1]
                    binsum += sir[2]
                } else {
                    routsum += sir[0]
                    goutsum += sir[1]
                    boutsum += sir[2]
                }
                if (iIdx < hm) {
                    yp += w
                }
            }
            yi = xIdx
            stackpointer = radius
            for (yIdx in 0 until h) {
                pix[yi] = (0xff000000.toInt() and pix[yi]) or (dv[rsum] shl 16) or (dv[gsum] shl 8) or dv[bsum]

                rsum -= routsum
                gsum -= goutsum
                bsum -= boutsum

                stackstart = stackpointer - radius + div
                sir = stack[stackstart % div]

                routsum -= sir[0]
                goutsum -= sir[1]
                boutsum -= sir[2]

                if (xIdx == 0) {
                    vmin[yIdx] = min(yIdx + r1, hm) * w
                }
                p = xIdx + vmin[yIdx]

                sir[0] = r[p]
                sir[1] = g[p]
                sir[2] = b[p]

                rinsum += sir[0]
                ginsum += sir[1]
                binsum += sir[2]

                rsum += rinsum
                gsum += ginsum
                bsum += binsum

                stackpointer = (stackpointer + 1) % div
                sir = stack[stackpointer]

                routsum += sir[0]
                goutsum += sir[1]
                boutsum += sir[2]

                rinsum -= sir[0]
                ginsum -= sir[1]
                binsum -= sir[2]

                yi += w
            }
        }

        bitmap.setPixels(pix, 0, w, 0, 0, w, h)
        return bitmap
    }
}