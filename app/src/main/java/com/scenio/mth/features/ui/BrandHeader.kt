package com.scenio.mth.features.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.scenio.mth.R

@Composable
fun BrandHeader() {
    Image(painterResource(R.drawable.sf_brand_banner),stringResource(R.string.sf_brand_artwork),
        modifier=Modifier.fillMaxWidth().heightIn(max=230.dp),contentScale=ContentScale.Fit)
}
