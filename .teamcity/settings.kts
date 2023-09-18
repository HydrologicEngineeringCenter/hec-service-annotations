import jetbrains.buildServer.configs.kotlin.v2019_2.*
import jetbrains.buildServer.configs.kotlin.v2019_2.buildFeatures.commitStatusPublisher
import jetbrains.buildServer.configs.kotlin.v2019_2.buildSteps.gradle
import jetbrains.buildServer.configs.kotlin.v2019_2.buildSteps.script
import jetbrains.buildServer.configs.kotlin.v2019_2.failureConditions.BuildFailureOnMetric
import jetbrains.buildServer.configs.kotlin.v2019_2.failureConditions.failOnMetricChange
import jetbrains.buildServer.configs.kotlin.v2019_2.triggers.vcs
import jetbrains.buildServer.configs.kotlin.v2019_2.triggers.finishBuildTrigger
import java.io.BufferedReader;
import java.io.File;
import jetbrains.buildServer.configs.kotlin.v2019_2.vcs.GitVcsRoot
 
/*
The settings script is an entry point for defining a TeamCity
project hierarchy. The script should contain a single call to the
project() function with a Project instance or an init function as
an argument.
 
VcsRoots, BuildTypes, Templates, and subprojects can be
registered inside the project using the vcsRoot(), buildType(),
template(), and subProject() methods respectively.
 
To debug settings scripts in command-line, run the
 
    mvnDebug org.jetbrains.teamcity:teamcity-configs-maven-plugin:generate
 
command and attach your debugger to the port 8000.
 
To debug in IntelliJ Idea, open the 'Maven Projects' tool window (View
-> Tool Windows -> Maven Projects), find the generate task node
(Plugins -> teamcity-configs -> teamcity-configs:generate), the
'Debug' option is available in the context menu for the task.
*/
 
version = "2021.1"

object GlobalContextProviderRepo : GitVcsRoot( {
    name = "GlobalContextProviderRepo"
    url = "https://bitbucket.hecdev.net/scm/sc/service-annotations.git"
    branch = "refs/heads/main"
    branchSpec = """+:*
-:refs/pull-requests/*
    """.trim() // prevent PR/from builds until reporting to bitbucket works correctly.
    useTagsAsBranches = true
    authMethod = password {
        userName = "builduser"
        password = "credentialsJSON:tcStashPassword"
    }
})

project {
 
    params {
        param("teamcity.ui.settings.readOnly", "true")
    }
     
    sequential {
        buildType(Build)           
        buildType(Deploy)
        
        
    }.buildTypes().forEach { buildType(it) }
    vcsRoot(GlobalContextProviderRepo)
}
 
 
object Build : BuildType({
    name = "Build Libraries"
    // artifacts rules place your generated output in a place it can be downloaded or sent to another Build configuration
    artifactRules = """
        **/target/*.jar => 
    """.trimIndent()
 
 
    params {
                 
    }
 
    vcs {
        root(DslContext.settingsRoot)
    }
 
    steps {
        gradle {
            // Just to be safe, clean first.
            tasks = "clean"
            gradleParams = "-PnexusUser=%env.NEXUS_USER% -PnexusPassword=%env.NEXUS_PASSWORD%"
            jdkHome = "%env.JDK_17_0_x64%"
        }
        gradle {
            tasks = "build"
            name = "build"
            gradleParams = "-PnexusUser=%env.NEXUS_USER% -PnexusPassword=%env.NEXUS_PASSWORD%"
            jdkHome = "%env.JDK_17_0_x64%"
        }
    }
 
    triggers {
        vcs {
        }
    }
 
    failureConditions {
        executionTimeoutMin = 5
    }
 
    features {
        commitStatusPublisher {
            publisher = bitbucketServer {
                url = "https://bitbucket.hecdev.net"
                userName = "builduser"
                password = "credentialsJSON:tcStashPassword"
            }
        }
        feature {
            type = "halfbaked-sonarqube-report-plugin"
        }
    }

 
})
 
 
object Deploy : BuildType({
    name = "Deploy to Nexus"
    
    artifactRules = """
 
    """.trimIndent()
 
    vcs {
        root(DslContext.settingsRoot)
    }
 
    steps {
        gradle {
            // Just to be safe, clean first.
            tasks = "clean"
            gradleParams = "-PnexusUser=%env.NEXUS_USER% -PnexusPassword=%env.NEXUS_PASSWORD%"
            jdkHome = "%env.JDK_17_0_x64%"
        }
        gradle {
            tasks = "build"
            name = "build for publish"
            gradleParams = "-PnexusUser=%env.NEXUS_USER% -PnexusPassword=%env.NEXUS_PASSWORD%"
            jdkHome = "%env.JDK_17_0_x64%"
        }
        gradle {
            tasks = "publish"
            name = "Deploy artifacts to Nexus"
            gradleParams = "-PnexusUser=%env.NEXUS_USER% -PnexusPassword=%env.NEXUS_PASSWORD%"
            jdkHome = "%env.JDK_17_0_x64%"
        }
    }
 
    // for this example deployed releases will always from from master
    // you could choose any other valid branch filter.
    triggers {
        finishBuildTrigger {
            buildType = "${Build.id}"
            successfulOnly = true
            branchFilter = """
                +:<default>
            """.trimIndent()
 
        }
    }
        
    requirements {
    }
     
 
})

