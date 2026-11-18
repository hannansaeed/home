package com.example.chatapp.ui.chat

import android.net.Uri
import android.widget.MediaController
import android.widget.VideoView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import com.example.chatapp.ui.components.*
import com.example.chatapp.ui.moth.*
import com.example.chatapp.ui.theme.*
import java.io.File

@Composable
internal fun ChatAttachmentErrorBanner(
    attachmentError: String?,
    onDismiss: () -> Unit,
) {
    if (attachmentError == null) return
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(TheirBubbleFill)
            .border(1.dp, WaxSealRed)
            .padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = attachmentError,
                fontFamily = PixelifySans,
                fontSize = 11.sp,
                color = WaxSealRed,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "✕",
                fontFamily = PixelifySans,
                fontSize = 12.sp,
                color = MutedText,
                modifier = Modifier.clickable(onClick = onDismiss).padding(start = 8.dp),
            )
        }
    }
}

@Composable
internal fun ChatMentionStrip(
    mentionCandidates: List<String>,
    onPickMember: (String) -> Unit,
) {
    if (mentionCandidates.isEmpty()) return
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .background(MothHeaderBg)
            .border(1.dp, MothDivider)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(mentionCandidates) { member ->
            SteppedBox(
                modifier = Modifier.clickable { onPickMember(member) },
                backgroundColor = MothBgDark,
                borderColor = LanternAmber,
                cutSize = 2.dp,
                borderWidth = 1.dp,
            ) {
                Text(
                    text = "@$member",
                    fontFamily = PixelifySans,
                    fontSize = 12.sp,
                    color = LanternAmber,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }
    }
}

@Composable
internal fun ChatPendingAttachmentBanner(
    att: PendingAttachment,
    pendingViewOnce: Boolean,
    onToggleViewOnce: () -> Unit,
    onClearAttachment: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MothHeaderBg)
            .border(1.dp, MothDivider)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            when (att.type) {
                AttachmentType.PHOTO -> {
                    AsyncImage(
                        model = File(att.localPath),
                        contentDescription = att.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(SteppedCornerShape(3.dp))
                            .border(1.dp, LanternAmber, SteppedCornerShape(3.dp)),
                    )
                }
                AttachmentType.VIDEO -> {
                    SteppedBox(
                        modifier = Modifier.size(44.dp),
                        backgroundColor = MothBgDark,
                        borderColor = LanternAmber,
                        cutSize = 3.dp,
                        borderWidth = 1.dp,
                    ) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            PixelVideoIcon(tint = LanternAmber, modifier = Modifier.size(20.dp))
                        }
                    }
                }
                else -> {
                    SteppedBox(
                        modifier = Modifier.size(44.dp),
                        backgroundColor = MothBgDark,
                        borderColor = LanternAmber,
                        cutSize = 3.dp,
                        borderWidth = 1.dp,
                    ) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                text = if (att.type == AttachmentType.VOICE) "MIC" else att.name.substringAfterLast('.', "FILE").take(4).uppercase(),
                                fontFamily = PixelifySans,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = LanternAmber,
                            )
                        }
                    }
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = att.name,
                    fontFamily = PixelifySans,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = TheirBubbleText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = if (pendingViewOnce) "${formatFileSize(att.sizeBytes)} • view once" else formatFileSize(att.sizeBytes),
                    fontFamily = PixelifySans,
                    fontSize = 11.sp,
                    color = if (pendingViewOnce) LanternAmber else MutedText,
                )
            }

            SteppedBox(
                modifier = Modifier
                    .size(34.dp)
                    .clickable(onClick = onToggleViewOnce)
                    .testTag("pending_view_once_toggle"),
                backgroundColor = if (pendingViewOnce) MyBubbleFill else MothBgDark,
                borderColor = if (pendingViewOnce) LanternAmber else TheirBubbleBorder,
                cutSize = 3.dp,
                borderWidth = 1.dp,
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    PixelViewOnceIcon(
                        tint = if (pendingViewOnce) MyBubbleText else MutedText,
                        active = pendingViewOnce,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }

            SteppedBox(
                modifier = Modifier
                    .clickable(onClick = onClearAttachment)
                    .testTag("clear_attachment_button"),
                backgroundColor = MothBgDark,
                borderColor = WaxSealRed,
                cutSize = 3.dp,
                borderWidth = 1.dp,
            ) {
                Text(
                    text = "✕",
                    fontFamily = PixelifySans,
                    fontSize = 12.sp,
                    color = WaxSealRed,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }
    }
}

