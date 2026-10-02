package com.glazrak.fogofparis.ui

import android.net.Uri
import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.glazrak.fogofparis.R
import com.glazrak.fogofparis.domain.Place
import com.glazrak.fogofparis.tracking.collectionNameRes
import com.glazrak.fogofparis.ui.theme.Night

// Fiche d'un trésor trouvé : son nom, une petite histoire (2-3 phrases, rangée
// dans l'app, lisible hors connexion) et, s'il existe, un lien vers Wikipédia,
// ouvert dans le navigateur seulement si on le touche.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaceStorySheet(place: Place, onShowOnMap: () -> Unit, onDismiss: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Night.Surface,
        contentColor = Night.Text,
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 32.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                IconBadge(place.set.icon, place.set.color, size = 48.dp)
                Column {
                    Kicker(stringResource(collectionNameRes(place.set)))
                    Text(place.name, style = MaterialTheme.typography.headlineSmall, color = Night.Text)
                }
            }
            place.story?.let { Text(it, style = MaterialTheme.typography.bodyLarge, color = Night.TextSoft) }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onShowOnMap,
                    colors = ButtonDefaults.buttonColors(containerColor = Night.Text, contentColor = Night.Background),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f).heightIn(min = 44.dp),
                ) {
                    Icon(painterResource(R.drawable.ic_tab_map), contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(stringResource(R.string.show_on_map), modifier = Modifier.padding(start = 8.dp), style = MaterialTheme.typography.labelLarge)
                }
                place.wikiTitle?.let { title ->
                    OutlinedButton(
                        onClick = {
                            try {
                                uriHandler.openUri(wikipediaUrl(title))
                            } catch (e: IllegalArgumentException) {
                                // Aucun navigateur sur le téléphone : rien à ouvrir.
                                Log.e("PlaceStorySheet", "Could not open Wikipedia for $title", e)
                            }
                        },
                        border = BorderStroke(1.dp, Night.BorderStrong),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.heightIn(min = 44.dp),
                    ) {
                        Text(stringResource(R.string.read_on_wikipedia), color = Night.Text, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}

private fun wikipediaUrl(title: String): String =
    "https://fr.wikipedia.org/wiki/" + Uri.encode(title.replace(' ', '_'))

// Seuls les lieux qui ont une histoire ouvrent la fiche ; les autres vont
// directement sur la carte.
val Place.hasStory: Boolean get() = story != null
