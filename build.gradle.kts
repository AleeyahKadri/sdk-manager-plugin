import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.wrapper.Wrapper
import org.gradle.plugins.ide.idea.model.IdeaModel

buildscript {
  repositories {
    mavenCentral()
  }

  dependencies {
    classpath("com.bmuschko:gradle-nexus-plugin:2.3.1")
  }
}

apply(plugin = "groovy")
apply(plugin = "maven")
apply(plugin = "com.bmuschko.nexus")
apply(plugin = "idea")

sourceSets {
  create("acceptanceTest") {
    java.srcDir(file("src/acceptanceTest/java"))
    withConvention(org.gradle.api.tasks.GroovySourceSet::class) {
      groovy.srcDir(file("src/acceptanceTest/groovy"))
    }
  }
}

configure<IdeaModel> {
  module {
    testSourceDirs = testSourceDirs + file("src/acceptanceTest/java")
    testSourceDirs = testSourceDirs + file("src/acceptanceTest/groovy")
  }
}

val acceptanceTest by tasks.creating(Test::class) {
  testClassesDir = sourceSets["acceptanceTest"].output.classesDir
  classpath = sourceSets["acceptanceTest"].runtimeClasspath
  mustRunAfter(tasks.findByName("test"))
  description = "Runs the acceptance tests."
  group = JavaBasePlugin.VERIFICATION_GROUP
}

tasks.named("check") {
  dependsOn(acceptanceTest)
}

repositories {
  mavenCentral()
}

dependencies {
  add("compile", gradleApi())
  add("compile", localGroovy())
  add("compile", "com.android.tools.build:gradle:1.5.0")
  add("compile", "org.rauschig:jarchivelib:0.6.0")
  add("compile", "commons-io:commons-io:2.4")

  add("testCompile", "org.easytesting:fest-assert-core:2.0M10")

  add("acceptanceTestCompile", sourceSets["main"].output)
  add("acceptanceTestCompile", sourceSets["test"].output)
  add("acceptanceTestCompile", configurations["testCompile"])
  add("acceptanceTestRuntime", configurations["testRuntime"])
}

group = "com.jakewharton.sdkmanager"
version = "1.5.0-SNAPSHOT"

configure<Upload>("install") {
  repositories.withConvention(org.gradle.api.artifacts.maven.MavenRepositoryHandlerConvention::class) {
    mavenInstaller {
      pom.artifactId = "gradle-plugin"
    }
  }
}

configure<Upload>("uploadArchives") {
  repositories.withConvention(org.gradle.api.artifacts.maven.MavenRepositoryHandlerConvention::class) {
    mavenDeployer {
      pom.artifactId = "gradle-plugin"
    }
  }
}

configure<com.bmuschko.gradle.nexus.NexusPluginExtension> {
  modifyPom {
    project {
      name = "SDK Manager"
      description = "Gradle plugin which downloads and manages your Android SDK."
      url = "https://github.com/JakeWharton/sdk-manager-plugin"
      inceptionYear = "2014"

      scm {
        url = "https://github.com/JakeWharton/sdk-manager-plugin"
        connection = "scm:git:git://github.com/JakeWharton/sdk-manager-plugin.git"
        developerConnection = "scm:git:ssh://git@github.com/JakeWharton/sdk-manager-plugin.git"
      }

      licenses {
        license {
          name = "The Apache Software License, Version 2.0"
          url = "http://www.apache.org/licenses/LICENSE-2.0.txt"
          distribution = "repo"
        }
      }

      developers {
        developer {
          id = "jakewharton"
          name = "Jake Wharton"
          email = "jakewharton@gmail.com"
        }
      }
    }
  }
}

tasks.named<Wrapper>("wrapper") {
  gradleVersion = "2.10"
  distributionUrl = "https://services.gradle.org/distributions/gradle-$gradleVersion-all.zip"
}
