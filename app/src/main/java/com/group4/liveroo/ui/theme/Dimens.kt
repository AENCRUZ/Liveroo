package com.group4.liveroo.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/** Spacing and sizes on a 4 dp grid (handoff spec, "Spacing, sizing, radii"). */
object LiverooDimens {
    val space4 = 4.dp
    val space8 = 8.dp
    val space12 = 12.dp
    val space16 = 16.dp   // screen side margin, card padding
    val space24 = 24.dp   // onboarding / centered-state margin, bottom button inset

    val rowMin = 56.dp
    val rowMinWithLeading = 64.dp
    val rowMinTwoLine = 72.dp
    val touchMin = 48.dp

    val buttonHeight = 52.dp
    val buttonHeightSmall = 48.dp
    val fieldHeight = 56.dp
    val fieldHeightMultiline = 104.dp
    val segmentHeight = 60.dp
    val chipHeight = 40.dp

    val iconTile = 40.dp
    val iconTileHero = 56.dp
    val iconTileCentered = 96.dp
    val icon = 24.dp
    val iconSmall = 20.dp

    val navBarHeight = 64.dp
    val navBarInset = 16.dp
    val fabBottomOffset = 96.dp
    val listBottomPaddingWithNav = 120.dp
    val rowDividerInset = 16.dp
    val rowDividerInsetLeading = 68.dp
}

/** Corner radii. */
object LiverooRadius {
    val small = RoundedCornerShape(12.dp)   // icon tiles, fields, banners, segmented control
    val medium = RoundedCornerShape(20.dp)  // grouped cards, group cards
    val large = RoundedCornerShape(24.dp)   // hero cards, dialogs
    val sheet = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    val full = CircleShape                  // buttons, chips, tags, nav pill, avatars, FAB
}
