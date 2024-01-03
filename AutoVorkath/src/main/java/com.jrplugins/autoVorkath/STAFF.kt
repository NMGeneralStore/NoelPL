package com.jrplugins.autoVorkath

import lombok.Getter
import lombok.RequiredArgsConstructor

@Getter
@RequiredArgsConstructor
enum class STAFF(private val staffName: String) {
    SLAYER_STAFF("Slayer's staff"),
    SLAYER_STAFF_E("Slayer's staff (e)");

    override fun toString(): String {
        return staffName
    }
}