plugins {
    groovy
    idea
    `maven-publish`
}

sourceSets {
    create("acceptanceTest") {
        java.srcDir(file("src/acceptanceTest/java"))
        groovy.srcDir(file("src/acceptanceTest/groovy"))
        compileClasspath += sourceSets["main"].output + sourceSets["test"].output
        runtimeClasspath += sourceSets["main"].output + sourceSets["test"].output
    }
}

idea {
    module {
        testSources.from(file("src/acceptanceTest/java"))
        testSources.from(file("src/acceptanceTest/groovy"))
    }
}

val acceptanceTestImplementation by configurations.getting {
    extendsFrom(configurations.testImplementation.get())
}

val acceptanceTestRuntimeOnly by configurations.getting {
    extendsFrom(configurations.testRuntimeOnly.get())
}

tasks.register<Test>("acceptanceTest") {
    testClassesDirs = sourceSets["acceptanceTest"].output.classesDirs
    classpath = sourceSets["acceptanceTest"].runtimeClasspath
    mustRunAfter(tasks.test)
    description = "Runs the acceptance tests."
    group = JavaBasePlugin.VERIFICATION_GROUP
}

tasks.check {
    dependsOn(tasks.named("acceptanceTest"))
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(gradleApi())
    implementation(localGroovy())
    implementation("com.android.tools.build:gradle:1.5.0")
    implementation("com.android.tools:common:25.1.0")
    implementation("org.rauschig:jarchivelib:0.6.0")
    implementation("commons-io:commons-io:2.4")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.easytesting:fest-assert-core:2.0M10")
}

group = "com.jakewharton.sdkmanager"
version = "1.5.0-SNAPSHOT"

publishing {
    publications {
        create<MavenPublication>("maven") {
            artifactId = "gradle-plugin"
            from(components["java"])

            pom {
                name.set("SDK Manager")
                description.set("Gradle plugin which downloads and manages your Android SDK.")
                url.set("https://github.com/JakeWharton/sdk-manager-plugin")
                inceptionYear.set("2014")

                scm {
                    url.set("https://github.com/JakeWharton/sdk-manager-plugin")
                    connection.set("scm:git:git://github.com/JakeWharton/sdk-manager-plugin.git")
                    developerConnection.set("scm:git:ssh://git@github.com/JakeWharton/sdk-manager-plugin.git")
                }

                licenses {
                    license {
                        name.set("The Apache Software License, Version 2.0")
                        url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                        distribution.set("repo")
                    }
                }

                developers {
                    developer {
                        id.set("jakewharton")
                        name.set("Jake Wharton")
                        email.set("jakewharton@gmail.com")
                    }
                }
            }
        }
    }
}

tasks.wrapper {
    gradleVersion = "7.6.4"
    distributionUrl = "https://services.gradle.org/distributions/gradle-${gradleVersion}-all.zip"
}
