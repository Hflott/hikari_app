package tv.anime.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.key
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.input.key.key
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text

enum class SideDest {
    Search, Home, MyList, Addons, Settings
}

/**
 * TV-friendly side menu that stays visible as a collapsed icon rail when closed.
 *
 * Behavior:
 * - When closed: shows icons only, not focusable, does not show selection state.
 * - When open: expands to icon + text, becomes focusable, supports selection highlighting.
 *
 * Focus routing:
 * - Opening moves focus into the menu (selected item).
 * - Closing restores focus to the last content element (if provided) or the content root.
 */
@Composable
fun SideMenuScaffold(
    modifier: Modifier = Modifier,
    menuOpen: Boolean,
    onMenuOpenChange: (Boolean) -> Unit,
    selected: SideDest,
    onSelect: (SideDest) -> Unit,
    backHandlerKey: Any = Unit,
    content: @Composable (
        openMenu: () -> Unit,
        rememberLastContentFocus: (FocusRequester) -> Unit,
        contentRootFocusRequester: FocusRequester
    ) -> Unit
) {
    val menuItemRequesters = remember {
        SideDest.entries.associateWith { FocusRequester() }
    }

    // Track whether focus is currently inside the menu.
    // We avoid Modifier.focusGroup() here for compatibility with older Compose versions.
    val menuItemFocused = remember {
        SideDest.entries.associateWith { mutableStateOf(false) }
    }
    val menuHasFocus by remember {
        derivedStateOf { menuItemFocused.values.any { it.value } }
    }
    var lastMenuFocusedDest by remember { mutableStateOf<SideDest?>(null) }

    val contentRootRequester = remember { FocusRequester() }
    var lastContentFocusRequester by remember { mutableStateOf<FocusRequester?>(null) }

    val rememberLastContentFocus: (FocusRequester) -> Unit = { lastContentFocusRequester = it }

    val openMenu = { onMenuOpenChange(true) }
    val closeMenu = { onMenuOpenChange(false) }

    // When we close the menu on an activation KeyDown, we must still consume the corresponding
    // KeyUp; otherwise the KeyUp can be delivered to the newly focused content and trigger an
    // unintended click (e.g., opening Details for the last focused poster).
    var consumeNextActivationKeyUp by remember { mutableStateOf(false) }

    val menuWidth by animateDpAsState(
        targetValue = if (menuOpen) ExpandedMenuWidth else CollapsedMenuWidth,
        label = "side_menu_width"
    )

    // Focus routing.
    // IMPORTANT: do not key this to `selected`, otherwise some devices will repeatedly
    // steal focus back to the current route item (e.g., Search), preventing navigation
    // within the menu.
    LaunchedEffect(menuOpen) {
        if (menuOpen) {
            // Some TV firmwares briefly restore focus to the underlying screen right after
            // opening the menu. Instead of continuously forcing focus (which can fight
            // normal UP/DOWN navigation within the menu), we retry focus acquisition a few
            // times ONLY during the opening transition.
            val target = lastMenuFocusedDest ?: selected
            // Some TVs restore focus to the underlying content for a handful of frames
            // after the menu opens. A short burst of retries during the opening
            // transition makes the menu reliably acquire focus without "fighting" user
            // navigation after it has focus.
            repeat(10) {
                withFrameNanos { /* next frame */ }
                if (menuHasFocus) return@LaunchedEffect
                runCatching { menuItemRequesters[target]?.requestFocus() }
            }
        } else {
            runCatching { (lastContentFocusRequester ?: contentRootRequester).requestFocus() }
        }
    }

    Surface(
        modifier = modifier
            .fillMaxSize()
            .onPreviewKeyEvent { e ->
                if (!consumeNextActivationKeyUp) return@onPreviewKeyEvent false

                val isActivateKey = when (e.key) {
                    Key.DirectionCenter,
                    Key.Enter,
                    Key.NumPadEnter,
                    Key.Spacebar,
                    Key.ButtonA -> true
                    else -> false
                }

                // Consume ONLY the next activation KeyUp, then reset the latch.
                // If we don't reset, the app will keep swallowing future DPAD_CENTER/ENTER
                // key-ups, which can make other UI elements feel like they require
                // multiple presses.
                if (isActivateKey && e.type == KeyEventType.KeyUp) {
                    consumeNextActivationKeyUp = false
                    true
                } else {
                    false
                }
            }
    ) {
        // Content should be able to draw "under" the collapsed icon rail (hero imagery),
        // while still allowing the menu to expand above content when opened.
        Box(Modifier.fillMaxSize()) {
            // Main content: always full width.
            Box(Modifier.fillMaxSize()) {
                content(openMenu, rememberLastContentFocus, contentRootRequester)
            }

            // Sidebar overlays content (collapsed rail floats above hero/banner).
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxHeight()
                    .width(menuWidth)
                    .background(
                        if (menuOpen) MaterialTheme.colorScheme.surface.copy(alpha = 0.98f)
                        else Color.Transparent
                    )
                    // Keep padding constant so icons do not "jump" when expanding.
                    .padding(horizontal = 10.dp, vertical = 16.dp)
                    // Sidebar is only focusable when open.
                    .focusProperties { canFocus = menuOpen }
                    .onPreviewKeyEvent { event ->
                        if (!menuOpen) return@onPreviewKeyEvent false
                        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false

                        when (event.key) {
                            // Close on Right while in the menu.
                            Key.DirectionRight -> {
                                closeMenu()
                                true
                            }
                            else -> false
                        }
                    }
            ) {
                SideMenu(
                    onSelect = { onSelect(it) },
                    onRequestClose = closeMenu,
                    itemRequesters = menuItemRequesters,
                    menuOpen = menuOpen,
                    requestConsumeNextActivationKeyUp = { consumeNextActivationKeyUp = true },
                    onItemFocusChanged = { dest, isFocused ->
                        menuItemFocused.getValue(dest).value = isFocused
                        if (isFocused) lastMenuFocusedDest = dest
                    }
                )
            }
        }

        // IMPORTANT: BackHandler must be composed AFTER the NavHost/content so it wins over
        // Navigation's own back-stack callback on Android TV devices.
        //
        // - If the menu is closed: Back opens the menu.
        // - If the menu is open: Back closes the menu.
        key(backHandlerKey) {
            BackHandler(enabled = true) {
                if (menuOpen) closeMenu() else openMenu()
            }
        }
    }
}