@Composable
internal fun ChatStickersTray(
    visible: Boolean,
    stickers: List<MothSticker>,
    onOpenDraw: () -> Unit,
    onOpenPhotoPicker: () -> Unit,
    onSendSticker: (MothSticker) -> Unit,
    onHoldSticker: (MothSticker) -> Unit,
) {
    AnimatedVisibility(visible = visible) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MothHeaderBg)
                .border(1.dp, MothDivider)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "my stickers",
                    fontFamily = PixelifySans,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = LanternAmber,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SteppedButton(
                        text = "+ draw",
                        onClick = onOpenDraw,
                        backgroundColor = TheirBubbleFill,
                        borderColor = LanternAmber,
                        textColor = LanternAmber,
                        cutSize = 3.dp,
                        borderWidth = 1.dp,
                        modifier = Modifier.testTag("draw_sticker_button"),
                    )
                    SteppedButton(
                        text = "+ photo",
                        onClick = onOpenPhotoPicker,
                        backgroundColor = TheirBubbleFill,
                        borderColor = TheirBubbleBorder,
                        textColor = TheirBubbleText,
                        cutSize = 3.dp,
                        borderWidth = 1.dp,
                        modifier = Modifier.testTag("add_photo_sticker_button"),
                    )
                }
            }

            if (stickers.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "no stickers yet — tap + draw or + photo to create one",
                        fontFamily = PixelifySans,
                        fontSize = 11.sp,
                        color = MutedText,
                    )
                }
            } else {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(stickers, key = { it.id }) { sticker ->
                        StickerTrayItem(
                            sticker = sticker,
                            onSend = { onSendSticker(sticker) },
                            onHold = { onHoldSticker(sticker) },
                        )
                    }
                }
                Text(
                    text = "tap to send • hold to unfavourite / delete",
                    fontFamily = PixelifySans,
                    fontSize = 10.sp,
                    color = PlaceholderText,
                )
            }
        }
    }
}

@Composable
internal fun ChatAttachMenuRow(
    visible: Boolean,
    onCameraClick: () -> Unit,
    onMediaClick: () -> Unit,
    onFileClick: () -> Unit,
) {
    AnimatedVisibility(visible = visible) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MothHeaderBg)
                .border(1.dp, MothDivider)
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SteppedButton(
                text = "cam",
                onClick = onCameraClick,
                modifier = Modifier.weight(1f).testTag("camera_capture_button"),
                backgroundColor = TheirBubbleFill,
                borderColor = TheirBubbleBorder,
                textColor = TheirBubbleText,
                cutSize = 3.dp,
                leadingIcon = { PixelCameraIcon(tint = TheirBubbleText) },
            )
            SteppedButton(
                text = "media",
                onClick = onMediaClick,
                modifier = Modifier.weight(1f).testTag("pick_photo_button"),
                backgroundColor = TheirBubbleFill,
                borderColor = TheirBubbleBorder,
                textColor = TheirBubbleText,
                cutSize = 3.dp,
                leadingIcon = { PixelPhotoIcon(tint = TheirBubbleText) },
            )
            SteppedButton(
                text = "file",
                onClick = onFileClick,
                modifier = Modifier.weight(1f).testTag("pick_file_button"),
                backgroundColor = TheirBubbleFill,
                borderColor = TheirBubbleBorder,
                textColor = TheirBubbleText,
                cutSize = 3.dp,
                leadingIcon = { PixelFileIcon(tint = TheirBubbleText) },
            )
        }
    }
}

