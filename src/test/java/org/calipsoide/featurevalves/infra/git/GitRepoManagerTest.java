package org.calipsoide.featurevalves.infra.git;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.PersonIdent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Unit tests for {@link GitRepoManager} against a real local JGit origin:
 * default-branch tracking, pulls, and branch override precedence.
 */
class GitRepoManagerTest {

    @TempDir
    Path temporaryFolder;

    @Test
    void clonesFromRemoteDefaultBranch() throws Exception {
        final Path origin = createOrigin("main");
        final GitRepoManager manager = new GitRepoManager(local("clone").toString(), origin.toString(), "");

        manager.initialize();
        manager.update();

        assertBranch(local("clone"), "main");
        assertThat(local("clone").resolve("features/app/always-on.yml")).exists();
    }

    @Test
    void pullsUpdatesFromDefaultBranch() throws Exception {
        final Path origin = createOrigin("main");
        final GitRepoManager manager = new GitRepoManager(local("clone").toString(), origin.toString(), "");
        manager.initialize();
        manager.update();

        commitFile(origin, "features/app/added.yml", "active: true\n");

        manager.update();

        assertThat(local("clone").resolve("features/app/added.yml")).exists();
    }

    @Test
    void branchOverrideTakesPrecedenceOverDefaultBranch() throws Exception {
        final Path origin = createOrigin("main");
        try (Git git = Git.open(origin.toFile())) {
            git.branchCreate().setName("legacy").call();
            git.checkout().setName("legacy").call();
            commit(git, "features/app/legacy.yml", "active: true\n");
            git.checkout().setName("main").call();
            commit(git, "features/app/main-only.yml", "active: true\n");
        }

        final GitRepoManager manager = new GitRepoManager(local("clone").toString(), origin.toString(), "legacy");
        manager.initialize();
        manager.update();

        assertBranch(local("clone"), "legacy");
        assertThat(local("clone").resolve("features/app/legacy.yml")).exists();
        assertThat(local("clone").resolve("features/app/main-only.yml")).doesNotExist();
    }

    private Path createOrigin(String initialBranch) throws Exception {
        final Path origin = Files.createDirectory(temporaryFolder.resolve("origin"));
        try (Git git = Git.init().setInitialBranch(initialBranch).setDirectory(origin.toFile()).call()) {
            commit(git, "features/app/always-on.yml", "active: true\n");
        }
        return origin;
    }

    private void commitFile(Path repo, String relative, String content) throws Exception {
        try (Git git = Git.open(repo.toFile())) {
            commit(git, relative, content);
        }
    }

    private void commit(Git git, String relative, String content) throws Exception {
        final Path file = git.getRepository().getWorkTree().toPath().resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
        git.add().addFilepattern(relative).call();
        final var identity = new PersonIdent("feature-valves", "feature-valves@example.org");
        git.commit().setAuthor(identity).setCommitter(identity).setMessage("commit " + relative).call();
    }

    private Path local(String name) {
        return temporaryFolder.resolve(name);
    }

    private void assertBranch(Path repo, String expected) throws Exception {
        try (Git git = Git.open(repo.toFile())) {
            assertThat(git.getRepository().getBranch()).isEqualTo(expected);
        }
    }

}
