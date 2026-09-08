pluginManagement {
	repositories {
		google {
			content {
				includeGroupByRegex("com\\.android.*")
				includeGroupByRegex("com\\.google.*")
				includeGroupByRegex("androidx.*")
			}
		}
		mavenCentral()
		gradlePluginPortal()
	}
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "MoneyPal"

plugins {
	// Auto-provision JDK toolchains (e.g. the JDK 17 required by :sync-contract)
	// when no matching installation is found locally.
	id("org.gradle.toolchains.foojay-resolver-convention") version "0.9.0"
}

include(":app")
if (providers.gradleProperty("minus.includeWearModule").orNull != "false") {
	include(":wear")
}
include(":sync-contract")
