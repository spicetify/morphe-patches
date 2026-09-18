extension {
    name = "extensions/spotify.mpe"
}

android {
    namespace = "app.spicetify.extension.spotify"
    testOptions.unitTests.isIncludeAndroidResources = true
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.17")
}
