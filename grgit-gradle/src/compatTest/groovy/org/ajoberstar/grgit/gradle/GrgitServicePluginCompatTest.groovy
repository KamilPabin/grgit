package org.ajoberstar.grgit.gradle

import spock.lang.Specification

import org.ajoberstar.grgit.Grgit
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.TempDir

class GrgitServicePluginCompatTest extends Specification {
    @TempDir File tempDir
    File projectDir
    File buildFile

    def setup() {
        projectDir = new File(tempDir, 'project')
        buildFile = projectFile('build.gradle')
        buildFile << '''\
import org.ajoberstar.grgit.gradle.GrgitService

plugins {
  id 'org.ajoberstar.grgit.service'
}

tasks.register("doStuff", DoStuffTask.class) {
    service = grgitService.service
}

class DoStuffTask extends DefaultTask {
    @Input
    final Property<GrgitService> service

    @Inject
    DoStuffTask(ObjectFactory objectFactory) {
        this.service = objectFactory.property(GrgitService.class);
    }

    @TaskAction
    void execute() {
        println service.get().grgit.describe()
    }
}
'''
    }

    def 'with no repo, accessing service fails'() {
        given:
        // nothing
        when:
        def result = buildAndFail('doStuff', '--configuration-cache')
        then:
        result.task(':doStuff').outcome == TaskOutcome.FAILED
    }

    def 'with repo, plugin opens the repo as grgit'() {
        given:
        Grgit git = Grgit.init(dir: projectDir)
        projectFile('1.txt') << '1'
        git.add(patterns: ['1.txt'])
        git.commit(message: 'yay')
        git.tag.add(name: '1.0.0')
        when:
        def result = build('doStuff', '--quiet', '--configuration-cache')
        then:
        result.task(':doStuff').outcome == TaskOutcome.SUCCESS
        result.output.normalize() == '1.0.0\n'
    }

    def 'with repo, plugin closes the repo after build is finished'() {
        given:
        Grgit git = Grgit.init(dir: projectDir)
        projectFile('1.txt') << '1'
        git.add(patterns: ['1.txt'])
        git.commit(message: 'yay')
        git.tag.add(name: '1.0.0')
        when:
        def result = build('doStuff', '--info', '--configuration-cache')
        then:
        result.task(':doStuff').outcome == TaskOutcome.SUCCESS
        result.output.contains('Closing Git repo')
    }

    private BuildResult build(String... args) {
        return GradleRunner.create()
                .withGradleVersion(System.properties['compat.gradle.version'])
                .withPluginClasspath()
                .withProjectDir(projectDir)
                .forwardOutput()
                .withArguments((args + '--stacktrace') as String[])
                .build()
    }

    private BuildResult buildAndFail(String... args) {
        return GradleRunner.create()
                .withGradleVersion(System.properties['compat.gradle.version'])
                .withPluginClasspath()
                .withProjectDir(projectDir)
                .forwardOutput()
                .withArguments((args + '--stacktrace') as String[])
                .buildAndFail()
    }

    private File projectFile(String path) {
        File file = new File(projectDir, path)
        file.parentFile.mkdirs()
        return file
    }
}
