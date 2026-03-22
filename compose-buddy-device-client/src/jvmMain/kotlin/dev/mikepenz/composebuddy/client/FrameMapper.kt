package dev.mikepenz.composebuddy.client

import dev.mikepenz.composebuddy.client.model.BuddyFrame
import dev.mikepenz.composebuddy.client.model.BuddySemanticNode
import dev.mikepenz.composebuddy.core.model.HierarchyNode
import dev.mikepenz.composebuddy.core.model.RenderConfiguration
import dev.mikepenz.composebuddy.core.model.RenderResult
import dev.mikepenz.composebuddy.core.model.boundsOf
import dev.mikepenz.composebuddy.core.model.pxToDp
import dev.mikepenz.composebuddy.core.model.sizeOf
import java.io.File
import java.util.Base64
import javax.imageio.ImageIO

object FrameMapper {

    private val cacheDir: File by lazy {
        File(System.getProperty("user.home"), ".compose-buddy/cache/device-frames").also { it.mkdirs() }
    }

    fun toRenderResult(frame: BuddyFrame.RenderFrame, densityDpi: Int = frame.densityDpi): RenderResult {
        val hierarchy = mapSemanticNode(frame.semantics, densityDpi)
        val timingMs = frame.frameTiming.totalMs.toLong()

        // Decode base64 image to a temp file so the inspector's PreviewPane can load it
        val imageFile = writeImageToFile(frame.image, frame.frameId)
        val (imageWidth, imageHeight) = readImageDimensions(imageFile)

        return RenderResult(
            previewName = frame.preview,
            configuration = RenderConfiguration(densityDpi = densityDpi),
            imagePath = imageFile?.absolutePath ?: "",
            imageBase64 = frame.image,
            imageWidth = imageWidth,
            imageHeight = imageHeight,
            renderDurationMs = timingMs,
            hierarchy = hierarchy,
            rendererUsed = "device",
        )
    }

    private fun writeImageToFile(base64Image: String, frameId: Int): File? {
        if (base64Image.isBlank()) return null
        return try {
            val bytes = Base64.getDecoder().decode(base64Image)
            val file = File(cacheDir, "frame-$frameId.png")
            file.writeBytes(bytes)
            file
        } catch (_: Exception) {
            null
        }
    }

    private fun readImageDimensions(file: File?): Pair<Int, Int> {
        if (file == null || !file.exists()) return 0 to 0
        return try {
            val image = ImageIO.read(file)
            (image?.width ?: 0) to (image?.height ?: 0)
        } catch (_: Exception) {
            0 to 0
        }
    }

    fun mapSemanticNode(node: BuddySemanticNode, densityDpi: Int = 420): HierarchyNode {
        val leftDp = pxToDp(node.bounds.left, densityDpi)
        val topDp = pxToDp(node.bounds.top, densityDpi)
        val rightDp = pxToDp(node.bounds.right, densityDpi)
        val bottomDp = pxToDp(node.bounds.bottom, densityDpi)

        val semanticsMap = buildMap<String, String> {
            node.role?.let { put("role", it) }
            node.contentDescription?.let { put("contentDescription", it) }
            node.testTag?.let { put("testTag", it) }
            node.colors.background?.let { put("backgroundColor", it) }
            node.colors.foreground?.let { put("foregroundColor", it) }
            putAll(node.mergedSemantics)
        }.ifEmpty { null }

        val boundsInParent = node.boundsInParent?.let {
            boundsOf(pxToDp(it.left, densityDpi), pxToDp(it.top, densityDpi), pxToDp(it.right, densityDpi), pxToDp(it.bottom, densityDpi))
        }
        val offsetFromParent = node.offsetFromParent?.let {
            boundsOf(pxToDp(it.left, densityDpi), pxToDp(it.top, densityDpi), pxToDp(it.right, densityDpi), pxToDp(it.bottom, densityDpi))
        }

        return HierarchyNode(
            id = node.id,
            name = node.name,
            bounds = boundsOf(leftDp, topDp, rightDp, bottomDp),
            boundsInParent = boundsInParent,
            size = sizeOf(rightDp - leftDp, bottomDp - topDp),
            offsetFromParent = offsetFromParent,
            semantics = semanticsMap,
            sourceFile = node.sourceFile,
            sourceLine = node.sourceLine,
            children = node.children.map { child -> mapSemanticNode(child, densityDpi) },
        )
    }
}
