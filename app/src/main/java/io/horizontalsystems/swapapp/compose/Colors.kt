package io.horizontalsystems.swapapp.compose

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

@Stable
class Colors(
    jacob: Color,
    remus: Color,
    lucian: Color,
    tyler: Color,
    leah: Color,
    lawrence: Color,
    andy: Color,
    blade: Color,
) {

    //base colors
    val dark = Dark
    val white = Color.White
    val issykBlue = Color(0xFF3372FF)
    val grey = Grey
    val yellowD = YellowD
    val red20 = Red20

    //themed colors
    var jacob by mutableStateOf(jacob)
        private set
    var remus by mutableStateOf(remus)
        private set
    var lucian by mutableStateOf(lucian)
        private set
    var tyler by mutableStateOf(tyler)
        private set
    var leah by mutableStateOf(leah)
        private set
    var lawrence by mutableStateOf(lawrence)
        private set
    var andy by mutableStateOf(andy)
        private set
    var blade by mutableStateOf(blade)
        private set

    fun update(other: Colors) {
        jacob = other.jacob
        remus = other.remus
        lucian = other.lucian
        tyler = other.tyler
        leah = other.leah
        lawrence = other.lawrence
        andy = other.andy
        blade = other.blade
    }

    fun copy(): Colors = Colors(
        jacob = jacob,
        remus = remus,
        lucian = lucian,
        tyler = tyler,
        leah = leah,
        lawrence = lawrence,
        andy = andy,
        blade = blade,
    )
}
