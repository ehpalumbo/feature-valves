package org.calipsoide.featurevalves.infra.git;

import java.io.File;
import java.util.Map;

import org.eclipse.jgit.api.Git;
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
            @Value("${features.git.remote.url}") String url,
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
     * @throws RuntimeException if the underlying git operation fails
     */
    public void initialize() {
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
            }
        } catch (Exception e) {
            log.error("Failed to initialize git repository at {}", localPath, e);
            throw new RuntimeException("Failed to initialize git repository", e);
        }
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
     * Brings the local clone up to date by pulling the branch tracked by the
     * git configuration established during {@link #initialize()}.
     *
     * @throws RuntimeException if the underlying git operation fails
     */
    public void update() {
        try {
            if (git == null) {
                initialize();
            }
            log.debug("Pulling latest changes from remote into {}", localPath);
            git.pull()
                    .setRemote("origin")
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
