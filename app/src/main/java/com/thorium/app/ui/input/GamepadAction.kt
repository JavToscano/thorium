package com.thorium.app.ui.input

enum class GamepadAction(val repeatable: Boolean = false) {
    Up(true), Down(true), Left(true), Right(true),
    Select, Back, TabLeft, TabRight, Menu, Secondary, Favorite,
}
