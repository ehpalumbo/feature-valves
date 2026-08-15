package org.calipsoide.featurevalves.infra.git;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Unit tests for {@link GitRepoManager} against a real local JGit origin:
 * default-branch tracking, shallow vs full-history clones, and branch override
 * precedence.
 */
class GitRepoManagerTest {

    @TempDir
    Path temporaryFolder;

    @Test
    void clonesFromRemoteDefaultBranch() throws Exception {
        final Path origin = createOrigin("main");
        final GitRepoManager manager = new GitRepoManager(local("clone").toString(), origin.toString(), "", 0);

        manager.initialize();
        manager.update();

        assertBranch(local("clone"), "main");
        assertThat(local("clone").resolve("features/app/always-on.yml")).exists();
    }

    @Test
    void pullsUpdatesFromDefaultBranch() throws Exception {
        final Path origin = createOrigin("main");
        final GitRepoManager manager = new GitRepoManager(local("clone").toString(), origin.toString(), "", 0);
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

        final GitRepoManager manager = new GitRepoManager(local("clone").toString(), origin.toString(), "legacy", 0);
        manager.initialize();
        manager.update();

        assertBranch(local("clone"), "legacy");
        assertThat(local("clone").resolve("features/app/legacy.yml")).exists();
        assertThat(local("clone").resolve("features/app/main-only.yml")).doesNotExist();
    }

    @Test
    void switchesToNewOverrideBranchOnExistingClone() throws Exception {
        final Path origin = createOrigin("main");
        try (Git git = Git.open(origin.toFile())) {
            git.branchCreate().setName("legacy").call();
            git.checkout().setName("legacy").call();
            commit(git, "features/app/legacy.yml", "active: true\n");
            git.checkout().setName("main").call();
            commit(git, "features/app/main-only.yml", "active: true\n");
        }

        final GitRepoManager first = new GitRepoManager(local("clone").toString(), origin.toString(), "legacy", 0);
        first.initialize();
        first.update();
        assertBranch(local("clone"), "legacy");

        final GitRepoManager restarted = new GitRepoManager(local("clone").toString(), origin.toString(), "main", 0);
        restarted.initialize();
        restarted.update();

        assertBranch(local("clone"), "main");
        assertThat(local("clone").resolve("features/app/main-only.yml")).exists();
        assertThat(local("clone").resolve("features/app/legacy.yml")).doesNotExist();
    }

    @Test
    void overrideSwitchOnExistingCloneStaysShallow() throws Exception {
        final Path origin = createOrigin("main");
        try (Git git = Git.open(origin.toFile())) {
            git.branchCreate().setName("legacy").call();
            git.checkout().setName("legacy").call();
            commit(git, "features/app/legacy.yml", "active: true\n");
            git.checkout().setName("main").call();
            commit(git, "features/app/main-only.yml", "active: true\n");
        }

        final GitRepoManager first = new GitRepoManager(local("clone").toString(), origin.toString(), "legacy", 1);
        first.initialize();
        first.update();
        assertBranch(local("clone"), "legacy");
        assertThat(historySize(local("clone"))).isEqualTo(1);

        final GitRepoManager restarted = new GitRepoManager(local("clone").toString(), origin.toString(), "main", 1);
        restarted.initialize();
        restarted.update();

        assertBranch(local("clone"), "main");
        assertThat(local("clone").resolve("features/app/main-only.yml")).exists();
        assertThat(historySize(local("clone"))).isEqualTo(1);
    }

    @Test
    void keepsCurrentBranchWhenOverrideUnchanged() throws Exception {
        final Path origin = createOrigin("main");
        try (Git git = Git.open(origin.toFile())) {
            git.branchCreate().setName("legacy").call();
            git.checkout().setName("legacy").call();
            commit(git, "features/app/legacy.yml", "active: true\n");
        }

        final GitRepoManager first = new GitRepoManager(local("clone").toString(), origin.toString(), "legacy", 0);
        first.initialize();
        first.update();
        assertBranch(local("clone"), "legacy");

        final GitRepoManager restarted = new GitRepoManager(local("clone").toString(), origin.toString(), "legacy", 0);
        restarted.initialize();
        restarted.update();

        assertBranch(local("clone"), "legacy");
        assertThat(local("clone").resolve("features/app/legacy.yml")).exists();
    }

    @Test
    void doesNotSwitchOnDefaultChangeWithoutOverride() throws Exception {
        final Path origin = createOrigin("main");
        final GitRepoManager first = new GitRepoManager(local("clone").toString(), origin.toString(), "", 0);
        first.initialize();
        first.update();
        assertBranch(local("clone"), "main");

        try (Git git = Git.open(origin.toFile())) {
            git.branchCreate().setName("new-default").call();
            git.checkout().setName("new-default").call();
            commit(git, "features/app/new-default.yml", "active: true\n");
        }

        final GitRepoManager restarted = new GitRepoManager(local("clone").toString(), origin.toString(), "", 0);
        restarted.initialize();
        restarted.update();

        assertBranch(local("clone"), "main");
        assertThat(local("clone").resolve("features/app/new-default.yml")).doesNotExist();
    }

    @Test
    void rejectsMissingRemoteUrl() {
        final GitRepoManager manager = new GitRepoManager(local("clone").toString(), "  ", "", 0);

        assertThatThrownBy(manager::initialize)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("features.git.remote.url");
    }

