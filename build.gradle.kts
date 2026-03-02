plugins {
    groovy
    `maven-publish`
    idea
}

repositories {
    mavenCentral()
    @Suppress("DEPRECATION")
    jcenter()
}

sourceSets {
    create("acceptanceTest") {
        java.srcDir("src/acceptanceTest/java")
        groovy.srcDir("src/acceptanceTest/groovy")
    }
}

idea {
    module {
        testSourceDirs.add(file("src/acceptanceTest/java"))
        testSourceDirs.add(file("src/acceptanceTest/groovy"))
    }
}

val acceptanceTest by tasks.registering(Test::class) {
    description = "Runs the acceptance tests."
    group = JavaBasePlugin.VERIFICATION_GROUP
    testClassesDirs = sourceSets["acceptanceTest"].output.classesDirs
    classpath = sourceSets["acceptanceTest"].runtimeClasspath
    mustRunAfter(tasks.test)
}

tasks.named("check") {
    dependsOn(acceptanceTest)
}

dependencies {
    implementation(gradleApi())
    implementation(localGroovy())
    implementation("com.android.tools.build:gradle:1.5.0")
    implementation("org.rauschig:jarchivelib:0.6.0")
    implementation("commons-io:commons-io:2.4")

    testImplementation("org.easytesting:fest-assert-core:2.0M10")

    "acceptanceTestImplementation"(sourceSets.main.get().output)
    "acceptanceTestImplementation"(sourceSets.test.get().output)
}

configurations {
    named("acceptanceTestImplementation") {
        extendsFrom(configurations.testImplementation.get())
    }
    named("acceptanceTestRuntimeOnly") {
        extendsFrom(configurations.testRuntimeOnly.get())
    }
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
    distributionType = Wrapper.DistributionType.ALL
}
