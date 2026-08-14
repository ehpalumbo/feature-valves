package org.calipsoide.featurevalves.infra.git;

import java.io.File;
import java.util.Map;

import org.eclipse.jgit.api.CreateBranchCommand.SetupUpstreamMode;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ResetCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

/**
 * Manages the local Git repository that stores feature definition files.
 * <p>
 * Initialization (see {@link #initialize()}) clones the remote and configures
 * the branch to track; subsequent {@link #update()} calls only pull that
 * branch. By default the remote's default branch is tracked; this can be
 * overridden with the {@code features.git.remote.branch} setting.
 * <p>
 * The {@code features.git.remote.url} setting is mandatory in every
 * environment except local development; when missing, startup aborts with a
 * clear error message (see {@link #initialize()}).
 *
 * @see GitFeatureFileRepository
 */
@Slf4j
@Service
public class GitRepoManager implements InitializingBean, DisposableBean {

    private final File localPath;

    private final String url;

    private final String branch;

    private Git git;

    /**
     * Creates the manager for the configured git repository.
     *
     * @param localPath local path where the clone lives
     * @param url       remote repository URL
     * @param branch    branch to track, or empty to use the remote's default branch
     */
    public GitRepoManager(
            @Value("${features.git.local.path}") String localPath,
            @Value("${features.git.remote.url:}") String url,
            @Value("${features.git.remote.branch:}") String branch) {
        this.localPath = new File(localPath);
        this.url = url;
        this.branch = branch;
    }

    /**
     * Initializes the local clone and configures the branch to track.
     * <p>
     * Opens an existing clone when one is already present at the local path,
     * clones the remote otherwise. Idempotent: a no-op once the clone is set
     * up.
     *
     * @throws IllegalStateException if {@code features.git.remote.url} is not
     *                               configured
     * @throws RuntimeException      if the underlying git operation fails
     */
    public void initialize() {
        if (url.isBlank()) {
            throw new IllegalStateException(
                    "features.git.remote.url is not configured. Provide the Git repository URI "
                            + "(e.g. the FEATURES_GIT_REMOTE_URL environment variable or the "
                            + "features.git.remote.url property); for local development run with the "
                            + "dev profile (SPRING_PROFILES_ACTIVE=dev).");
        }
        try {
            if (git == null) {
                if (localPath.isDirectory()) {
                    log.debug("Opening existing git repository at {}", localPath);
                    git = Git.open(localPath);
                } else {
                    final String branch = resolveEffectiveBranch();
                    log.info(
                            "Cloning git repository at {} into {}; tracking branch {}",
                            url, localPath, branch);
                    git = Git
                            .cloneRepository()
                            .setURI(url)
                            .setRemote("origin")
                            .setBranch(branch)
                            .setDirectory(localPath)
                            .call();
                }
                ensureTrackedBranch();
            }
        } catch (Exception e) {
            log.error("Failed to initialize git repository at {}", localPath, e);
            throw new RuntimeException("Failed to initialize git repository", e);
        }
    }

    /**
     * Reconciles the local clone with a configured branch override.
     * <p>
     * When no override is configured this is a no-op, leaving the branch that
     * was tracked at clone time in place. When an override is set and differs
     * from the currently checked-out branch, the remote is fetched, the local
     * branch is reset to track the override, and the target branch's files are
     * checked out so subsequent {@link #update()} pulls stay on that branch.
     * This supports restarting the application with a different
     * {@code features.git.remote.branch}.
     *
     * @throws Exception if the underlying git operation fails
     */
    private void ensureTrackedBranch() throws Exception {
        if (branch.isBlank()) {
            return;
        }
        final String current = git.getRepository().getBranch();
        if (branch.equals(current)) {
            return;
        }
        log.info(
                "Branch override {} differs from checked-out branch {}; switching local checkout",
                branch, current);
        git.fetch()
                .setRemote("origin")
                .call();
        git.branchCreate()
                .setName(branch)
                .setStartPoint(Constants.R_REMOTES + "origin/" + branch)
                .setUpstreamMode(SetupUpstreamMode.SET_UPSTREAM)
                .setForce(true)
                .call();
        git.checkout()
                .setName(branch)
                .setForced(true)
                .call();
    }

    /**
     * Runs the initialization phase on application startup, before any load is
     * triggered, so that {@link #update()} can rely on the tracked branch
     * configuration set during the clone.
     *
     * @throws Exception if the initialization phase fails
     */
    @Override
    public void afterPropertiesSet() throws Exception {
        initialize();
    }

    /**
     * Closes the underlying JGit repository on shutdown, releasing the file
     * handles and locks held by the clone.
     */
    @Override
    public void destroy() {
        if (git != null) {
            log.debug("Closing git repository at {}", localPath);
            git.close();
        }
    }

    /**
     * Brings the local clone up to date by fetching the branch tracked by the
     * git configuration established during {@link #initialize()} and hard
     * resetting the working tree to its remote tip.
     * <p>
     * A fetch plus hard reset is used rather than a merge-based pull because
     * the local clone is a read-only mirror of the latest committed files: the
     * working tree is always reset to {@code origin/&lt;tracked-branch&gt;}, so no
     * shared history or merge base is required. This also keeps shallow clones
     * working, where a merge-pull would fail for lack of a common ancestor.
     *
     * @throws RuntimeException if the underlying git operation fails
     */
    public void update() {
        try {
            if (git == null) {
                initialize();
            }
            final String tracked = git.getRepository().getBranch();
            log.debug("Fetching latest changes from remote into {}; tracking branch {}", localPath, tracked);
            git.fetch()
                    .setRemote("origin")
                    .call();
            git.reset()
                    .setMode(ResetCommand.ResetType.HARD)
                    .setRef(Constants.R_REMOTES + "origin/" + tracked)
                    .call();
        } catch (Exception e) {
            log.error("Failed to update git repository at {}", localPath, e);
            throw new RuntimeException("Failed to update git repository", e);
        }
    }

    /**
     * Resolves the branch to track: the configured override when present, the
     * remote's default branch otherwise.
     *
     * @return the branch name to track
     * @throws GitAPIException if resolving the default branch fails
     */
    private String resolveEffectiveBranch() throws GitAPIException {
        if (!branch.isBlank()) {
            return branch;
        }
        log.debug("No branch override configured; resolving default branch for {}", url);
        final Map<String, Ref> refs = Git
                .lsRemoteRepository()
                .setRemote(url)
                .callAsMap();
        final Ref head = refs.get(Constants.HEAD);
        if (head == null || !head.isSymbolic()) {
            final String fallback = Constants.MASTER;
            log.warn(
                    "Could not resolve default branch for {}: HEAD is not a symbolic ref; falling back to {}",
                    url, fallback);
            return fallback;
        }
        return Repository.shortenRefName(head.getTarget().getName());
    }

}