@Composable
internal fun ChatVoiceFilterPickerBar(
    visible: Boolean,
    activeFilter: VoiceFilter,
    onSelectFilter: (VoiceFilter) -> Unit,
) {
    AnimatedVisibility(visible = visible) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MothHeaderBg)
                .border(1.dp, LanternAmber)
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 10.dp, vertical = 8.dp)
                .testTag("voice_filter_picker_bar"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "voice filter:",
                fontFamily = PixelifySans,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                color = Color(0xFFFFD152),
            )
            VoiceFilter.entries.forEach { filter ->
                val isChosen = activeFilter == filter
                SteppedButton(
                    text = filter.label,
                    onClick = { onSelectFilter(filter) },
                    backgroundColor = if (isChosen) Color(0xFFFFD152) else TheirBubbleFill,
                    borderColor = if (isChosen) Color(0xFFFFD152) else TheirBubbleBorder,
                    textColor = if (isChosen) MothInkDark else TheirBubbleText,
                    cutSize = 2.dp,
                    borderWidth = 1.dp,
                    modifier = Modifier.testTag("voice_filter_${filter.id}"),
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ChatInputBar(
    voiceRecorder: VoiceNoteRecorder,
    draft: String,
    inputFocusRequester: FocusRequester,
    hasPendingAttachment: Boolean,
    pendingViewOnce: Boolean,
    showAttachMenu: Boolean,
    showStickerTray: Boolean,
    showVoiceFilterMenu: Boolean,
    onDraftChange: (String) -> Unit,
    onToggleAttachMenu: () -> Unit,
    onToggleStickerTray: () -> Unit,
    onVoiceNoteClick: () -> Unit,
    onVoiceNoteLongClick: () -> Unit,
    onSendTextClick: () -> Unit,
    onSendVoiceNoteClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MothHeaderBg)
            .windowInsetsPadding(
                WindowInsets.navigationBars.union(WindowInsets.ime).only(WindowInsetsSides.Bottom)
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        if (voiceRecorder.isRecording) {
            val isRecPaused = voiceRecorder.isPaused
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .background(MothBgDark, SteppedCornerShape(4.dp))
                    .border(2.dp, if (isRecPaused) LanternAmber else WaxSealRed, SteppedCornerShape(4.dp))
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(modifier = Modifier.size(8.dp).background(if (isRecPaused) LanternAmber else WaxSealRed))
                    val sec = voiceRecorder.elapsedSec
                    val activeFilter = voiceRecorder.selectedFilter
                    Text(
                        text = "${if (isRecPaused) "paused" else "recording"} ${sec / 60}:${"%02d".format(sec % 60)}${if (activeFilter != VoiceFilter.NONE) " • ${activeFilter.label}" else ""}",
                        fontFamily = PixelifySans,
                        fontSize = 13.sp,
                        color = if (activeFilter != VoiceFilter.NONE) Color(0xFFFFD152) else LanternAmber,
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SteppedBox(
                        modifier = Modifier
                            .size(32.dp)
                            .clickable {
                                if (isRecPaused) voiceRecorder.resumeRecording() else voiceRecorder.pauseRecording()
                            }
                            .testTag("pause_resume_voice_button"),
                        backgroundColor = TheirBubbleFill,
                        borderColor = LanternAmber,
                        cutSize = 2.dp,
                        borderWidth = 1.dp,
                    ) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            if (isRecPaused) {
                                PixelPlayIcon(tint = LanternAmber, modifier = Modifier.size(14.dp))
                            } else {
                                PixelPauseIcon(tint = LanternAmber, modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                    SteppedButton(
                        text = "✕",
                        onClick = { voiceRecorder.cancelRecording() },
                        backgroundColor = TheirBubbleFill,
                        borderColor = WaxSealRed,
                        textColor = WaxSealRed,
                        cutSize = 2.dp,
                    )
                    SteppedButton(
                        text = "✓ send",
                        onClick = onSendVoiceNoteClick,
                        backgroundColor = MyBubbleFill,
                        borderColor = MyBubbleBorder,
                        textColor = MyBubbleText,
                        cutSize = 2.dp,
                        modifier = Modifier.testTag("send_voice_note_button"),
                    )
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SteppedBox(
                    modifier = Modifier
                        .size(44.dp)
                        .clickable(onClick = onToggleAttachMenu)
                        .testTag("attach_button"),
                    backgroundColor = MothBgDark,
                    borderColor = if (showAttachMenu || hasPendingAttachment || pendingViewOnce) LanternAmber else TheirBubbleBorder,
                    cutSize = 4.dp,
                    borderWidth = 2.dp,
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = if (showAttachMenu) "✕" else "+",
                            fontFamily = PixelifySans,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (showAttachMenu || hasPendingAttachment || pendingViewOnce) LanternAmber else TheirBubbleText,
                        )
                    }
                }

                SteppedBox(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 44.dp, max = 132.dp),
                    backgroundColor = MothInputBg,
                    borderColor = TheirBubbleBorder,
                    cutSize = 4.dp,
                    borderWidth = 2.dp,
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = if (draft.contains('\n')) Alignment.Bottom else Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 28.dp, max = 112.dp)
                                .verticalScroll(rememberScrollState()),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            BasicTextField(
                                value = draft,
                                onValueChange = onDraftChange,
                                textStyle = TextStyle(
                                    fontFamily = PixelifySans,
                                    fontSize = 14.sp,
                                    color = TheirBubbleText,
                                    lineHeight = 19.sp,
                                ),
                                cursorBrush = SolidColor(LanternAmber),
                                singleLine = false,
                                maxLines = 5,
                                keyboardOptions = KeyboardOptions(
                                    capitalization = KeyboardCapitalization.Sentences,
                                    imeAction = ImeAction.Default,
                                ),
                                decorationBox = { innerTextField ->
                                    Box(
                                        modifier = Modifier.fillMaxWidth(),
                                        contentAlignment = Alignment.CenterStart,
                                    ) {
                                        if (draft.isEmpty()) {
                                            Text(
                                                text = if (hasPendingAttachment) "add caption..." else "say something",
                                                fontFamily = PixelifySans,
                                                fontSize = 14.sp,
                                                lineHeight = 19.sp,
                                                color = PlaceholderText,
                                            )
                                        }
                                        innerTextField()
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .focusRequester(inputFocusRequester)
                                    .testTag("message_input_field"),
                            )
                        }

                        Box(
                            modifier = Modifier
                                .size(26.dp)
                                .clickable(onClick = onToggleStickerTray)
                                .testTag("sticker_picker_button"),
                            contentAlignment = Alignment.Center,
                        ) {
                            PixelStickerIcon(tint = if (showStickerTray) LanternAmber else MutedText)
                        }

                        val hasActiveVoiceFilter = voiceRecorder.selectedFilter != VoiceFilter.NONE
                        SteppedBox(
                            modifier = Modifier
                                .size(28.dp)
                                .combinedClickable(
                                    onClick = onVoiceNoteClick,
                                    onLongClick = onVoiceNoteLongClick,
                                )
                                .testTag("voice_note_button"),
                            backgroundColor = if (hasActiveVoiceFilter) Color(0xFFFFD152) else Color.Transparent,
                            borderColor = if (hasActiveVoiceFilter || showVoiceFilterMenu) Color(0xFFFFD152) else Color.Transparent,
                            cutSize = 2.dp,
                            borderWidth = if (hasActiveVoiceFilter || showVoiceFilterMenu) 1.dp else 0.dp,
                        ) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                PixelMicIcon(
                                    tint = if (hasActiveVoiceFilter) MothInkDark else if (showVoiceFilterMenu) Color(0xFFFFD152) else MutedText,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clickable(onClick = onSendTextClick)
                        .testTag("send_message_button"),
                    contentAlignment = Alignment.Center,
                ) {
                    PixelWaxSeal(hasArrow = true)
                }
            }
        }
    }
}

@Composable
internal fun ChatFullScreenPhotoOverlay(
    photoPath: String,
    onSave: () -> Unit,
    onClose: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MothBgDark.copy(alpha = 0.96f))
            .statusBarsPadding()
            .navigationBarsPadding()
            .clickable(onClick = onClose)
            .padding(16.dp),
    ) {
        AsyncImage(
            model = File(photoPath),
            contentDescription = "Full screen photo",
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize().align(Alignment.Center),
        )
        Row(
            modifier = Modifier.align(Alignment.TopEnd),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SteppedButton(
                text = "save",
                onClick = onSave,
                modifier = Modifier.testTag("save_photo_button"),
                backgroundColor = MothHeaderBg,
                borderColor = SecureGreen,
                textColor = SecureGreen,
                cutSize = 3.dp,
            )
            SteppedButton(
                text = "close",
                onClick = onClose,
                modifier = Modifier.testTag("close_photo_viewer_button"),
                backgroundColor = MothHeaderBg,
                borderColor = LanternAmber,
                textColor = LanternAmber,
                cutSize = 3.dp,
            )
        }
    }
}