private val CollapsedMenuWidth = 72.dp
private val ExpandedMenuWidth = 240.dp
private val MenuItemHeight = 56.dp
private val IconSlotWidth = 48.dp

@Composable
private fun SideMenu(
    onSelect: (SideDest) -> Unit,
    onRequestClose: () -> Unit,
    itemRequesters: Map<SideDest, FocusRequester>,
    menuOpen: Boolean,
    requestConsumeNextActivationKeyUp: () -> Unit,
    onItemFocusChanged: (SideDest, Boolean) -> Unit
) {
    val items = remember {
        listOf(
            SideDest.Search to MenuItemMeta("Search", Icons.Filled.Search),
            SideDest.Home to MenuItemMeta("Home", Icons.Filled.Home),
            SideDest.MyList to MenuItemMeta("My List", Icons.Filled.FavoriteBorder),
            SideDest.Addons to MenuItemMeta("Addons", Icons.Filled.Extension),
            SideDest.Settings to MenuItemMeta("Settings", Icons.Filled.Settings),
        )
    }

    // Keep focus trapped inside the menu while it's open.
    // Without explicit focus routing, some TV firmwares will "fall through" to the
    // underlying content when the user presses UP/DOWN at the ends of the rail.
    val order = remember { items.map { it.first } }

    Column(
        modifier = Modifier.fillMaxHeight(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items.forEachIndexed { idx, (dest, meta) ->
            val prev = order[(idx - 1 + order.size) % order.size]
            val next = order[(idx + 1) % order.size]

            SideMenuItem(
                label = meta.label,
                icon = meta.icon,
                // Never show a persistent "selected/current route" state in the menu.
                // TV UX should rely on focus state only.
                expanded = menuOpen,
                modifier = Modifier
                    .focusRequester(itemRequesters.getValue(dest))
                    .focusProperties {
                        canFocus = menuOpen
                        // Trap focus inside the side rail.
                        up = itemRequesters.getValue(prev)
                        down = itemRequesters.getValue(next)
                        left = FocusRequester.Cancel
                        right = FocusRequester.Cancel
                    },
                onActivate = { onSelect(dest) },
                onRequestClose = onRequestClose,
                requestConsumeNextActivationKeyUp = requestConsumeNextActivationKeyUp,
                onFocusedChanged = { isFocused -> onItemFocusChanged(dest, isFocused) }
            )
        }
    }
}

@Composable
private fun SideMenuItem(
    label: String,
    icon: ImageVector,
    expanded: Boolean,
    modifier: Modifier = Modifier,
    onActivate: () -> Unit,
    onRequestClose: () -> Unit,
    requestConsumeNextActivationKeyUp: () -> Unit,
    onFocusedChanged: (Boolean) -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val cs = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.medium

    val borderColor =
        when {
            focused -> cs.onSurface.copy(alpha = 0.45f)
            else -> Color.Transparent
        }

    val backgroundColor =
        when {
            focused -> cs.onSurface.copy(alpha = 0.12f)
            else -> Color.Transparent
        }

    val base = modifier
        .fillMaxWidth()
        .height(MenuItemHeight)
        .clip(shape)
        .background(backgroundColor)
        .border(2.dp, borderColor, shape)
        .animateContentSize() // keeps expansion smooth as text appears/disappears

    val interactiveModifier =
        if (expanded) {
            base
                .onFocusChanged {
                    val now = it.isFocused
                    if (focused != now) {
                        // Keep local focus state in sync so focus styling is rendered.
                        focused = now
                        onFocusedChanged(now)
                    }
                }
                .focusable()
                // Some TV remotes/devices don't consistently translate DPAD_CENTER/ENTER
                // into semantics "click" on the first press. Handle activation keys explicitly
                // and consume both down/up so clickable does not fire a second time.
                .onPreviewKeyEvent { e ->
                    val isActivateKey = when (e.key) {
                        Key.DirectionCenter,
                        Key.Enter,
                        Key.NumPadEnter,
                        Key.Spacebar,
                        Key.ButtonA -> true
                        else -> false
                    }

                    if (!isActivateKey) return@onPreviewKeyEvent false

                    // Close immediately on KeyDown so the user sees responsive navigation.
                    // We then consume the corresponding KeyUp at the scaffold level
                    // (consumeNextActivationKeyUp) to prevent fall-through clicks to content.
                    when (e.type) {
                        KeyEventType.KeyDown -> {
                            requestConsumeNextActivationKeyUp()
                            onActivate()
                            onRequestClose()
                            true
                        }
                        // Some remotes/TV firmwares only deliver the "activation" event on KeyUp.
                        // If that happens, we still want a single press to navigate.
                        KeyEventType.KeyUp -> {
                            onActivate()
                            onRequestClose()
                            true
                        }
                        else -> true
                    }
                }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    onActivate()
                    onRequestClose()
                }
        } else {
            // Not focusable/clickable while collapsed.
            base
        }

    Row(
        modifier = interactiveModifier.padding(horizontal = 0.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.width(IconSlotWidth),
            contentAlignment = Alignment.Center
        ) {
            androidx.compose.material3.Icon(
                imageVector = icon,
                contentDescription = label,
                tint = cs.onSurface
            )
        }

        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn() + expandHorizontally(expandFrom = Alignment.Start),
            exit = fadeOut() + shrinkHorizontally(shrinkTowards = Alignment.Start)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.width(12.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleMedium,
                    color = cs.onSurface,
                    maxLines = 1
                )
            }
        }
    }
}

private data class MenuItemMeta(
    val label: String,
    val icon: ImageVector
)
