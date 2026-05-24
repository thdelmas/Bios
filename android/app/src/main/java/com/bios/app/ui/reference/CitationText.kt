package com.bios.app.ui.reference

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import com.bios.app.alerts.Citation

/**
 * Renders a scientific [Citation] as a tappable annotated string
 * (#305). The whole citation is one link target — tapping anywhere
 * on the row opens the URL in the system browser via the platform
 * URI handler.
 */
@Composable
fun CitationText(
    citation: Citation,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
) {
    val linkColor = MaterialTheme.colorScheme.primary
    val annotated = buildAnnotatedString {
        withLink(
            LinkAnnotation.Url(
                url = citation.url,
                styles = TextLinkStyles(
                    style = SpanStyle(
                        color = linkColor,
                        textDecoration = TextDecoration.Underline,
                    ),
                ),
            )
        ) {
            append(citation.text)
        }
    }
    Text(text = annotated, modifier = modifier, style = style)
}
