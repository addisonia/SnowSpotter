package com.example.snowspotterapp

data class UserPreferences(
    val theme: String = "snow",
    val musicEnabled: Boolean = true,
    val basemap: String = "standard"
) {
    constructor() : this("snow", true, "default")
}