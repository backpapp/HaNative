package com.backpapp.hanative.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.backpapp.hanative.ui.theme.HaNativeTheme

// Previews drive the picker body via `EntityPickerUiState` directly — UI-layer
// types only, no domain models, no Koin graph. The full state machine is
// reachable statically; runtime LaunchedEffect/StateFlow plumbing lives in the
// ViewModel and is exercised by `EntityPickerViewModelTest`.

private fun row(id: String, domain: String, name: String, stateLabel: String): EntityRowUi =
    EntityRowUi(entityId = id, domain = domain, name = name, stateLabel = stateLabel)

private val multiDomainRows: List<EntityRowUi> = listOf(
    row("light.living_room", "light", "Living Room", "On"),
    row("switch.kitchen_outlet", "switch", "Kitchen Outlet", "Off"),
    row("climate.lounge", "climate", "Lounge", "Heat"),
    row("media_player.living_room", "media_player", "Living Room Speaker", "Playing"),
    row("sensor.living_room_temp", "sensor", "Living Room Temp", "21.4"),
    row("binary_sensor.front_door", "binary_sensor", "Front Door", "On"),
    row("script.goodnight", "script", "Goodnight", "Off"),
    row("light.bedroom", "light", "Bedroom", "Off"),
)

private val onlyLightsRows: List<EntityRowUi> = listOf(
    row("light.living_room", "light", "Living Room", "On"),
    row("light.bedroom", "light", "Bedroom", "Off"),
    row("light.kitchen", "light", "Kitchen", "On"),
)

private val staleRows: List<EntityRowUi> = listOf(
    row("light.recent", "light", "Recent Light", "On"),
    row("light.an_hour_old", "light", "Hour-Old Light", "On"),
    row("light.day_old", "light", "Day-Old Light", "On"),
)

private val longNameRows: List<EntityRowUi> = listOf(
    row(
        "light.very_long",
        "light",
        "Extremely Long Friendly Name That Definitely Exceeds The Available Row Width By A Wide Margin",
        "On",
    ),
    row("light.short", "light", "Short", "On"),
)

@Composable
private fun PreviewWrap(content: @Composable () -> Unit) {
    HaNativeTheme {
        Column { content() }
    }
}

@Preview(name = "Picker_Loading")
@Composable
private fun PickerLoading() = PreviewWrap {
    EntityPickerBody(
        state = EntityPickerUiState.Loading,
        selectedDomain = null,
        onDomainSelect = {},
        onRowTap = {},
    )
}

@Preview(name = "Picker_Loaded_AllDomains")
@Composable
private fun PickerLoadedAll() = PreviewWrap {
    EntityPickerBody(
        state = EntityPickerUiState.Loaded(multiDomainRows),
        selectedDomain = null,
        onDomainSelect = {},
        onRowTap = {},
    )
}

@Preview(name = "Picker_Loaded_FilteredLight")
@Composable
private fun PickerLoadedLight() = PreviewWrap {
    EntityPickerBody(
        state = EntityPickerUiState.Loaded(onlyLightsRows),
        selectedDomain = "light",
        onDomainSelect = {},
        onRowTap = {},
    )
}

@Preview(name = "Picker_EmptyDomain")
@Composable
private fun PickerEmptyDomain() = PreviewWrap {
    EntityPickerBody(
        state = EntityPickerUiState.EmptyDomain("climate"),
        selectedDomain = "climate",
        onDomainSelect = {},
        onRowTap = {},
    )
}

@Preview(name = "Picker_EmptyAll")
@Composable
private fun PickerEmptyAll() = PreviewWrap {
    EntityPickerBody(
        state = EntityPickerUiState.EmptyAll,
        selectedDomain = null,
        onDomainSelect = {},
        onRowTap = {},
    )
}

@Preview(name = "Picker_LongFriendlyName")
@Composable
private fun PickerLongName() = PreviewWrap {
    EntityPickerBody(
        state = EntityPickerUiState.Loaded(longNameRows),
        selectedDomain = null,
        onDomainSelect = {},
        onRowTap = {},
    )
}

@Preview(name = "Picker_StaleEntities")
@Composable
private fun PickerStale() = PreviewWrap {
    EntityPickerBody(
        state = EntityPickerUiState.Loaded(staleRows),
        selectedDomain = null,
        onDomainSelect = {},
        onRowTap = {},
    )
}