@Composable
internal fun ChatFullScreenVideoOverlay(
    videoPath: String,
    onSave: () -> Unit,
    onClose: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MothBgDark.copy(alpha = 0.96f))
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(16.dp),
    ) {
        AndroidView(
            factory = { ctx ->
                VideoView(ctx).apply {
                    val controller = MediaController(ctx)
                    controller.setAnchorView(this)
                    setMediaController(controller)
                    setVideoURI(Uri.fromFile(File(videoPath)))
                    setOnPreparedListener { start() }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 240.dp, max = 520.dp)
                .align(Alignment.Center),
        )
        Row(
            modifier = Modifier.align(Alignment.TopEnd),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SteppedButton(
                text = "save",
                onClick = onSave,
                modifier = Modifier.testTag("save_video_button"),
                backgroundColor = MothHeaderBg,
                borderColor = SecureGreen,
                textColor = SecureGreen,
                cutSize = 3.dp,
            )
            SteppedButton(
                text = "close",
                onClick = onClose,
                modifier = Modifier.testTag("close_video_viewer_button"),
                backgroundColor = MothHeaderBg,
                borderColor = LanternAmber,
                textColor = LanternAmber,
                cutSize = 3.dp,
            )
        }
    }
}

@Composable
internal fun ChatUnpinConfirmDialog(
    targetMsg: MothMessage,
    onCancel: () -> Unit,
    onConfirmUnpin: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MothScrimStrong)
            .clickable(onClick = onCancel)
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        SteppedBox(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = false) { }
                .testTag("unpin_confirm_dialog"),
            backgroundColor = MothHeaderBg,
            borderColor = LanternAmber,
            cutSize = 4.dp,
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "unpin message?",
                    fontFamily = PixelifySans,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = LanternAmber,
                )
                val snippet = targetMsg.text.ifBlank {
                    targetMsg.attachmentName ?: "pinned message"
                }
                Text(
                    text = "\"${snippet.take(80)}\"",
                    fontFamily = PixelifySans,
                    fontSize = 12.sp,
                    color = TheirBubbleText,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SteppedButton(
                        text = "cancel",
                        onClick = onCancel,
                        backgroundColor = MothBgDark,
                        borderColor = TheirBubbleBorder,
                        textColor = MutedText,
                        modifier = Modifier.weight(1f).testTag("cancel_unpin_button"),
                        cutSize = 3.dp,
                    )
                    SteppedButton(
                        text = "unpin",
                        onClick = onConfirmUnpin,
                        backgroundColor = MyBubbleFill,
                        borderColor = MyBubbleBorder,
                        textColor = MyBubbleText,
                        modifier = Modifier.weight(1f).testTag("confirm_unpin_button"),
                        cutSize = 3.dp,
                    )
                }
            }
        }
    }
}

