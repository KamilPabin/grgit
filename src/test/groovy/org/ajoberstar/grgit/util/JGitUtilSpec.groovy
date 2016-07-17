/*
 * Copyright 2012-2015 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.ajoberstar.grgit.util

import org.ajoberstar.grgit.Commit
import org.ajoberstar.grgit.Grgit
import org.ajoberstar.grgit.Person
import org.ajoberstar.grgit.Repository
import org.ajoberstar.grgit.Tag
import org.ajoberstar.grgit.exception.GrgitException

import org.eclipse.jgit.api.Git
import org.eclipse.jgit.errors.RevisionSyntaxException
import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.lib.Ref
import org.eclipse.jgit.merge.MergeStrategy
import org.eclipse.jgit.revwalk.RevTag
import org.eclipse.jgit.revwalk.RevWalk

import org.junit.Rule
import org.junit.rules.TemporaryFolder

import spock.lang.Specification

class JGitUtilSpec extends Specification {
    @Rule TemporaryFolder tempDir = new TemporaryFolder()

    Repository repo
    List commits = []
    Ref annotatedTag
    Ref unannotatedTag
    Ref taggedAnnotatedTag

    def 'resolveObject works for branch name'() {
        expect:
        JGitUtil.resolveObject(repo, 'master') == commits[3]
    }

    def 'resolveObject works for full commit hash'() {
        expect:
        JGitUtil.resolveObject(repo, ObjectId.toString(commits[0])) == commits[0]
    }

    def 'resolveObject works for abbreviated commit hash'() {
        expect:
        JGitUtil.resolveObject(repo, ObjectId.toString(commits[0])[0..5]) == commits[0]
    }

    def 'resolveObject works for full ref name'() {
        expect:
        JGitUtil.resolveObject(repo, 'refs/heads/master') == commits[3]
    }

    def 'resolveObject works for HEAD'() {
        expect:
        JGitUtil.resolveObject(repo, 'HEAD') == commits[3]
    }

    def 'resolveObject works for parent commit'() {
        expect:
        JGitUtil.resolveObject(repo, 'master^') == commits[1]
    }

    def 'resolveObject works for current commit'() {
        expect:
        JGitUtil.resolveObject(repo, 'master^0') == commits[3]
    }

    def 'resolveObject works for n-th parent'() {
        expect:
        JGitUtil.resolveObject(repo, 'master^2') == commits[2]
    }

    def 'resolveObject works for the n-th ancestor'() {
        expect:
        JGitUtil.resolveObject(repo, 'master~2') == commits[0]
    }

    def 'resolveObject fails if revision cannot be found'() {
        when:
        JGitUtil.resolveObject(repo, 'unreal')
        then:
        def e = thrown(GrgitException)
        e.cause == null
    }

    def 'resolveObject fails if revision syntax is wrong'() {
        when:
        JGitUtil.resolveObject(repo, 'lkj!)#(*')
        then:
        def e = thrown(GrgitException)
        e.cause instanceof RevisionSyntaxException
    }

    def 'convertCommit works for valid commit'() {
        given:
        Person person = new Person(repo.jgit.repo.config.getString('user', null, 'name'), repo.jgit.repo.config.getString('user', null, 'email'))
        expect:
        JGitUtil.convertCommit(commits[1]) == new Commit(
            ObjectId.toString(commits[1]),
            [ObjectId.toString(commits[0])],
            person,
            person,
            commits[1].commitTime,
            'second commit',
            'second commit'
        )
    }

    def 'resolveTag works for annotated tag ref'() {
        given:
        Person person = new Person(repo.jgit.repo.config.getString('user', null, 'name'), repo.jgit.repo.config.getString('user', null, 'email'))
        expect:
        JGitUtil.resolveTag(repo, annotatedTag) == new Tag(
            JGitUtil.convertCommit(commits[0]),
            person,
            'refs/tags/v1.0.0',
            'first tag\ntesting',
            'first tag testing'
        )
    }

    def 'resolveTag works for unannotated tag ref'() {
        given:
        Person person = new Person(repo.jgit.repo.config.getString('user', null, 'name'), repo.jgit.repo.config.getString('user', null, 'email'))
        expect:
        JGitUtil.resolveTag(repo, unannotatedTag) == new Tag(
            JGitUtil.convertCommit(commits[0]),
            null,
            'refs/tags/v2.0.0',
            null,
            null
        )
    }

    def 'resolveTag works for a tag pointing to a tag'() {
        given:
        Person person = new Person(repo.jgit.repo.config.getString('user', null, 'name'), repo.jgit.repo.config.getString('user', null, 'email'))
        expect:
        JGitUtil.resolveTag(repo, taggedAnnotatedTag) == new Tag(
            JGitUtil.convertCommit(commits[0]),
            person,
            'refs/tags/v1.1.0',
            'testing',
            'testing'
        )
    }

    def setup() {
        File repoDir = tempDir.newFolder('repo')
        Git git = Git.init().setDirectory(repoDir).call()
        git.repo.config.with {
            setString('user', null, 'name', 'Bruce Wayne')
            setString('user', null, 'email', 'bruce.wayne@wayneindustries.com')
            save()
        }
        File testFile = new File(repoDir, '1.txt')
        testFile << '1\n'
        git.add().addFilepattern(testFile.name).call()
        commits << git.commit().setMessage('first commit\ntesting').call()
        annotatedTag = git.tag().setName('v1.0.0').setMessage('first tag\ntesting').call()
        unannotatedTag = git.tag().setName('v2.0.0').setAnnotated(false).call()
        testFile << '2\n'
        git.add().addFilepattern(testFile.name).call()
        commits << git.commit().setMessage('second commit').call()
        git.checkout().setName(ObjectId.toString(commits[0])).call()
        testFile << '3\n'
        git.add().addFilepattern(testFile.name).call()
        commits << git.commit().setMessage('third commit').call()
        git.checkout().setName('master').call()
        commits << git.merge().include(commits[2]).setStrategy(MergeStrategy.OURS).call().newHead
        RevTag tagV1 = new RevWalk(git.repository).parseTag(annotatedTag.objectId)
        taggedAnnotatedTag = git.tag().setName('v1.1.0').setObjectId(tagV1).setMessage('testing').call()
        repo = Grgit.open(dir: repoDir).repository
    }
}