    @Test
    void clonesShallowByDefaultAndStaysShallowOnUpdate() throws Exception {
        final Path origin = createOrigin("main");
        commitFile(origin, "features/app/added.yml", "active: true\n");

        final GitRepoManager manager = new GitRepoManager(local("clone").toString(), origin.toString(), "", 1);
        manager.initialize();
        assertThat(historySize(local("clone"))).isEqualTo(1);

        commitFile(origin, "features/app/updated.yml", "active: true\n");
        manager.update();

        assertThat(local("clone").resolve("features/app/updated.yml")).exists();
        assertThat(historySize(local("clone"))).isEqualTo(1);
    }

    @Test
    void cloneDepthZeroKeepsFullHistory() throws Exception {
        final Path origin = createOrigin("main");
        commitFile(origin, "features/app/added.yml", "active: true\n");

        final GitRepoManager manager = new GitRepoManager(local("clone").toString(), origin.toString(), "", 0);
        manager.initialize();

        assertThat(historySize(local("clone"))).isGreaterThan(1);
    }

    @Test
    void branchOverrideWorksOnShallowClone() throws Exception {
        final Path origin = createOrigin("main");
        try (Git git = Git.open(origin.toFile())) {
            git.branchCreate().setName("legacy").call();
            git.checkout().setName("legacy").call();
            commit(git, "features/app/legacy.yml", "active: true\n");
            git.checkout().setName("main").call();
        }

        final GitRepoManager manager = new GitRepoManager(local("clone").toString(), origin.toString(), "legacy", 1);
        manager.initialize();
        manager.update();

        assertBranch(local("clone"), "legacy");
        assertThat(local("clone").resolve("features/app/legacy.yml")).exists();
        assertThat(historySize(local("clone"))).isEqualTo(1);
    }

    @Test
    void cloneAndUpdateFetchOnlyTrackedBranch() throws Exception {
        final Path origin = createOrigin("main");
        try (Git git = Git.open(origin.toFile())) {
            git.branchCreate().setName("feature").call();
            git.checkout().setName("feature").call();
            commit(git, "features/app/feature-only.yml", "active: true\n");
            git.checkout().setName("main").call();
        }

        final GitRepoManager manager = new GitRepoManager(local("clone").toString(), origin.toString(), "", 1);
        manager.initialize();

        assertBranch(local("clone"), "main");
        assertThat(ref(local("clone"), "refs/remotes/origin/feature")).isNull();

        manager.update();

        assertThat(local("clone").resolve("features/app/feature-only.yml")).doesNotExist();
        assertThat(ref(local("clone"), "refs/remotes/origin/feature")).isNull();

        try (Git git = Git.open(origin.toFile())) {
            git.checkout().setName("feature").call();
            commit(git, "features/app/feature-update.yml", "active: true\n");
            git.checkout().setName("main").call();
            commit(git, "features/app/main-update.yml", "active: true\n");
        }

        manager.update();

        assertThat(local("clone").resolve("features/app/main-update.yml")).exists();
        assertThat(local("clone").resolve("features/app/feature-update.yml")).doesNotExist();
        assertThat(ref(local("clone"), "refs/remotes/origin/feature")).isNull();
    }

    @Test
    void cloneNarrowsToDefaultBranchOnly() throws Exception {
        final Path origin = createOrigin("main");
        try (Git git = Git.open(origin.toFile())) {
            git.branchCreate().setName("feature").call();
            git.checkout().setName("feature").call();
            commit(git, "features/app/feature-only.yml", "active: true\n");
            git.checkout().setName("main").call();
            git.branchCreate().setName("another").call();
            git.checkout().setName("another").call();
            commit(git, "features/app/another-only.yml", "active: true\n");
            git.checkout().setName("main").call();
        }

        final GitRepoManager manager = new GitRepoManager(local("clone").toString(), origin.toString(), "", 0);
        manager.initialize();

        assertThat(ref(local("clone"), "refs/remotes/origin/main")).isNotNull();
        assertThat(ref(local("clone"), "refs/remotes/origin/feature")).isNull();
        assertThat(ref(local("clone"), "refs/remotes/origin/another")).isNull();
        assertThat(ref(local("clone"), "refs/heads/feature")).isNull();
        assertThat(ref(local("clone"), "refs/heads/another")).isNull();
        assertThat(local("clone").resolve("features/app/feature-only.yml")).doesNotExist();
        assertThat(local("clone").resolve("features/app/another-only.yml")).doesNotExist();
    }

    @Test
    void cloneWithOverrideNarrowsToOverrideBranchOnly() throws Exception {
        final Path origin = createOrigin("main");
        try (Git git = Git.open(origin.toFile())) {
            git.branchCreate().setName("feature").call();
            git.checkout().setName("feature").call();
            commit(git, "features/app/feature-only.yml", "active: true\n");
            git.checkout().setName("main").call();
            commit(git, "features/app/main-only.yml", "active: true\n");
        }

        final GitRepoManager manager = new GitRepoManager(local("clone").toString(), origin.toString(), "feature", 0);
        manager.initialize();

        assertBranch(local("clone"), "feature");
        assertThat(ref(local("clone"), "refs/remotes/origin/feature")).isNotNull();
        assertThat(ref(local("clone"), "refs/remotes/origin/main")).isNull();
        assertThat(ref(local("clone"), "refs/heads/main")).isNull();
        assertThat(local("clone").resolve("features/app/feature-only.yml")).exists();
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

    private Ref ref(Path repo, String name) throws Exception {
        try (Git git = Git.open(repo.toFile())) {
            return git.getRepository().getRefDatabase().findRef(name);
        }
    }

    private int historySize(Path repo) throws Exception {
        try (Git git = Git.open(repo.toFile())) {
            final var commits = git.log().call().iterator();
            int count = 0;
            while (commits.hasNext()) {
                commits.next();
                count++;
            }
            return count;
        }
    }

}