@Composable
internal fun ChatStickerHoldOptionsDialog(
    sticker: MothSticker,
    onUnfavourite: () -> Unit,
    onDelete: () -> Unit,
    onCancel: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MothScrimStrong)
            .clickable(onClick = onCancel)
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        SteppedBox(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = false) { }
                .testTag("sticker_hold_dialog"),
            backgroundColor = MothHeaderBg,
            borderColor = LanternAmber,
            cutSize = 4.dp,
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    SteppedBox(
                        modifier = Modifier.size(52.dp),
                        backgroundColor = MothBgDark,
                        borderColor = TheirBubbleBorder,
                        cutSize = 3.dp,
                        borderWidth = 1.dp,
                    ) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            AsyncImage(
                                model = File(sticker.imageLocalPath),
                                contentDescription = sticker.label,
                                contentScale = ContentScale.Fit,
                                filterQuality = FilterQuality.None,
                                modifier = Modifier.size(40.dp),
                            )
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "sticker options",
                            fontFamily = PixelifySans,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = LanternAmber,
                        )
                        Text(
                            text = sticker.label,
                            fontFamily = PixelifySans,
                            fontSize = 12.sp,
                            color = MutedText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                SteppedButton(
                    text = "unfavourite",
                    onClick = onUnfavourite,
                    backgroundColor = MothBgDark,
                    borderColor = LanternAmber,
                    textColor = LanternAmber,
                    modifier = Modifier.fillMaxWidth().testTag("sticker_unfavourite_button"),
                    cutSize = 3.dp,
                )
                SteppedButton(
                    text = "delete",
                    onClick = onDelete,
                    backgroundColor = MothBgDark,
                    borderColor = WaxSealRed,
                    textColor = WaxSealRed,
                    modifier = Modifier.fillMaxWidth().testTag("sticker_delete_button"),
                    cutSize = 3.dp,
                )
                SteppedButton(
                    text = "cancel",
                    onClick = onCancel,
                    backgroundColor = TheirBubbleFill,
                    borderColor = TheirBubbleBorder,
                    textColor = MutedText,
                    modifier = Modifier.fillMaxWidth().testTag("sticker_hold_cancel_button"),
                    cutSize = 3.dp,
                )
            }
        }
    }
}
