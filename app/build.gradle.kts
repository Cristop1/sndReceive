import org.gradle.kotlin.dsl.invoke

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    kotlin("kapt")
}

android {
    namespace = "com.cuibluetooth.bleeconomy"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.cuibluetooth.bleeconomy"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "AWS_VERIFICATION_URL", "\"\"")
        /*buildConfigField("String", "MQTT_SCHEME", "\"tcp\"")
        buildConfigField("String", "MQTT_HOST", "\"10.0.2.2\"")
        buildConfigField("int", "MQTT_PORT", "1883")*/
        buildConfigField("String", "MQTT_SCHEME", "\"ssl\"")
        buildConfigField(
            "String",
            "MQTT_HOST",
            "\"1d319c2c71c04728b5afe6ed52eadc3b.s1.eu.hivemq.cloud\""
        )
        buildConfigField("int", "MQTT_PORT", "8883")
        buildConfigField("String", "MQTT_CLIENT_ID_PREFIX", "\"BleEconomy\"")

    /* TODO : Add AWS IoT configuration
        buildConfigField("String", "AWS_REGION", "\"us-east-1\"")
        buildConfigField("String", "AWS_IOT_THING_NAME", "\"BleEconomy\"")
        buildConfigField("String", "AWS_IOT_ENDPOINT", "\"a1m7p7a2m2.iot.us-east-1.amazonaws.com\"")
        buildConfigField("String", "AWS_IOT_CERTIFICATE_ID", "\"a1m7p7a2m2-certificate\"")
        buildConfigField("String", "AWS_IOT_THING_GROUP_NAME", "\"BleEconomy\"")
        buildConfigField("String", "AWS_IOT_THING_GROUP_ARN", "\"arn:aws:iot:us-east-1:a1m7p7a2m2:thinggroup/BleEconomy\"")
        buildConfigField("String", "AWS_IOT_POLICY_NAME", "\"BleEconomy\"")
        buildConfigField("String", "AWS_IOT_POLICY_ARN", "\"arn:aws:io
   */
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }

    buildFeatures{
        viewBinding = true
        buildConfig = true
    }

    testOptions{
        unitTests.isIncludeAndroidResources = true
    }

}


dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.play.services.maps)
    implementation(libs.androidx.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.activity:activity-ktx:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.3")
    implementation("androidx.lifecycle:lifecycle-service:2.8.3")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("androidx.datastore:datastore-preferences:1.1.7")

    // MQTT Implementations
    implementation("org.eclipse.paho:org.eclipse.paho.client.mqttv3:1.2.5")
    implementation(libs.org.eclipse.paho.android.service)
    // Test implementations
    testImplementation(kotlin("test"))
    testImplementation(kotlin("test-junit"))
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlin:kotlin-test")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    testImplementation("app.cash.turbine:turbine:1.1.0")
    testImplementation(libs.android.content.context)
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")

}