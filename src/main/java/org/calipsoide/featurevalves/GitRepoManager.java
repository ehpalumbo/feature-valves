package org.calipsoide.featurevalves;

import java.io.File;

import org.eclipse.jgit.api.Git;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Manages the local Git repository that stores feature definition files.
 * <p>
 * Clones the configured remote on first use and pulls on subsequent updates,
 * always targeting the configured branch.
 *
 * @see GitFeatureFileRepository
 */
@Service
public class GitRepoManager {

    private final File localPath;

    private final String url;

    private final String branch;

    private Git git;

    /**
     * Creates the manager for the configured git repository.
     *
     * @param localPath local path where the clone lives
     * @param url       remote repository URL
     * @param branch    branch to track
     */
    public GitRepoManager(
            @Value("${features.git.local.path}") String localPath,
            @Value("${features.git.remote.url}") String url,
            @Value("${features.git.remote.branch}") String branch) {
        this.localPath = new File(localPath);
        this.url = url;
        this.branch = branch;
    }

    /**
     * Brings the local clone up to date: pulls when the path already contains
     * a repository, clones it otherwise.
     *
     * @throws RuntimeException if the underlying git operation fails
     */
    public void update() {
        try {
            if (localPath.isDirectory()) {
                if (git == null) {
                    git = Git.open(localPath);
                }
                git.pull()
                        .setRemote("origin")
                        .setRemoteBranchName(branch)
                        .call();
            } else {
                git = Git
                        .cloneRepository()
                        .setURI(url)
                        .setRemote("origin")
                        .setBranch(branch)
                        .setDirectory(localPath)
                        .call();
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}
